package br.com.copilotoia99

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class RegionLearning(val region: String, val samples: Int, val meanWaitMinutes: Double)
data class Offer(val id: Long, val price: Double, val km: Double, val minutes: Double, val rating: Double, val score: Int, val verdict: String, val createdAt: Long)

class OfferStore(context: Context) : SQLiteOpenHelper(context, "copiloto99.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE offers(id INTEGER PRIMARY KEY AUTOINCREMENT, price REAL NOT NULL, km REAL NOT NULL, minutes REAL NOT NULL, rating REAL NOT NULL, score INTEGER NOT NULL, verdict TEXT NOT NULL, created_at INTEGER NOT NULL, region TEXT NOT NULL DEFAULT '', pickup_km REAL NOT NULL DEFAULT 0, pickup_minutes REAL NOT NULL DEFAULT 0, confidence TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE region_learning(region TEXT PRIMARY KEY, samples INTEGER NOT NULL, mean_wait REAL NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE offers ADD COLUMN region TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE offers ADD COLUMN pickup_km REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE offers ADD COLUMN pickup_minutes REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE offers ADD COLUMN confidence TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE IF NOT EXISTS region_learning(region TEXT PRIMARY KEY, samples INTEGER NOT NULL, mean_wait REAL NOT NULL)")
        }
    }
    fun add(input: OfferInput, result: Evaluation): Long {
        val v = ContentValues().apply {
            put("price", input.price); put("km", input.pickupKm+input.tripKm)
            put("minutes", input.pickupMinutes+input.tripMinutes); put("rating", input.rating)
            put("score", result.score); put("verdict", result.verdict); put("created_at", System.currentTimeMillis())
            put("region", input.region); put("pickup_km", input.pickupKm); put("pickup_minutes", input.pickupMinutes)
            put("confidence", result.confidence)
        }
        return writableDatabase.insertOrThrow("offers", null, v)
    }
    // Keep older instrumentation test compatible.
    fun add(price: Double, km: Double, minutes: Double, rating: Double, score: Int, verdict: String): Long {
        val v = ContentValues().apply {
            put("price",price); put("km",km); put("minutes",minutes); put("rating",rating)
            put("score",score); put("verdict",verdict); put("created_at",System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("offers",null,v)
    }
    fun getLearning(region: String): RegionLearning? {
        readableDatabase.rawQuery("SELECT samples,mean_wait FROM region_learning WHERE region=?",arrayOf(region)).use { c ->
            if(c.moveToFirst()) return RegionLearning(region,c.getInt(0),c.getDouble(1))
        }
        return null
    }
    fun observeWait(region: String, actualMinutes: Double) {
        require(region.isNotBlank() && actualMinutes >= 0 && actualMinutes <= 240)
        writableDatabase.beginTransaction()
        try {
            val old = getLearning(region)
            val n = (old?.samples ?: 0) + 1
            // Recency-weighted exponentially smoothed observations; conservative sample count.
            val mean = if (old == null) actualMinutes else old.meanWaitMinutes * 0.8 + actualMinutes * 0.2
            val v = ContentValues().apply { put("region",region); put("samples",n); put("mean_wait",mean) }
            writableDatabase.insertWithOnConflict("region_learning",null,v,SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun all(): List<Offer> {
        val rows = mutableListOf<Offer>()
        readableDatabase.rawQuery("SELECT id,price,km,minutes,rating,score,verdict,created_at FROM offers ORDER BY id DESC",null).use { c ->
            while(c.moveToNext()) rows.add(Offer(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getDouble(4),c.getInt(5),c.getString(6),c.getLong(7)))
        }
        return rows
    }
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM offers",null).use { c -> c.moveToFirst(); c.getInt(0) }
}
