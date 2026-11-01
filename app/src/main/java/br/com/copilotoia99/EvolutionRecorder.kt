package br.com.copilotoia99

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.util.concurrent.Executors

/** Local-first, bounded diagnostic recorder. No screenshots, routes or passenger identifiers. */
class EvolutionRecorder(context: Context) : SQLiteOpenHelper(context, "evolution_recorder.db", null, 1) {
    companion object {
        const val MAX_EVENTS = 3000
        const val RETENTION_DAYS = 30L
        private const val DAY = 86_400_000L
        val writer = Executors.newSingleThreadExecutor()
        fun recordAsync(context: Context, kind: String, latencyMs: Long = 0, confidence: String = "", detail: String = "") {
            val app = context.applicationContext
            writer.execute { try { EvolutionRecorder(app).use { it.record(kind, latencyMs, confidence, detail) } } catch (_: Exception) {} }
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, at_ms INTEGER NOT NULL, kind TEXT NOT NULL, latency_ms INTEGER NOT NULL, confidence TEXT NOT NULL, detail TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_events_at ON events(at_ms)")
        db.execSQL("CREATE TABLE daily (day INTEGER NOT NULL, kind TEXT NOT NULL, count INTEGER NOT NULL, sum_latency INTEGER NOT NULL, PRIMARY KEY(day,kind))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    @Synchronized fun record(kind: String, latencyMs: Long = 0, confidence: String = "", detail: String = "") {
        require(kind in setOf("offer_detected", "offer_ended", "ocr_failed", "capture_error", "capture_started", "capture_stopped", "manual_offer"))
        val now = System.currentTimeMillis()
        val safeLatency = latencyMs.coerceIn(0, 120000)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("at_ms", now); put("kind", kind); put("latency_ms", safeLatency)
                put("confidence", confidence.take(20)); put("detail", detail.take(80))
            }
            db.insertOrThrow("events", null, values)
            db.execSQL("INSERT INTO daily(day,kind,count,sum_latency) VALUES(?,?,1,?) ON CONFLICT(day,kind) DO UPDATE SET count=count+1,sum_latency=sum_latency+excluded.sum_latency", arrayOf(now / DAY,kind,safeLatency))
            db.delete("events", "at_ms < ?", arrayOf((now - RETENTION_DAYS * DAY).toString()))
            db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT $MAX_EVENTS)")
            db.execSQL("DELETE FROM daily WHERE day < ?", arrayOf((now / DAY - 365).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun report(): JSONObject {
        val result=JSONObject().put("schema_version",2).put("source","evolution_recorder_local")
            .put("contains_screen_images",false).put("contains_passenger_identity",false)
            .put("generated_at_ms",System.currentTimeMillis())
        val summary=JSONObject()
        readableDatabase.rawQuery("SELECT kind,SUM(count),SUM(sum_latency) FROM daily WHERE day >= ? GROUP BY kind", arrayOf((System.currentTimeMillis()/DAY-30).toString())).use { c ->
            while(c.moveToNext()) {
                val n=c.getLong(1);val entry=JSONObject().put("count",n)
                if(n>0)entry.put("avg_latency_ms",c.getLong(2)/n)
                summary.put(c.getString(0),entry)
            }
        }
        val count=readableDatabase.rawQuery("SELECT COUNT(*) FROM events",null).use {c->c.moveToFirst();c.getInt(0)}
        result.put("event_types",summary).put("recent_event_rows",count)
        result.put("diagnostic_hints",org.json.JSONArray().apply {
            val detected=summary.optJSONObject("offer_detected")?.optLong("count")?:0
            val failed=summary.optJSONObject("ocr_failed")?.optLong("count")?:0
            if(failed>0)addHint(this,"Revisar falhas de OCR e testar recortes menores")
            if(detected==0L)addHint(this,"Ainda não há ofertas detectadas nesta janela")
        })
        return result
    }
    private fun addHint(arr:org.json.JSONArray, text:String) { arr.put(text) }
}
