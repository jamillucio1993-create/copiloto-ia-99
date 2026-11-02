package br.com.copilotoia99

import org.junit.Assert.*
import org.junit.Test

class OfferCardSegmenterTest {
    private fun line(t: String, y: Int) = CardLine(t, 0, y, 300, y + 15)

    @Test fun separatesThreeOffersAndIgnoresButtonPrices() {
        val lines = listOf(
            line("R$ 7,76", 10), line("4,89 • 40 corridas", 25),
            line("9 min (2,5 km)", 40), line("10 min (1,8 km)", 55),
            line("Aceitar por R$ 7,76", 75),
            line("R$ 10,25", 110), line("4,94 • 60 corridas", 125),
            line("9 min (2,3 km)", 140), line("18 min (3,8 km)", 155),
            line("Aceitar por R$ 10,25", 175),
            line("R$ 18,00", 210), line("3 min (1 km)", 225),
            line("14 min (7 km)", 240), line("Aceitar por R$ 18,00", 260)
        )
        val cards = OfferCardSegmenter.segment(lines)
        assertEquals(3, cards.size)
        assertEquals(listOf(7.76, 10.25, 18.0), cards.map { OfferTextParser.parse(it.text).price })
        assertTrue(cards.all { OfferTextParser.parse(it.text).complete })
    }

    @Test fun usesAcceptPriceWhenPrimaryFareIsAbsent() {
        val cards = OfferCardSegmenter.segment(listOf(
            line("9 min (2,5 km)", 20), line("10 min (1,8 km)", 40),
            line("Aceitar por R$ 7,76", 60)
        ))
        assertEquals(7.76, OfferTextParser.parse(cards.single().text).price!!, 0.001)
    }

    @Test fun neverInventsCardBoundariesForMultipleUnanchoredPrices() {
        val cards = OfferCardSegmenter.segment(listOf(
            line("R$ 7,76", 10), line("R$ 10,25", 70),
            line("9 min (2,5 km)", 20), line("10 min (1,8 km)", 40)
        ))
        assertTrue(cards.isEmpty())
    }

    @Test fun auxiliaryPriceIsNotAnotherRide() {
        val cards = OfferCardSegmenter.segment(listOf(
            line("R$ 18,80", 10), line("R$ 1,57/km", 25),
            line("7 min (2 km)", 40), line("19 min (10 km)", 55),
            line("Aceitar por R$ 18,80", 75)
        ))
        assertEquals(1, cards.size)
        assertTrue(OfferTextParser.parse(cards.single().text).complete)
    }
}
