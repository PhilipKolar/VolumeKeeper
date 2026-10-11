package com.philkolar.volumekeeper.runtime

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.philkolar.volumekeeper.core.Decision
import com.philkolar.volumekeeper.core.Device
import com.philkolar.volumekeeper.core.Outcome
import org.json.JSONArray
import org.json.JSONObject

/**
 * The only persistent state: `devices` (one row per output, for display — never fed back into
 * the engine as a restore target) and `events` (the log, newest 1000 kept). Wall-clock ms throughout.
 */
class Store(context: Context) : SQLiteOpenHelper(context, "volumekeeper.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE devices (
              key TEXT PRIMARY KEY,
              type TEXT NOT NULL,
              name TEXT NOT NULL,
              last_level INTEGER,
              max_level INTEGER,
              last_level_at INTEGER,
              last_level_source TEXT,
              excluded INTEGER NOT NULL DEFAULT 0,
              restore_count INTEGER NOT NULL DEFAULT 0,
              last_restore_at INTEGER,
              last_restore_from INTEGER,
              last_restore_to INTEGER,
              first_seen_at INTEGER NOT NULL,
              last_seen_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE events (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              at INTEGER NOT NULL,
              kind TEXT NOT NULL,
              outcome TEXT,
              reason TEXT,
              csd_kind TEXT,
              device_key TEXT,
              device_name TEXT,
              before_level INTEGER,
              lowered INTEGER,
              restored INTEGER,
              signal TEXT,
              latency_ms INTEGER,
              notice_lag_ms INTEGER,
              simulated INTEGER NOT NULL DEFAULT 0,
              detail TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX events_at ON events(at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the only schema so far. Add ALTERs here; never drop Phil's log.
    }

    // -- devices ---------------------------------------------------------------------------------

    fun observeLevel(device: Device, level: Int, max: Int, source: String, wall: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("type", device.type.name)
            put("name", device.name)
            put("last_level", level)
            put("max_level", max)
            put("last_level_at", wall)
            put("last_level_source", source)
            put("last_seen_at", wall)
        }
        if (db.update("devices", cv, "key = ?", arrayOf(device.key)) == 0) {
            cv.put("key", device.key)
            cv.put("first_seen_at", wall)
            db.insert("devices", null, cv)
        }
    }

    fun recordRestore(key: String, from: Int?, to: Int?, wall: Long) {
        writableDatabase.execSQL(
            "UPDATE devices SET restore_count = restore_count + 1, last_restore_at = ?, last_restore_from = ?, last_restore_to = ? WHERE key = ?",
            arrayOf<Any?>(wall, from, to, key),
        )
    }

    fun setExcluded(key: String, excluded: Boolean) {
        val cv = ContentValues().apply { put("excluded", if (excluded) 1 else 0) }
        writableDatabase.update("devices", cv, "key = ?", arrayOf(key))
    }

    fun forget(key: String) {
        writableDatabase.delete("devices", "key = ?", arrayOf(key))
    }

    fun excludedKeys(): Set<String> =
        readableDatabase.rawQuery("SELECT key FROM devices WHERE excluded = 1", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun devices(): List<Map<String, Any?>> =
        readableDatabase.rawQuery("SELECT * FROM devices ORDER BY last_seen_at DESC", null).use { rows(it) }

    // -- events ----------------------------------------------------------------------------------

    /** Inserts or (same [rowId]) updates the row for one engine decision. Returns the row id. */
    fun recordDecision(rowId: Long?, d: Decision, wall: Long, latency: Long?, noticeLag: Long?): Long {
        val cv = ContentValues().apply {
            put("at", wall)
            put("kind", "csd")
            put("outcome", d.outcome.name)
            put("reason", d.reason)
            put("csd_kind", d.notice?.kind?.name)
            put("device_key", d.device?.key)
            put("device_name", d.device?.name)
            put("before_level", d.before)
            put("lowered", d.lowered)
            put("restored", if (d.outcome == Outcome.SKIPPED) null else d.restored)
            put("signal", d.signal)
            put("latency_ms", latency)
            put("notice_lag_ms", noticeLag)
            put("simulated", if (d.notice?.simulated == true) 1 else 0)
            put("detail", d.notice?.let { n ->
                JSONObject()
                    .put("id", n.id)
                    .put("channel", n.channel)
                    .put("title", n.title)
                    .put("text", n.text)
                    .put("actions", JSONArray(n.actions))
                    .put("postWall", n.postWall)
                    .toString()
            })
        }
        val db = writableDatabase
        if (rowId != null) {
            cv.remove("at") // keep the first timestamp
            db.update("events", cv, "id = ?", arrayOf(rowId.toString()))
            return rowId
        }
        val id = db.insert("events", null, cv)
        trim(db)
        return id
    }

    fun logEvent(kind: String, wall: Long, reason: String? = null, device: Device? = null, detail: String? = null): Long {
        val cv = ContentValues().apply {
            put("at", wall)
            put("kind", kind)
            put("reason", reason)
            put("device_key", device?.key)
            put("device_name", device?.name)
            put("detail", detail)
        }
        val db = writableDatabase
        val id = db.insert("events", null, cv)
        trim(db)
        return id
    }

    fun events(limit: Int): List<Map<String, Any?>> =
        readableDatabase.rawQuery("SELECT * FROM events ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { rows(it) }

    fun clearEvents() {
        writableDatabase.delete("events", null, null)
    }

    private fun trim(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM events WHERE id <= (SELECT MAX(id) - 1000 FROM events)")
    }

    private fun rows(c: Cursor): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        while (c.moveToNext()) {
            val m = LinkedHashMap<String, Any?>()
            for (i in 0 until c.columnCount) {
                m[c.getColumnName(i)] = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toDouble()
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    else -> c.getString(i)
                }
            }
            out += m
        }
        return out
    }
}
