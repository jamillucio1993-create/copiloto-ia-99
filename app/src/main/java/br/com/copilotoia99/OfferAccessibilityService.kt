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
    private var is99Foreground = false
    private var lastConnectionPopupAt = 0L
    private fun on99Foreground() {
        if (is99Foreground) return
        is99Foreground = true
        val now = SystemClock.elapsedRealtime()
        if (now - lastConnectionPopupAt < 1500L) return
        lastConnectionPopupAt = now
        publish("99 detectada • janela de conexão exibida")
        showOverlay("✓ COPILOTO CONECTADO À 99 • aguardando oferta", "CONEXAO")
    }

    private fun on99Background() {
        if (!is99Foreground) return
        is99Foreground = false
        mainHandler.removeCallbacks(clearExpired)
        hideOverlay()
        publish("99 saiu do primeiro plano • janela recolhida")
    }

    private fun mark99Detected(pkg: String) {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit()
            .putLong("last_99_seen_at", System.currentTimeMillis())
            .putString("last_99_package", pkg)
            .putBoolean("service_connected", true).apply()
    }

    private var lastReadStartedAt = 0L
    private var lastRawFingerprint = ""
    private var screenGeneration = 0L
    private var lastAccessibleText = ""
    private var lastAccessibleAt = 0L
    private var lastOcrText = ""
    private var lastOcrAt = 0L
    private val scanOnChange = Runnable { scanActiveWindow() }
    private fun scanActiveWindow() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
        if (!pkg.contains("99") && !pkg.contains("didi")) {
            root.recycle()
            if (SystemClock.elapsedRealtime() - last99EventAt > 1800L) on99Background()
            return
        }
        val out = StringBuilder()
        try { collectText(root, out, 0) } finally { root.recycle() }
        mark99Detected(pkg)
        on99Foreground()
        val raw = out.toString()
        if (raw.contains("buscando viagens", true) || raw.contains("procurando viagens", true)) {
            clearOffer()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (raw == lastAccessibleText && now - lastAccessibleAt < 1600L) return
        if (raw != lastAccessibleText) screenGeneration++
        lastAccessibleText = raw
        lastAccessibleAt = now
        if (raw.isBlank()) { requestScreenshot(); return }
        val fareCount = Regex("""R\$\s*\d+[.,]\d{2}""").findAll(raw).count()
        if (fareCount > 1) { requestScreenshot(); return }
        if (!processOffer(raw, "acessibilidade")) requestScreenshot()
    }
    private val poller = object : Runnable {
        override fun run() {
            scanActiveWindow()
            mainHandler.postDelayed(this, if (is99Foreground) 650L else 1200L)
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
    private val listOverlays = mutableListOf<TextView>()
    private var overlayVisibleUntil = 0L
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", true).apply()
        diagnostic("Acessibilidade ativa • aguardando oferta")
        mainHandler.removeCallbacks(poller)
        mainHandler.postDelayed(poller, 250L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.lowercase(Locale.ROOT) ?: return
        if (pkg == packageName.lowercase(Locale.ROOT)) return
        if (!pkg.contains("99") && !pkg.contains("didi")) {
            mainHandler.removeCallbacks(scanOnChange)
            mainHandler.postDelayed(scanOnChange, 200L)
            return
        }
        mark99Detected(pkg)
        on99Foreground()
        last99EventAt = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(poller)
        mainHandler.postDelayed(poller, 250L)
        if (event.eventType !in intArrayOf(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )) return

        lastReadStartedAt = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(scanOnChange)
        mainHandler.postDelayed(scanOnChange, 90L)
    }

    private fun requestScreenshot() {
        val now = SystemClock.elapsedRealtime()
        if (screenshotBusy || now - lastScreenshotAt < 650L) return
        lastReadStartedAt = now
        lastScreenshotAt = now
        screenshotBusy = true
        val capturedGeneration = screenGeneration
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
                            if (capturedGeneration != screenGeneration || !is99Foreground) return@addOnSuccessListener
                            val ocrNow = SystemClock.elapsedRealtime()
                            if (recognized.text == lastOcrText && ocrNow - lastOcrAt < 1200L) return@addOnSuccessListener
                            lastOcrText = recognized.text
                            lastOcrAt = ocrNow
                            val cards = extractCardTexts(recognized)
                            val valid = cards.filter { OfferTextParser.parse(it).complete }
                            if (cards.size > 1) {
                                showListOffers(cards.take(3))
                            } else if (valid.size == 1) {
                                processOffer(valid.first(), "OCR por cartão")
                            } else {
                                val combined = recognized.text
                                if (!processOffer(combined, "OCR tela inteira"))
                                    diagnostic("99 detectada • leitura incompleta")
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

    private fun showListOffers(cards: List<String>) {
        if (cards.isEmpty() || !Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("driver_limits", MODE_PRIVATE)
        val limits = DriverLimits(
            prefs.getFloat("km", 1.8f).toDouble(),
            prefs.getFloat("hour", 35f).toDouble(),
            prefs.getFloat("fare", 10f).toDouble(),
            0.0,
            prefs.getFloat("pickupKm", 8f).toDouble(),
            prefs.getFloat("pickupMin", 15f).toDouble()
        )
        val items = cards.take(3).mapIndexed { index, card ->
            val parsed = OfferTextParser.parse(card)
            if (!parsed.complete) {
                val fare = parsed.price?.let { String.format(Locale("pt", "BR"), "R$ %.2f", it) } ?: "valor indisponível"
                Pair("CORRIDA ${index + 1} • $fare\nDados insuficientes para avaliar", 0xE5C58A12.toInt())
            } else {
                try {
                    val offer = OfferInput(parsed.price!!, parsed.pickupKm!!, parsed.tripKm!!,
                        parsed.pickupMinutes!!, parsed.tripMinutes!!, parsed.rating ?: 0.0, "Desconhecida")
                    val ev = OfferEvaluator.evaluate(offer, limits)
                    val text = String.format(Locale("pt", "BR"),
                        "CORRIDA %d • R$ %.2f • %s\nR$ %.2f/km • R$ %.2f/h",
                        index + 1, parsed.price, ev.verdict, ev.perKm, ev.perHour)
                    val color = when (ev.verdict) {
                        "EXCELENTE", "BOA" -> 0xE51B873D.toInt()
                        "REGULAR" -> 0xE5C58A12.toInt()
                        else -> 0xE5B4232C.toInt()
                    }
                    Pair(text, color)
                } catch (_: Exception) {
                    Pair("CORRIDA ${index + 1}\nNão foi possível avaliar", 0xE5C58A12.toInt())
                }
            }
        }
        val fingerprint = items.joinToString("|") { it.first }
        if (fingerprint == lastRawFingerprint && listOverlays.size == items.size &&
            listOverlays.all { it.visibility == android.view.View.VISIBLE }) return
        lastRawFingerprint = fingerprint
        if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay?.visibility = android.view.View.GONE
        try {
            while (listOverlays.size > items.size) {
                val removed = listOverlays.removeAt(listOverlays.lastIndex)
                windowManager?.removeView(removed)
            }
            items.forEachIndexed { index, item ->
                val view = if (index < listOverlays.size) listOverlays[index] else {
                    val created = TextView(this).apply {
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(20, 12, 20, 12)
                        maxLines = 3
                    }
                    val params = WindowManager.LayoutParams(
                        (resources.displayMetrics.widthPixels * 0.83f).toInt(),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = (resources.displayMetrics.density * (40 + index * 84)).toInt()
                    }
                    windowManager?.addView(created, params)
                    listOverlays.add(created)
                    created
                }
                view.text = item.first
                view.setBackgroundColor(item.second)
                view.visibility = android.view.View.VISIBLE
            }
            publish("99 detectada • ${items.size} avaliações independentes na lista")
            mainHandler.removeCallbacks(clearExpired)
            mainHandler.postDelayed(clearExpired, 8000L)
        } catch (e: Exception) {
            publish("Falha ao exibir lista: ${e.javaClass.simpleName}")
        }
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
        if (now - pendingAt < 120L) return true
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
        screenGeneration++
        lastAccessibleText = ""
        hideOverlay()
        lastFingerprint = ""
        lastRawFingerprint = ""
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
        listOverlays.forEach { it.visibility = android.view.View.GONE }
        lastRawFingerprint = ""
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
                maxLines = 10
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
        listOverlays.forEach { it.visibility = android.view.View.GONE }
        overlay?.apply {
            visibility = android.view.View.VISIBLE
            text = message
            setBackgroundColor(when (verdict) {
                "CONEXAO" -> 0xE51B557C.toInt()
                "LISTA" -> 0xEE162A3D.toInt()
                "EXCELENTE", "BOA" -> 0xE51B873D.toInt()
                "REGULAR" -> 0xE5C58A12.toInt()
                "RUIM" -> 0xE5B4232C.toInt()
                else -> 0xE51C2732.toInt()
            })
        }
        if (verdict != "CONEXAO") publish("Janela automática exibida: $verdict")
        // Keep the result readable while the offer continues to be detected.
        overlayVisibleUntil = SystemClock.elapsedRealtime() + 8000L
        mainHandler.removeCallbacks(clearExpired)
        if (verdict != "CONEXAO") mainHandler.postDelayed(clearExpired, 8000L)
    }

    override fun onInterrupt() {
        on99Background()
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", false).apply()
        publish("Acessibilidade interrompida")
    }

    override fun onDestroy() {
        getSharedPreferences("accessibility_status", MODE_PRIVATE).edit().putBoolean("service_connected", false).apply()
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        listOverlays.forEach { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        listOverlays.clear()
        overlay = null
        mainHandler.removeCallbacks(clearExpired)
        mainHandler.removeCallbacks(poller)
        mainHandler.removeCallbacks(scanOnChange)
        recognizer.close()
        super.onDestroy()
    }
}
