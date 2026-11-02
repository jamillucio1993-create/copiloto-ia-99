package br.com.copilotoia99

/** OCR geometry independent of Android, allowing deterministic unit tests. */
data class CardLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
}
data class SegmentedCard(val text: String, val top: Int, val bottom: Int)

object OfferCardSegmenter {
    private val accept = Regex("""aceitar\s*(?:por\s*)?R\$\s*\d+[.,]\d{2}""", RegexOption.IGNORE_CASE)
    private val fare = Regex("""R\$\s*\d+[.,]\d{2}""", RegexOption.IGNORE_CASE)
    private val auxiliary = Regex("""(?:/km|por\s*km|por\s*embarque|/h)""", RegexOption.IGNORE_CASE)
    private val suggested = Regex("""(?:sugerir|propor|negociar|aumentar|contraproposta)""", RegexOption.IGNORE_CASE)

    fun segment(input: List<CardLine>): List<SegmentedCard> {
        val lines = input.filter { it.text.isNotBlank() && it.bottom > it.top }
            .sortedWith(compareBy<CardLine> { it.top }.thenBy { it.left })
        if (lines.isEmpty()) return emptyList()
        val buttons = lines.filter { accept.containsMatchIn(it.text) }
        if (buttons.isEmpty()) {
            // Without card boundaries, never guess how several offers fit together.
            val prices = lines.filter { primaryFare(it.text) }.size
            return if (prices == 1) listOf(makeCard(lines)) else emptyList()
        }
        val result = mutableListOf<SegmentedCard>()
        var previousBottom = Int.MIN_VALUE
        for (button in buttons.take(3)) {
            val bottom = button.bottom
            val group = lines.filter { it.centerY > previousBottom && it.centerY <= bottom }
            previousBottom = bottom
            val cleaned = group.filterNot { accept.containsMatchIn(it.text) ||
                suggested.containsMatchIn(it.text) ||
                (auxiliary.containsMatchIn(it.text) && fare.containsMatchIn(it.text)) }
            val fares = cleaned.filter { primaryFare(it.text) }
            val text = if (fares.size == 1) cleaned.joinToString("\n") { it.text }
                else if (fares.isEmpty()) {
                    // Some layouts show the primary price only inside the accept button.
                    val buttonFare = fare.find(button.text)?.value
                    if (buttonFare == null) cleaned.joinToString("\n") { it.text }
                    else buttonFare + "\n" + cleaned.joinToString("\n") { it.text }
                } else cleaned.joinToString("\n") { it.text }
            result.add(SegmentedCard(text, group.minOfOrNull { it.top } ?: button.top, bottom))
        }
        return result
    }

    private fun primaryFare(text: String): Boolean =
        fare.containsMatchIn(text) && !accept.containsMatchIn(text) &&
            !auxiliary.containsMatchIn(text) && !suggested.containsMatchIn(text)

    private fun makeCard(lines: List<CardLine>): SegmentedCard =
        SegmentedCard(lines.joinToString("\n") { it.text }, lines.first().top, lines.maxOf { it.bottom })
}
