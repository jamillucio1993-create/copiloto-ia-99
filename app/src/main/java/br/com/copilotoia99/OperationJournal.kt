package br.com.copilotoia99

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Local event journal: no screenshots, video, passenger names or exact GPS coordinates. */
class OperationJournal(context: Context) {
    private val file = File(context.filesDir, "operation-events.jsonl")
    private val allowed = setOf(
        "OFFER_SEEN", "OFFER_RECOMMENDED", "OFFER_DISAPPEARED",
        "RIDE_ACCEPTED", "PICKUP_STARTED", "PASSENGER_ONBOARD",
        "RIDE_COMPLETED", "RIDE_CANCELLED", "REPOSITION_STARTED",
        "REPOSITION_ENDED", "WORK_SESSION_STARTED", "WORK_SESSION_ENDED"
    )
    @Synchronized
    fun record(
        event: String,
        rideId: String? = null,
        source: String,
        confidence: String,
        metrics: Map<String, Double> = emptyMap(),
        regionCode: String? = null
    ): String {
        require(event in allowed)
        require(source in setOf("ACCESSIBILITY", "LOCATION", "USER", "INFERENCE"))
        require(confidence in setOf("CONFIRMED", "ESTIMATED", "UNKNOWN"))
        require(metrics.values.all { it.isFinite() })
        val id = UUID.randomUUID().toString()
        val obj = JSONObject()
            .put("id", id).put("timestamp", Instant.now().toString())
            .put("event", event).put("source", source).put("confidence", confidence)
        if (rideId != null) obj.put("rideId", rideId)
        if (regionCode != null) obj.put("regionCode", regionCode.take(60))
        val values = JSONObject()
        metrics.forEach { (key, value) ->
            if (key.matches(Regex("[a-zA-Z0-9_]{1,40}"))) values.put(key, value)
        }
        obj.put("metrics", values)
        file.appendText(obj.toString() + "\n")
        return id
    }

    /** Aggregate diagnostics for review; never uploads automatically. */
    @Synchronized
    fun report(): JSONObject {
        val counts = mutableMapOf<String, Int>()
        var total = 0
        var uncertain = 0
        if (file.exists()) file.forEachLine { line ->
            runCatching {
                val event = JSONObject(line)
                val name = event.optString("event")
                counts[name] = (counts[name] ?: 0) + 1
                if (event.optString("confidence") != "CONFIRMED") uncertain++
                total++
            }
        }
        val events = JSONObject()
        counts.toSortedMap().forEach { (key, count) -> events.put(key, count) }
        return JSONObject().put("schemaVersion", 1)
            .put("totalEvents", total).put("uncertainEvents", uncertain)
            .put("eventsByType", events)
            .put("note", "Counts reflect only events actually recorded by the app.")
    }
}
