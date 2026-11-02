package br.com.copilotoia99

import org.junit.Assert.*
import org.junit.Test

class OfferTextParserTest {
    @Test fun popNova() {
        val p = OfferTextParser.parse("Pop Nova\nR$27,10 R$1,58/km\n4,95 • 600 corridas\n8 min (2 km)\n24 min (15,1 km)\nEscolher")
        assertTrue(p.complete)
        assertEquals(27.10, p.price!!, 0.001)
        assertEquals(2.0, p.pickupKm!!, 0.001)
        assertEquals(15.1, p.tripKm!!, 0.001)
        assertEquals(4.95, p.rating!!, 0.001)
    }
    @Test fun dinheiroWithAuxiliaryPrice() {
        val p = OfferTextParser.parse("Dinheiro\nR$18,80\nR$1,57/km\nR$3,06 por embarque distante\n4,92 · 356 corridas\n7 min (2 km)\n19 min (10 km)")
        assertTrue(p.complete)
        assertEquals(18.80, p.price!!, 0.001)
        assertEquals(4.92, p.rating!!, 0.001)
    }
    @Test fun metersAreConverted() {
        val p = OfferTextParser.parse("R$8,50\n4,90 • 30 corridas\n3 min (850 m)\n9 min (2,5 km)")
        assertTrue(p.complete)
        assertEquals(0.85, p.pickupKm!!, 0.001)
    }
    @Test fun twoCardsMustNotBeMixed() {
        assertFalse(OfferTextParser.parse("R$27,10\n8 min (2 km)\n24 min (15,1 km)\nR$18,80\n7 min (2 km)\n19 min (10 km)").complete)
    }
    @Test fun partialOrSearchScreenMustNotBeRated() {
        assertFalse(OfferTextParser.parse("Buscando viagens perto de você").complete)
        assertFalse(OfferTextParser.parse("R$18,80 7 min (2 km)").complete)
    }
}
