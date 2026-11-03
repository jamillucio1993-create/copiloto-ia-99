package br.com.copilotoia99

import android.content.Context
import java.time.YearMonth

/** Stores only explicitly selected goals; never treats offers as realized income. */
class GoalPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("monthly_goals", Context.MODE_PRIVATE)
    fun save(month: YearMonth, type: GoalType, amount: Double) {
        require(amount.isFinite() && amount >= 0.0)
        prefs.edit().putString("month", month.toString())
            .putString("type", type.name).putString("amount", amount.toString()).apply()
    }
    fun load(month: YearMonth): Pair<GoalType, Double>? {
        if (prefs.getString("month", null) != month.toString()) return null
        val type = runCatching { GoalType.valueOf(prefs.getString("type", "") ?: "") }.getOrNull() ?: return null
        val amount = prefs.getString("amount", null)?.toDoubleOrNull() ?: return null
        if (!amount.isFinite() || amount < 0.0) return null
        return type to amount
    }
}
