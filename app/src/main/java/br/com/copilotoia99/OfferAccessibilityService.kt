package br.com.copilotoia99

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/**
 * Reads 99 offers using accessible nodes, falling back to a single accessibility
 * screenshot processed locally by ML Kit. No MediaProjection or continuous recording.
 * Never accepts or rejects rides.
 */
class OfferAccessibilityService : AccessibilityService() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastFingerprint = ""
    private var lastPublishedAt = 0L
    private var lastDiagnosticAt = 0L
    private var lastScreenshotAt = 0L
    private var screenshotBusy = false
    private var lastOfferSeenAt = 0L
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        diagnostic("Acessibilidade ativa • aguardando oferta")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.lowercase(Locale.ROOT) ?: return
        if (pkg == packageName.lowercase(Locale.ROOT)) return
        if (!pkg.contains("99") && !pkg.contains("didi")) return
        if (event.eventType !in intArrayOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )) return

        val out = StringBuilder()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let(roots::add)
        event.source?.let(roots::add)
        try {
            windows?.forEach { it.root?.let(roots::add) }
            roots.forEach { collectText(it, out, 0) }
        } finally {
            roots.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
        val raw = out.toString()
        if (raw.isNotBlank() && processOffer(raw, "acessibilidade")) return
        requestScreenshot()
    }

    private fun requestScreenshot() {
        val now = SystemClock.elapsedRealtime()
        if (screenshotBusy || now - lastScreenshotAt < 1800L) return
        lastScreenshotAt = now
        screenshotBusy = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            screenshotBusy = false
            diagnostic("Captura por acessibilidade indisponível neste Android")
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null }
                    finally { buffer.close() }
                    if (bitmap == null) {
                        screenshotBusy = false
                        diagnostic("Falha ao converter imagem da oferta")
                        return
                    }
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { recognized ->
                            if (!processOffer(recognized.text, "OCR")) {
                                diagnostic("99 detectada • dados da oferta não identificados")
                            }
                        }
                        .addOnFailureListener { diagnostic("OCR não conseguiu ler a oferta") }
                        .addOnCompleteListener {
                            bitmap.recycle()
                            screenshotBusy = false
                        }
                }
                override fun onFailure(errorCode: Int) {
                    screenshotBusy = false
                    diagnostic("Captura de acessibilidade indisponível (código $errorCode)")
                }
            })
        } catch (_: Exception) {
            screenshotBusy = false
            diagnostic("Não foi possível capturar a oferta")
        }
    }

    private fun processOffer(raw: String, source: String): Boolean {
        val parsed = OfferTextParser.parse(raw)
        if (!parsed.complete) return false
        val fingerprint = "${parsed.price}:${parsed.pickupKm}:${parsed.tripKm}:${parsed.pickupMinutes}:${parsed.tripMinutes}"
        val now = SystemClock.elapsedRealtime()
        if (fingerprint == lastFingerprint && now - lastPublishedAt < 8000L) return true
        lastFingerprint = fingerprint
        lastPublishedAt = now
        lastOfferSeenAt = now
        val offer = OfferInput(
            parsed.price!!, parsed.pickupKm!!, parsed.tripKm!!,
            parsed.pickupMinutes!!, parsed.tripMinutes!!,
            parsed.rating ?: 0.0, "Desconhecida"
        )
        val prefs = getSharedPreferences("driver_limits", MODE_PRIVATE)
        val limits = DriverLimits(
            prefs.getFloat("km", 1.8f).toDouble(),
            prefs.getFloat("hour", 35f).toDouble(),
            prefs.getFloat("fare", 10f).toDouble(),
            if (parsed.rating == null) 0.0 else prefs.getFloat("rating", 4.7f).toDouble(),
            prefs.getFloat("pickupKm", 8f).toDouble(),
            prefs.getFloat("pickupMin", 15f).toDouble()
        )
        try {
            val evaluation = OfferEvaluator.evaluate(offer, limits)
            val summary = String.format(Locale("pt", "BR"),
                "%s %d/100 • R$ %.2f/km • R$ %.2f/h%s",
                evaluation.verdict, evaluation.score, evaluation.perKm, evaluation.perHour,
                if (parsed.rating == null) " • nota não lida" else "")
            publish("$source: $summary")
            showOverlay(summary, evaluation.verdict)
            Thread {
                try { OfferStore(applicationContext).use { it.add(offer, evaluation) } }
                catch (_: Exception) {}
            }.start()
            EvolutionRecorder.recordAsync(this, "accessibility_offer_detected")
        } catch (_: Exception) { diagnostic("Oferta lida, mas avaliação falhou") }
        return true
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 22 || out.length > 5000) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                try { collectText(child, out, depth + 1) }
                finally { child.recycle() }
            }
        }
    }

    private fun diagnostic(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDiagnosticAt < 5000L || now - lastOfferSeenAt < 10000L) return
        lastDiagnosticAt = now
        publish(message)
        showOverlay("Copiloto IA 99 • $message")
    }

    private fun publish(message: String) {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit()
            .putString("last_status", message)
            .putLong("updated_at", System.currentTimeMillis()).apply()
    }

    private fun showOverlay(message: String, verdict: String? = null) {
        if (!Settings.canDrawOverlays(this)) return
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (overlay == null) {
            val view = TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xE51C2732.toInt())
                textSize = 14f
                setPadding(18, 12, 18, 12)
                maxLines = 3
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
            try { windowManager?.addView(view, params); overlay = view }
            catch (_: Exception) {}
        }
        overlay?.apply {
            text = message
            setBackgroundColor(when (verdict) {
                "EXCELENTE", "BOA" -> 0xE51B873D.toInt()
                "REGULAR" -> 0xE5C58A12.toInt()
                "RUIM" -> 0xE5B4232C.toInt()
                else -> 0xE51C2732.toInt()
            })
        }
    }

    override fun onInterrupt() { publish("Acessibilidade interrompida") }

    override fun onDestroy() {
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlay = null
        recognizer.close()
        super.onDestroy()
    }
}
