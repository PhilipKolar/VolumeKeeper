package com.philkolar.volumekeeper.runtime

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.philkolar.volumekeeper.core.Action
import com.philkolar.volumekeeper.core.CsdNotice
import com.philkolar.volumekeeper.core.CsdNotificationMatcher
import com.philkolar.volumekeeper.core.Decision
import com.philkolar.volumekeeper.core.DecisionEngine
import com.philkolar.volumekeeper.core.Device
import com.philkolar.volumekeeper.core.EngineSettings
import com.philkolar.volumekeeper.core.MatchKind
import com.philkolar.volumekeeper.core.NotificationInfo
import com.philkolar.volumekeeper.core.Outcome
import com.philkolar.volumekeeper.core.Probe
import com.philkolar.volumekeeper.core.Rs1
import com.philkolar.volumekeeper.core.Source
import com.philkolar.volumekeeper.core.VolumeChange
import com.philkolar.volumekeeper.core.Windows
import com.philkolar.volumekeeper.service.KeepAliveService
import com.philkolar.volumekeeper.service.VkListenerService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide wiring. Started by whichever comes first — the notification listener, the boot
 * receiver, the keep-alive service or the JS module — and lives as long as the process.
 *
 * Every engine call happens on the single "vk-engine" HandlerThread: the receivers and observers
 * are registered with [handler], and listener callbacks are posted onto it. The engine is pure
 * (core/); this object only reads AudioManager into a Probe and executes the engine's Actions.
 */
object VkRuntime {
    private const val TAG = "VolumeKeeper"

    @Volatile private var started = false
    lateinit var app: Context
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var store: Store
        private set
    lateinit var handler: Handler
        private set
    private lateinit var audio: AudioManager
    private lateinit var resolver: DeviceResolver
    private lateinit var engine: DecisionEngine
    private lateinit var observer: AudioObserver

    @Volatile private var excluded: Set<String> = emptySet()
    @Volatile var listenerConnected = false
        private set
    @Volatile private var lastListenerConnect: Long? = null
    @Volatile private var lastListenerDisconnect: Long? = null
    @Volatile private var currentDevice: Device? = null

    /** Engine decision id → events row id (engine thread only). Ids restart each process; rows don't. */
    private val rowIds = object : LinkedHashMap<Long, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > 200
    }
    private val trace = ArrayDeque<String>()
    private val bus = CopyOnWriteArraySet<(String) -> Unit>()

    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            app = context.applicationContext
            prefs = Prefs(app)
            store = Store(app)
            excluded = store.excludedKeys()
            val thread = HandlerThread("vk-engine").apply { start() }
            handler = Handler(thread.looper)
            audio = app.getSystemService(AudioManager::class.java)
            resolver = DeviceResolver(app, audio, prefs)
            engine = DecisionEngine { EngineSettings(prefs.enabled, excluded) }
            observer = AudioObserver(app, audio, handler, ::onVolumeBroadcast, ::onOutputs, ::onSettings)
            Notifier.ensureChannels(app, prefs.simulationEnabled)
            started = true
            handler.post {
                observer.register()
                readCurrent("start")
                log("service", "engine_started")
            }
        }
    }

    // -- bus (JS module subscribes) ---------------------------------------------------------------

    fun addListener(l: (String) -> Unit) { bus += l }
    fun removeListener(l: (String) -> Unit) { bus -= l }
    private fun emit(what: String) = bus.forEach { runCatching { it(what) } }

    // -- inputs -----------------------------------------------------------------------------------

    private fun probe() = Probe(
        SystemClock.elapsedRealtime(),
        resolver.current(),
        audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
    )

    private fun noteDevice(dev: Device?, t: Long): Boolean {
        if (dev?.key == currentDevice?.key) return false
        currentDevice = dev
        engine.onDeviceChanged(t)
        addTrace("output → ${dev?.name ?: "not headphones"}")
        return true
    }

    private fun onVolumeBroadcast(prev: Int, new: Int, t: Long) {
        val dev = resolver.current()
        noteDevice(dev, t)
        addTrace("vol $prev→$new ${dev?.name ?: "-"}")
        if (dev != null) store.observeLevel(dev, new, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), "broadcast", System.currentTimeMillis())
        run(engine.onVolumeChanged(VolumeChange(t, prev, new, dev, Source.BROADCAST), probe()))
        emit("device")
    }

    private fun onOutputs(t: Long) {
        val dev = resolver.current()
        if (!noteDevice(dev, t)) return
        emit("status")
        // Bluetooth applies a headset's remembered volume as it connects: read once it has settled.
        handler.postDelayed({ readCurrent("read") }, Windows.DEVICE_READ_DELAY)
    }

    private fun onSettings(name: String, t: Long) {
        val dev = resolver.current() ?: return
        if (name != "volume_music_" + dev.type.settingsSuffix) return
        val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val known = engine.tracker[dev.key]?.level
        if (known == null) {
            engine.onVolumeRead(dev, v, t)
            return
        }
        if (known == v) return
        addTrace("settings $known→$v ${dev.name}")
        run(engine.onVolumeChanged(VolumeChange(t, known, v, dev, Source.SETTINGS), probe()))
    }

    private fun readCurrent(source: String) {
        val dev = resolver.current()
        if (currentDevice?.key != dev?.key) noteDevice(dev, SystemClock.elapsedRealtime())
        if (dev == null) return
        val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        engine.onVolumeRead(dev, v, SystemClock.elapsedRealtime())
        store.observeLevel(dev, v, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), source, System.currentTimeMillis())
        addTrace("$source ${dev.name} = $v")
        emit("device")
    }

    /** Called on the main thread by the listener; snapshot first, decide on the engine thread. */
    fun onNotification(info: NotificationInfo) {
        if (!started) return
        val recvT = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        handler.post {
            val m = CsdNotificationMatcher.match(info, app.packageName, prefs.simulationEnabled)
            when (m.kind) {
                MatchKind.CSD -> {
                    val postT = recvT - (wall - info.postWall)
                    val notice = CsdNotice(
                        key = info.key, postWall = info.postWall, postT = postT, recvT = recvT, kind = m.csdKind,
                        id = info.id, channel = info.channel, title = info.title, text = info.text,
                        actions = info.actions, simulated = m.simulated,
                    )
                    addTrace("notice ${m.csdKind} lag ${recvT - postT} ms${if (m.simulated) " (sim)" else ""}")
                    if (recvT - postT > 2_000 && recvT - postT <= Windows.STALE) {
                        log("warning", "notice_delivery_lag", detail = "${recvT - postT} ms after posting")
                    }
                    run(engine.onCsdNotice(notice, probe()))
                }
                MatchKind.NEAR_MISS -> {
                    log("near_miss", info.title ?: "(no title)", detail = infoJson(info))
                }
                MatchKind.OTHER -> Unit
            }
        }
    }

    fun onListenerConnected() {
        listenerConnected = true
        lastListenerConnect = System.currentTimeMillis()
        handler.post { log("service", "listener_connected") }
        emit("status")
    }

    fun onListenerDisconnected() {
        listenerConnected = false
        lastListenerDisconnect = System.currentTimeMillis()
        handler.post { log("service", "listener_disconnected") }
        emit("status")
    }

    // -- actions ----------------------------------------------------------------------------------

    private fun run(actions: List<Action>) {
        for (a in actions) when (a) {
            is Action.Schedule -> {
                val delay = (a.at - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                handler.postDelayed({ run(engine.onTimer(a.token, probe())) }, delay)
            }
            is Action.Restore -> {
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, a.target, 0)
                } catch (e: SecurityException) {
                    Log.w(TAG, "setStreamVolume refused", e)
                }
                addTrace("restore ${a.device.name} → ${a.target}")
                handler.postDelayed({ run(engine.onVerify(a.restoreId, probe())) }, Windows.VERIFY)
            }
            is Action.Record -> record(a.decision)
        }
    }

    private fun record(d: Decision) {
        val wall = System.currentTimeMillis()
        val lag = d.notice?.let { it.recvT - it.postT }
        rowIds[d.id] = store.recordDecision(rowIds[d.id], d, wall, d.latencyMs, lag)
        val dev = d.device
        when (d.outcome) {
            Outcome.RESTORED -> {
                if (dev != null) store.recordRestore(dev.key, d.lowered, d.restored, wall)
                if (prefs.notifyOnRestore && dev != null) Notifier.restored(app, "${dev.name}: ${d.lowered} → ${d.restored}")
                val n = d.notice
                if (prefs.dismissSystemNotice && n != null && !n.simulated && d.signal != "echo") VkListenerService.cancel(n.key)
            }
            Outcome.FAILED -> if (prefs.notifyOnFailure) {
                Notifier.failure(
                    app, "Couldn't restore the volume",
                    "Android lowered ${dev?.name ?: "your headphones"} to ${d.lowered} and ignored the restore to ${d.restored}. " +
                        "Turn on the keep-alive service in VolumeKeeper settings.",
                )
            }
            Outcome.SKIPPED -> if (d.reason == "unknown_pre_drop" && prefs.notifyOnFailure && prefs.enabled) {
                Notifier.failure(
                    app, "Volume lowered",
                    "VolumeKeeper didn't know the level ${dev?.name ?: "your headphones"} was at before Android lowered it, so it left it alone.",
                )
            }
            Outcome.PENDING -> Unit
        }
        addTrace("decision ${d.id} ${d.outcome}${d.reason?.let { " ($it)" } ?: ""}")
        emit("event")
        emit("device")
    }

    private fun log(kind: String, reason: String, device: Device? = null, detail: String? = null) {
        store.logEvent(kind, System.currentTimeMillis(), reason, device, detail)
        emit("event")
    }

    private fun addTrace(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        synchronized(trace) {
            trace.addLast("$stamp $line")
            while (trace.size > 200) trace.removeFirst()
        }
        Log.d(TAG, line)
    }

    private fun infoJson(i: NotificationInfo): String = JSONObject()
        .put("pkg", i.pkg).put("id", i.id).put("tag", i.tag).put("channel", i.channel)
        .put("title", i.title).put("text", i.text).put("actions", JSONArray(i.actions)).put("postWall", i.postWall)
        .toString()

    // -- JS-facing --------------------------------------------------------------------------------

    fun traceSnapshot(): List<String> = synchronized(trace) { trace.toList().asReversed() }

    fun status(): Map<String, Any?> {
        val nm = app.getSystemService(NotificationManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        val dev = resolver.current()
        return mapOf(
            "packageName" to app.packageName,
            "isDevVariant" to prefs.isDevVariant,
            "simulationEnabled" to prefs.simulationEnabled,
            "listenerGranted" to nm.isNotificationListenerAccessGranted(ComponentName(app, VkListenerService::class.java)),
            "listenerConnected" to listenerConnected,
            "lastListenerConnect" to lastListenerConnect?.toDouble(),
            "lastListenerDisconnect" to lastListenerDisconnect?.toDouble(),
            "btPermission" to resolver.hasBtConnect(),
            "notificationsAllowed" to nm.areNotificationsEnabled(),
            "batteryUnrestricted" to pm.isIgnoringBatteryOptimizations(app.packageName),
            "keepAliveRunning" to KeepAliveService.running,
            "settings" to prefs.toMap(),
            "device" to dev?.let { mapOf("key" to it.key, "name" to it.name, "type" to it.type.name, "rs1" to Rs1.of(it.type)) },
            "volume" to audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            "maxVolume" to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            "importance" to importance(),
        )
    }

    private fun importance(): Int = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance

    fun setSetting(name: String, value: Boolean): Boolean {
        if (!prefs.set(name, value)) return false
        when (name) {
            Prefs.KEY_KEEP_ALIVE -> applyKeepAlive()
            Prefs.KEY_SPEAKER_TEST -> handler.post { onOutputs(SystemClock.elapsedRealtime()) }
        }
        handler.post { log("service", "setting $name=$value") }
        emit("status")
        return true
    }

    fun setExcluded(key: String, value: Boolean) {
        store.setExcluded(key, value)
        excluded = store.excludedKeys()
        emit("device")
    }

    fun forget(key: String) {
        store.forget(key)
        excluded = store.excludedKeys()
        emit("device")
    }

    fun applyKeepAlive() {
        val intent = Intent(app, KeepAliveService::class.java)
        if (prefs.keepAlive) {
            try {
                app.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "keep-alive start refused", e)
                handler.post { log("warning", "keep_alive_start_refused", detail = e.toString()) }
            }
        } else {
            app.stopService(intent)
        }
    }

    // -- dev variant: simulation + background restore test ----------------------------------------

    /**
     * Mimics a CSD event: lowers STREAM_MUSIC to the current output's RS1 itself and posts a
     * look-alike notification, in [order] with [gapMs] between them. Optional [from] sets a
     * starting level first (as a user change) and waits for it to settle.
     */
    fun simulate(order: String, gapMs: Long, from: Int?, fiveX: Boolean) {
        if (!prefs.simulationEnabled) return
        handler.post {
            if (from != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC, from, 0)
            handler.postDelayed({
                val dev = resolver.current()
                val rs1 = dev?.let { Rs1.of(it.type) } ?: 6
                val drop = Runnable { audio.setStreamVolume(AudioManager.STREAM_MUSIC, rs1, 0) }
                val notice = Runnable { Notifier.simulatedCsd(app, fiveX) }
                addTrace("simulate $order gap $gapMs")
                when (order) {
                    "notif-first" -> { notice.run(); handler.postDelayed(drop, gapMs) }
                    "drop-only" -> drop.run()
                    "notif-only" -> notice.run()
                    else -> { drop.run(); handler.postDelayed(notice, gapMs) }
                }
                handler.postDelayed({ Notifier.cancelSimulated(app) }, gapMs + 15_000)
            }, if (from != null) 1_500L else 0L)
        }
    }

    /** S3: set the volume from wherever the process is (background, screen off) and read it back. */
    fun testSet(value: Int) {
        handler.post {
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val imp = importance()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            handler.postDelayed({
                val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                log(
                    "test", if (after == value) "applied" else "not_applied", resolver.current(),
                    detail = "set $before→$value, read $after, importance $imp, listener ${if (listenerConnected) "connected" else "not connected"}",
                )
            }, Windows.VERIFY)
        }
    }
}
