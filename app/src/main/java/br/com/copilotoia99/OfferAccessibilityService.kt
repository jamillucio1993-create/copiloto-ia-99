package br.com.copilotoia99

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import com.google.mlkit.vision.text.Text
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearExpired = Runnable { hideOverlay() }
    private var last99EventAt = 0L
    private fun mark99Detected(pkg: String) {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit()
            .putLong("last_99_seen_at", System.currentTimeMillis())
            .putString("last_99_package", pkg)
            .putBoolean("service_connected", true).apply()
    }

    private var lastReadStartedAt = 0L
    private val poller = object : Runnable {
        override fun run() {
            val root = rootInActiveWindow
            if (root != null) {
                lastReadStartedAt = SystemClock.elapsedRealtime()
                val pkg = root.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
                val out = StringBuilder()
                try { collectText(root, out, 0) } finally { root.recycle() }
                if (pkg.contains("99") || pkg.contains("didi")) {
                    mark99Detected(pkg)
                    if (out.contains("buscando viagens", true) || out.contains("procurando viagens", true)) clearOffer()
                    else if (!processOffer(out.toString(), "acessibilidade") && (pendingFingerprint == null || SystemClock.elapsedRealtime() - pendingAt > 1200L)) requestScreenshot()
                    mainHandler.postDelayed(this, 700L)
                } else {
                    clearOffer()
                    mainHandler.postDelayed(this, 1500L)
                }
            } else if (SystemClock.elapsedRealtime() - last99EventAt < 10000L) {
                // A transient null window must not permanently stop monitoring.
                mainHandler.postDelayed(this, 700L)
            } else {
                // Continue checking for the 99 app even if no event arrived yet.
                mainHandler.postDelayed(this, 1500L)
            }
        }
    }
    private var lastOfferConfirmedAt = 0L
    private var pendingFingerprint: String? = null
    private var pendingAt = 0L
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastFingerprint = ""
    private var lastPublishedAt = 0L
    private var lastDiagnosticAt = 0L
    private var lastScreenshotAt = 0L
    private var screenshotBusy = false
    private var lastOfferSeenAt = 0L
    private var overlay: TextView? = null
    private var overlayVisibleUntil = 0L
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", true).apply()
        diagnostic("Acessibilidade ativa • aguardando oferta")
        mainHandler.removeCallbacks(poller)
        mainHandler.postDelayed(poller, 700L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.lowercase(Locale.ROOT) ?: return
        if (pkg == packageName.lowercase(Locale.ROOT)) return
        if (!pkg.contains("99") && !pkg.contains("didi")) {
            clearOffer()
            mainHandler.removeCallbacks(poller)
            mainHandler.postDelayed(poller, 1500L)
            return
        }
        mark99Detected(pkg)
        last99EventAt = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(poller)
        mainHandler.postDelayed(poller, 700L)
        if (event.eventType !in intArrayOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )) return

        lastReadStartedAt = SystemClock.elapsedRealtime()
        val out = StringBuilder()
        val root = rootInActiveWindow
        if (root != null) {
            try {
                val activePkg = root.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
                if (activePkg.contains("99") || activePkg.contains("didi")) collectText(root, out, 0)
            } finally { root.recycle() }
        }
        val raw = out.toString()
        if (raw.contains("buscando viagens", true) || raw.contains("procurando viagens", true)) { clearOffer(); return }
        if (raw.isNotBlank() && processOffer(raw, "acessibilidade")) return
        // A complete offer awaiting its second reading is not an OCR failure.
        if (pendingFingerprint == null || SystemClock.elapsedRealtime() - pendingAt > 1200L) requestScreenshot()
    }

    private fun requestScreenshot() {
        val now = SystemClock.elapsedRealtime()
        if (screenshotBusy || now - lastScreenshotAt < 1000L) return
        lastReadStartedAt = now
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
                            val cards = extractCardTexts(recognized)
                            val valid = cards.filter { OfferTextParser.parse(it).complete }
                            if (valid.size == 1) processOffer(valid.first(), "OCR por cartão")
                            else if (valid.size > 1) {
                                clearOffer()
                                diagnostic("Múltiplas ofertas • selecione uma")
                            } else {
                                val combined = recognized.text
                                if (!processOffer(combined, "OCR tela inteira")) diagnostic("99 detectada • leitura incompleta")
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

    private fun extractCardTexts(recognized: Text): List<String> {
        val lines = recognized.textBlocks.flatMap { it.lines }
            .filter { it.boundingBox != null }
            .sortedWith(compareBy({ it.boundingBox!!.top }, { it.boundingBox!!.left }))
        val anchor = Regex("""R\$\s*\d+[.,]\d{2}""")
        val fares = lines.filter {
            anchor.containsMatchIn(it.text) && !it.text.contains("/km", true) &&
                !it.text.contains("por embarque", true) && !it.text.contains("por km", true)
        }
        if (fares.isEmpty()) return emptyList()
        // A single fare can still be accompanied by unrelated prices or a
        // partially visible card. The parser must validate the full card.
        if (fares.size == 1) return listOf(lines.joinToString("\n") { it.text })
        // Cards in the same row cannot safely be split by Y coordinate.
        // Refuse to mix their fare, pickup and trip fields.
        val overlappingRows = fares.zipWithNext().any { (a, b) ->
            val first = a.boundingBox!!
            val second = b.boundingBox!!
            first.top < second.bottom && second.top < first.bottom
        }
        if (overlappingRows) return emptyList()
        val centers = fares.map { it.boundingBox!!.centerY() }.sorted()
        val boundaries = centers.zipWithNext { a, b -> (a + b) / 2 }
        val groups = MutableList(centers.size) { mutableListOf<Text.Line>() }
        for (line in lines) {
            val y = line.boundingBox!!.centerY()
            val index = boundaries.indexOfFirst { y < it }.let { if (it < 0) groups.lastIndex else it }
            groups[index].add(line)
        }
        return groups.map { group -> group.joinToString("\n") { it.text } }
    }

    private fun processOffer(raw: String, source: String): Boolean {
        val parsed = OfferTextParser.parse(raw)
        if (!parsed.complete) {
            if (raw.contains("R$", true)) publish("Leitura incompleta: valor=${parsed.price ?: "ausente"}, embarque=${parsed.pickupKm ?: "km?"}/${parsed.pickupMinutes ?: "min?"}, viagem=${parsed.tripKm ?: "km?"}/${parsed.tripMinutes ?: "min?"}")
            return false
        }
        val fingerprint = "${parsed.price}:${parsed.pickupKm}:${parsed.tripKm}:${parsed.pickupMinutes}:${parsed.tripMinutes}:${parsed.rating}"
        val now = SystemClock.elapsedRealtime()
        if (fingerprint != pendingFingerprint || now - pendingAt > 6000L) {
            pendingFingerprint = fingerprint
            pendingAt = now
            publish("Oferta identificada • aguardando confirmação")
            return false
        }
        if (now - pendingAt < 250L) return true
        if (fingerprint == lastFingerprint && now - lastPublishedAt < 8000L) {
            lastOfferConfirmedAt = now
            if (overlay != null && overlay?.visibility != android.view.View.VISIBLE) {
                overlay?.visibility = android.view.View.VISIBLE
            }
            mainHandler.removeCallbacks(clearExpired)
            mainHandler.postDelayed(clearExpired, 8000L)
            return true
        }
        lastFingerprint = fingerprint
        lastPublishedAt = now
        lastOfferSeenAt = now
        lastOfferConfirmedAt = now
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
            getSharedPreferences("accessibility_status", MODE_PRIVATE).edit()
                .putLong("last_latency_ms", (SystemClock.elapsedRealtime() - lastReadStartedAt).coerceAtLeast(0L))
                .putString("last_source", source).apply()
            publish("$source: $summary")
            showOverlay(summary, evaluation.verdict)
            Thread {
                try { OfferStore(applicationContext).use { it.add(offer, evaluation) } }
                catch (_: Exception) {}
            }.start()
            EvolutionRecorder.recordAsync(this, "offer_detected")
        } catch (_: Exception) { diagnostic("Oferta lida, mas avaliação falhou") }
        return true
    }

    private fun clearOffer() {
        lastFingerprint = ""
        pendingFingerprint = null
        // Clear the last offer after its display window when the 99 stops presenting it.
        mainHandler.removeCallbacks(clearExpired)
        mainHandler.postDelayed(clearExpired, 8000L)
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
        // Diagnostics remain available in the app, without a persistent floating window.
    }

    private fun publish(message: String) {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit()
            .putString("last_status", message)
            .putLong("updated_at", System.currentTimeMillis()).apply()
    }

    private fun hideOverlay() {
        overlay?.visibility = android.view.View.GONE
        overlayVisibleUntil = 0L
    }

    private fun showOverlay(message: String, verdict: String? = null) {
        if (!Settings.canDrawOverlays(this)) {
            publish("Oferta detectada, mas permissão de janela flutuante bloqueada")
            return
        }
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (overlay == null) {
            val view = TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xE51C2732.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(12, 8, 12, 8)
                maxLines = 2
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (resources.displayMetrics.density * 38).toInt()
            }
            try { windowManager?.addView(view, params); overlay = view }
            catch (e: Exception) { publish("Falha ao abrir janela: ${e.javaClass.simpleName}") }
        }
        if (overlay == null) return
        overlay?.apply {
            visibility = android.view.View.VISIBLE
            text = message
            setBackgroundColor(when (verdict) {
                "EXCELENTE", "BOA" -> 0xE51B873D.toInt()
                "REGULAR" -> 0xE5C58A12.toInt()
                "RUIM" -> 0xE5B4232C.toInt()
                else -> 0xE51C2732.toInt()
            })
        }
        publish("Janela automática exibida: $verdict")
        // Keep the result readable while the offer continues to be detected.
        overlayVisibleUntil = SystemClock.elapsedRealtime() + 8000L
        mainHandler.removeCallbacks(clearExpired)
        mainHandler.postDelayed(clearExpired, 8000L)
    }

    override fun onInterrupt() {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", false).apply()
        publish("Acessibilidade interrompida")
    }

    override fun onDestroy() {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", false).apply()
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlay = null
        mainHandler.removeCallbacks(clearExpired)
        mainHandler.removeCallbacks(poller)
        recognizer.close()
        super.onDestroy()
    }
}
