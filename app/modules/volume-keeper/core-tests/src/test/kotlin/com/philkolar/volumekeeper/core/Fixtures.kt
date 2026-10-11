package com.philkolar.volumekeeper.core

val SONY = Device("bt:88:C9:E8:69:C1:2E", OutputType.BT_A2DP, "WH-1000XM5")
val SHOKZ = Device("bt:20:74:CF:00:21:43", OutputType.BT_A2DP, "OpenRun Pro 2")
val WIRED = Device("wired:headphone", OutputType.WIRED_HEADPHONES, "Wired headphones")

/** A tiny phone: the engine plus the state a real AudioManager would report in the probe. */
class Harness(var enabled: Boolean = true, val excluded: MutableSet<String> = mutableSetOf()) {
    val engine = DecisionEngine { EngineSettings(enabled, excluded.toSet()) }
    var now = 1_000_000L
    var device: Device? = SONY
    var volume = 15
    val max = 25
    val actions = mutableListOf<Action>()
    private var noticeSeq = 0

    fun probe() = Probe(now, device, volume, max)
    fun advance(ms: Long) { now += ms }

    /** The volume really changes and the broadcast arrives. */
    fun change(to: Int, source: Source = Source.BROADCAST): List<Action> {
        val prev = volume
        volume = to
        return run(engine.onVolumeChanged(VolumeChange(now, prev, to, device, source), probe()))
    }

    /** The volume changes but the broadcast is lost. */
    fun silentChange(to: Int) { volume = to }

    fun switchTo(d: Device?, level: Int) {
        device = d
        volume = level
        engine.onDeviceChanged(now)
    }

    fun read() { device?.let { engine.onVolumeRead(it, volume, now) } }

    fun notice(postedAgo: Long = 0, kind: CsdKind = CsdKind.DOSE_1X, key: String = "0|com.android.systemui|1007|null|10246"): List<Action> {
        noticeSeq++
        val n = CsdNotice(
            key = key, postWall = 1_760_000_000_000L + noticeSeq, postT = now - postedAgo, recvT = now,
            kind = kind, id = 1007, channel = "ALR", title = "Volume lowered to safer level", text = null,
            actions = emptyList(), simulated = false,
        )
        return run(engine.onCsdNotice(n, probe()))
    }

    fun timers(): List<Action> {
        val due = actions.filterIsInstance<Action.Schedule>()
        return due.flatMap { s -> if (s.at <= now) run(engine.onTimer(s.token, probe())) else emptyList() }
    }

    /** Executes a Restore like the adapter would: apply (unless [blocked]) and deliver our own broadcast. */
    fun applyRestores(blocked: Boolean = false): List<Action> {
        val out = mutableListOf<Action>()
        for (r in actions.filterIsInstance<Action.Restore>().filter { it.restoreId !in applied }) {
            applied += r.restoreId
            if (!blocked) out += change(r.target)
            advance(Windows.VERIFY)
            out += run(engine.onVerify(r.restoreId, probe()))
        }
        return out
    }

    private val applied = mutableSetOf<Long>()

    private fun run(a: List<Action>): List<Action> { actions += a; return a }

    fun restores() = actions.filterIsInstance<Action.Restore>()
    fun records() = actions.filterIsInstance<Action.Record>().map { it.decision }
    fun lastRecord() = records().last()
}
