package com.philkolar.volumekeeper.core

/**
 * The detection and restore rule (docs/detection-rule.md), as a deterministic state machine.
 *
 * Single-threaded: the runtime serialises every call onto one handler thread. The adapter reads a
 * [Probe] (current device + STREAM_MUSIC index) right before each call and executes the returned
 * [Action]s. Time is elapsedRealtime ms, always taken from the probe or the event, never a clock here.
 *
 * Invariants (InvariantPropertyTest):
 *  I1 a restore target is always a level previously observed for that device — the drop
 *     broadcast's `prev`, or the tracker's last observation. No constant, no max, ever.
 *  I2 RS1 < target <= max.
 *  I3 no restore without a matched CSD notice (the 5× echo belongs to the notice before it).
 *  I4 no restore if the volume changed after the drop, or the output changed around it.
 */
class DecisionEngine(private val settings: () -> EngineSettings) {

    private data class Pending(val id: Long, val notice: CsdNotice, val deadline: Long)
    private data class ExpectSelf(val deviceKey: String, val target: Int, val until: Long)
    private data class Echo(val deviceKey: String, val target: Int, val until: Long, val notice: CsdNotice)
    private data class InFlight(val decision: Decision, val target: Int, val from: Int)

    val tracker = VolumeTracker()

    /** Recent non-self volume changes, oldest first. */
    private val history = ArrayDeque<VolumeChange>()
    /** Times of recent output-device changes. */
    private val deviceChanges = ArrayDeque<Long>()
    private val seenNotices = LinkedHashSet<String>()
    private val restoreTimes = HashMap<String, ArrayDeque<Long>>()
    private val pausedUntil = HashMap<String, Long>()
    private val inFlight = HashMap<Long, InFlight>()

    private var pending: Pending? = null
    private var expectSelf: ExpectSelf? = null
    private var echo: Echo? = null
    private var nextId = 1L

    /** A STREAM_MUSIC change from the VOLUME_CHANGED broadcast or the settings observer. */
    fun onVolumeChanged(change: VolumeChange, probe: Probe): List<Action> {
        if (change.prev == change.new) return emptyList()
        val device = change.device
        val es = expectSelf
        if (es != null && device != null && device.key == es.deviceKey && change.new == es.target && change.t <= es.until) {
            expectSelf = null
            tracker.observe(device, change.new, change.t, Source.SELF)
            return emptyList()
        }
        if (device != null) tracker.observe(device, change.new, change.t, change.source)
        history.addLast(change)
        prune(change.t)

        val actions = mutableListOf<Action>()
        val p = pending
        if (p != null) {
            actions += tryDecide(p, probe, final = false)
        } else {
            actions += checkEcho(change, probe)
        }
        return actions
    }

    /** A plain read of the current device's level (engine start, after an output change settles). */
    fun onVolumeRead(device: Device, volume: Int, t: Long) {
        tracker.observe(device, volume, t, Source.READ)
    }

    /** The media output changed (connect, disconnect, switch). */
    fun onDeviceChanged(t: Long) {
        deviceChanges.addLast(t)
        echo = null
        prune(t)
    }

    fun onCsdNotice(notice: CsdNotice, probe: Probe): List<Action> {
        val dedupe = "${notice.key}@${notice.postWall}"
        if (!seenNotices.add(dedupe)) return emptyList()
        while (seenNotices.size > 64) seenNotices.remove(seenNotices.first())

        val id = nextId++
        val s = settings()
        if (!s.enabled) return listOf(skip(id, notice, probe.device, "disabled"))
        if (notice.recvT - notice.postT > Windows.STALE) return listOf(skip(id, notice, probe.device, "stale_notice"))

        val actions = mutableListOf<Action>()
        pending?.let { old ->
            actions += skip(old.id, old.notice, probe.device, "superseded")
        }
        echo = null
        val p = Pending(id, notice, notice.postT + Windows.W_AFTER)
        pending = p
        val final = probe.now >= p.deadline
        val decided = tryDecide(p, probe, final)
        actions += decided
        if (decided.isEmpty()) actions += Action.Schedule(p.deadline, p.id)
        return actions
    }

    fun onTimer(token: Long, probe: Probe): List<Action> {
        val p = pending ?: return emptyList()
        if (p.id != token) return emptyList()
        return tryDecide(p, probe, final = true)
    }

    /** Read-back [Windows.VERIFY] after a restore. */
    fun onVerify(restoreId: Long, probe: Probe): List<Action> {
        val f = inFlight.remove(restoreId) ?: return emptyList()
        val sameDevice = probe.device?.key == f.decision.device?.key
        val d = when {
            sameDevice && probe.volume == f.target ->
                f.decision.copy(outcome = Outcome.RESTORED, tVerified = probe.now)
            sameDevice && probe.volume == f.from ->
                f.decision.copy(outcome = Outcome.FAILED, reason = "not_applied", tVerified = probe.now)
            sameDevice ->
                f.decision.copy(outcome = Outcome.RESTORED, reason = "changed_again_before_verify", tVerified = probe.now)
            else ->
                f.decision.copy(outcome = Outcome.FAILED, reason = "device_changed_before_verify", tVerified = probe.now)
        }
        if (d.outcome == Outcome.FAILED) {
            echo = null
            expectSelf = null
        }
        return listOf(Action.Record(d))
    }

    // ---------------------------------------------------------------------------------------

    private fun tryDecide(p: Pending, probe: Probe, final: Boolean): List<Action> {
        val n = p.notice
        val last = history.lastOrNull()
        if (last != null && qualifiesAsDrop(last, n)) return decideWithDrop(p, last, probe)
        if (!final) return emptyList()
        return decideFallback(p, probe)
    }

    private fun qualifiesAsDrop(c: VolumeChange, n: CsdNotice): Boolean {
        val dev = c.device ?: return false
        return c.t >= n.postT - Windows.W_BEFORE &&
            c.t <= n.postT + Windows.W_AFTER &&
            c.new == Rs1.of(dev.type) &&
            c.prev > c.new
    }

    private fun decideWithDrop(p: Pending, drop: VolumeChange, probe: Probe): List<Action> {
        pending = null
        val dropDevice = drop.device!!
        val dev = probe.device
        val reason = when {
            dev == null -> "no_headphone_output"
            dev.key != dropDevice.key -> "device_changed"
            deviceChanges.any { it >= drop.t - Windows.SETTLE && it <= probe.now } -> "device_changed"
            probe.volume != drop.new -> "volume_changed_after_drop"
            dev.key in settings().excluded -> "excluded"
            drop.prev > probe.max -> "invalid_target"
            breakerTrips(dev.key, probe.now) -> "circuit_breaker"
            else -> null
        }
        if (reason != null) {
            return listOf(skip(p.id, p.notice, dev ?: dropDevice, reason, before = drop.prev, lowered = drop.new, tDrop = drop.t))
        }
        val signal = if (drop.source == Source.SETTINGS) "notif+settings" else "notif+broadcast"
        return restore(p.id, p.notice, dev!!, from = drop.new, target = drop.prev, signal = signal, tDrop = drop.t, probe = probe, countsForBreaker = true)
    }

    private fun decideFallback(p: Pending, probe: Probe): List<Action> {
        pending = null
        val n = p.notice
        val dev = probe.device ?: return listOf(skip(p.id, n, null, "no_headphone_output"))
        if (dev.key in settings().excluded) return listOf(skip(p.id, n, dev, "excluded"))
        val rs1 = Rs1.of(dev.type)
        val dropInWindow = history.any { qualifiesAsDrop(it, n) }
        if (probe.volume != rs1) {
            val reason = when {
                dropInWindow -> "volume_changed_after_drop"
                probe.volume < rs1 -> "below_rs1"
                else -> "no_drop_seen"
            }
            return listOf(skip(p.id, n, dev, reason, lowered = probe.volume))
        }
        val entry = tracker[dev.key]
        val reason = when {
            entry == null || entry.level <= rs1 -> "unknown_pre_drop"
            entry.at >= n.postT + Windows.W_AFTER -> "unknown_pre_drop"
            deviceChanges.any { it > entry.at } -> "device_changed"
            entry.level > probe.max -> "invalid_target"
            breakerTrips(dev.key, probe.now) -> "circuit_breaker"
            else -> null
        }
        if (reason != null) return listOf(skip(p.id, n, dev, reason, before = entry?.level, lowered = probe.volume))
        return restore(p.id, n, dev, from = probe.volume, target = entry!!.level, signal = "notif+tracker", tDrop = null, probe = probe, countsForBreaker = true)
    }

    /** The 5× path lowers twice (SoundDoseHelper, then SystemUI): re-restore a second drop once. */
    private fun checkEcho(change: VolumeChange, probe: Probe): List<Action> {
        val e = echo ?: return emptyList()
        val dev = change.device
        if (change.t > e.until || dev == null || dev.key != e.deviceKey) {
            if (change.t > e.until) echo = null
            return emptyList()
        }
        echo = null
        val isEcho = change.prev == e.target && change.new == Rs1.of(dev.type)
        if (!isEcho) return emptyList() // Phil changed it: his change stands, the echo watch ends.
        if (probe.device?.key != dev.key || probe.volume != change.new) return emptyList()
        if (dev.key in settings().excluded) return emptyList()
        val id = nextId++
        return restore(id, e.notice, dev, from = change.new, target = e.target, signal = "echo", tDrop = change.t, probe = probe, countsForBreaker = false)
    }

    private fun restore(
        id: Long, notice: CsdNotice, dev: Device, from: Int, target: Int, signal: String,
        tDrop: Long?, probe: Probe, countsForBreaker: Boolean,
    ): List<Action> {
        if (countsForBreaker) restoreTimes.getOrPut(dev.key) { ArrayDeque() }.addLast(probe.now)
        expectSelf = ExpectSelf(dev.key, target, probe.now + Windows.SELF)
        echo = if (signal == "echo") null else Echo(dev.key, target, probe.now + Windows.ECHO, notice)
        val d = Decision(
            id = id, notice = notice, device = dev, before = target, lowered = from, restored = target,
            signal = signal, outcome = Outcome.PENDING, reason = null, tDrop = tDrop, tRestore = probe.now,
        )
        inFlight[id] = InFlight(d, target, from)
        return listOf(Action.Record(d), Action.Restore(id, dev, target))
    }

    private fun breakerTrips(key: String, now: Long): Boolean {
        pausedUntil[key]?.let { if (now < it) return true else pausedUntil.remove(key) }
        val times = restoreTimes[key] ?: return false
        while (times.isNotEmpty() && times.first() < now - Windows.BREAKER_SPAN) times.removeFirst()
        if (times.size >= Windows.BREAKER_MAX) {
            pausedUntil[key] = now + Windows.BREAKER_PAUSE
            times.clear()
            return true
        }
        return false
    }

    private fun skip(
        id: Long, notice: CsdNotice?, device: Device?, reason: String,
        before: Int? = null, lowered: Int? = null, tDrop: Long? = null,
    ): Action = Action.Record(
        Decision(
            id = id, notice = notice, device = device, before = before, lowered = lowered, restored = null,
            signal = null, outcome = Outcome.SKIPPED, reason = reason, tDrop = tDrop,
        ),
    )

    private fun prune(now: Long) {
        while (history.isNotEmpty() && (history.first().t < now - Windows.HISTORY_SPAN || history.size > 64)) history.removeFirst()
        while (deviceChanges.isNotEmpty() && deviceChanges.first() < now - Windows.HISTORY_SPAN) deviceChanges.removeFirst()
    }

    /** True while a breaker pause is active for [key] (shown on the Devices screen). */
    fun isPaused(key: String, now: Long): Boolean = pausedUntil[key]?.let { now < it } ?: false
}
