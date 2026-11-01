package br.com.copilotoia99

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Configuração remota limitada a parâmetros não críticos; nunca executa código remoto. */
data class EvolutionPolicy(
    val version: Int = 0,
    val timeMultiplier: Double = 1.0,
    val waitWeight: Double = 0.10,
    val scanIntervalMs: Int = 350,
    val expiresAtEpochMs: Long = Long.MAX_VALUE
)

object EvolutionValidator {
    fun validate(candidate: EvolutionPolicy, currentVersion: Int, now: Long): Boolean =
        candidate.version > currentVersion &&
        candidate.expiresAtEpochMs > now &&
        candidate.timeMultiplier in 1.0..1.6 &&
        candidate.waitWeight in 0.0..0.20 &&
        candidate.scanIntervalMs in 250..1200
}

class EvolutionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("evolution_policy", Context.MODE_PRIVATE)
    fun enabled() = prefs.getBoolean("consent", false)
    fun setEnabled(value: Boolean) { prefs.edit().putBoolean("consent", value).apply() }
    fun current(): EvolutionPolicy = EvolutionPolicy(
        prefs.getInt("version",0),
        prefs.getFloat("timeMultiplier",1f).toDouble(),
        prefs.getFloat("waitWeight",.10f).toDouble(),
        prefs.getInt("scanIntervalMs",350),
        prefs.getLong("expiresAt",Long.MAX_VALUE)
    )
    fun previewReport(totalOffers: Int, expiredOffers: Int, avgLatencyMs: Int): JSONObject = JSONObject().apply {
        put("schema_version",1)
        put("device_class","galaxy_a54")
        put("offers",totalOffers.coerceAtLeast(0))
        put("expired",expiredOffers.coerceIn(0,totalOffers.coerceAtLeast(0)))
        put("avg_latency_ms",avgLatencyMs.coerceAtLeast(0))
        put("policy_version",current().version)
        // Sem nomes, rotas, imagens, localização exata ou identificadores do passageiro.
    }
    /** Somente após verificação de assinatura no transporte e avaliação local. */
    fun applyVerified(candidate: EvolutionPolicy): Boolean {
        val old=current()
        if (!enabled() || !EvolutionValidator.validate(candidate,old.version,System.currentTimeMillis())) return false
        prefs.edit()
            .putInt("rollbackVersion",old.version)
            .putFloat("rollbackTimeMultiplier",old.timeMultiplier.toFloat())
            .putFloat("rollbackWaitWeight",old.waitWeight.toFloat())
            .putInt("rollbackScanIntervalMs",old.scanIntervalMs)
            .putLong("rollbackExpiresAt",old.expiresAtEpochMs)
            .putInt("version",candidate.version)
            .putFloat("timeMultiplier",candidate.timeMultiplier.toFloat())
            .putFloat("waitWeight",candidate.waitWeight.toFloat())
            .putInt("scanIntervalMs",candidate.scanIntervalMs)
            .putLong("expiresAt",candidate.expiresAtEpochMs)
            .apply()
        return true
    }
    fun rollback() {
        if (!prefs.contains("rollbackVersion")) return
        prefs.edit()
            .putInt("version",prefs.getInt("rollbackVersion",0))
            .putFloat("timeMultiplier",prefs.getFloat("rollbackTimeMultiplier",1f))
            .putFloat("waitWeight",prefs.getFloat("rollbackWaitWeight",.10f))
            .putInt("scanIntervalMs",prefs.getInt("rollbackScanIntervalMs",350))
            .putLong("expiresAt",prefs.getLong("rollbackExpiresAt",Long.MAX_VALUE))
            .apply()
    }
}
