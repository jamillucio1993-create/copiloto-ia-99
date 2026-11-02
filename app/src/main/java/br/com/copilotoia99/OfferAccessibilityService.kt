package br.com.copilotoia99

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * Reads visible offer text from the 99 driver app through Android Accessibility.
 * It never clicks, accepts, rejects, or otherwise controls ride offers.
 */
class OfferAccessibilityService : AccessibilityService() {
    private var lastFingerprint = ""
    private var lastPublishedAt = 0L
    private var lastDiagnosticAt = 0L
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        publish("Automação por acessibilidade ativada")
        showOverlay("Copiloto IA 99 • aguardando oferta")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.lowercase() ?: return
        if (pkg == packageName) return
        // Restrict collection to package names plausibly belonging to the 99/Didi app.
        if (!pkg.contains("99") && !pkg.contains("didi")) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text, 0)
        root.recycle()
        val raw = text.toString().trim()
        if (raw.isBlank()) {
            diagnostic("99 detectada, mas sem texto acessível")
            return
        }

        val parsed = OfferTextParser.parse(raw)
        if (!parsed.complete) {
            val amounts = Regex("""R\\$\\s*[0-9]+(?:[.,][0-9]{2})?""").findAll(raw).count()
            diagnostic("Tela 99 lida • ${raw.length} caracteres • ${amounts} valores • dados insuficientes para avaliar")
            return
        }
        val fingerprint = "${parsed.price}:${parsed.pickupKm}:${parsed.tripKm}:${parsed.pickupMinutes}:${parsed.tripMinutes}"
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint || now - lastPublishedAt < 1200) return
        lastFingerprint = fingerprint
        lastPublishedAt = now

        val offer = OfferInput(
            parsed.price!!, parsed.pickupKm!!, parsed.tripKm!!,
            parsed.pickupMinutes!!, parsed.tripMinutes!!,
            4.99, "Desconhecida"
        )
        val prefs = getSharedPreferences("driver_limits", MODE_PRIVATE)
        val limits = DriverLimits(
            prefs.getFloat("km", 1.8f).toDouble(),
            prefs.getFloat("hour", 35f).toDouble(),
            prefs.getFloat("fare", 10f).toDouble(),
            prefs.getFloat("rating", 4.7f).toDouble(),
            prefs.getFloat("pickupKm", 8f).toDouble(),
            prefs.getFloat("pickupMin", 15f).toDouble()
        )
        val evaluation = OfferEvaluator.evaluate(offer, limits)
        val summary = "${evaluation.verdict} ${evaluation.score}/100 • R$ %.2f/km • R$ %.2f/h".format(
            java.util.Locale("pt", "BR"), evaluation.perKm, evaluation.perHour
        )
        publish(summary)
        showOverlay(summary)
        Thread {
            try { OfferStore(applicationContext).use { it.add(offer, evaluation) } } catch (_: Exception) {}
        }.start()
        EvolutionRecorder.recordAsync(this, "accessibility_offer_detected")
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 22 || out.length > 5000) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append('\n')
            out.append(it)
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (out.isNotEmpty()) out.append('\n')
            out.append(it)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
            child.recycle()
        }
    }

    private fun diagnostic(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastDiagnosticAt < 5000) return
        lastDiagnosticAt = now
        publish(message)
        showOverlay("Copiloto IA 99 • " + message)
    }

    private fun publish(message: String) {
        sendBroadcast(Intent(CaptureService.ACTION_UPDATE).setPackage(packageName)
            .putExtra(CaptureService.EXTRA_SUMMARY, message))
    }

    private fun showOverlay(message: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (overlay == null) {
            val view = TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xE51C2732.toInt())
                textSize = 14f
                setPadding(18, 12, 18, 12)
                maxLines = 2
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (resources.displayMetrics.density * 38).toInt()
            }
            try {
                windowManager?.addView(view, params)
                overlay = view
            } catch (_: Exception) {}
        }
        overlay?.text = message
    }

    override fun onInterrupt() {
        publish("Automação interrompida pelo Android")
    }

    override fun onDestroy() {
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlay = null
        super.onDestroy()
    }
}