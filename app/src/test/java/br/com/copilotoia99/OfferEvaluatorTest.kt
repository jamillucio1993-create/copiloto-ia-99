package br.com.copilotoia99
import org.junit.Assert.*
import org.junit.Test
class OfferEvaluatorTest {
    @Test fun calculatesTotalPickupEconomics() {
        val e = OfferEvaluator.evaluate(30.0,12.0,30.0,4.95)
        assertEquals(2.5,e.perKm,0.001)
        assertEquals(60.0,e.perHour,0.001)
    }
    @Test fun mandatoryThresholdOverridesScore() {
        val e = OfferEvaluator.evaluate(100.0,10.0,20.0,4.0)
        assertTrue(e.verdict.startsWith("RUIM"))
    }
    @Test fun unknownRatingDoesNotRejectProfitableOffer() {
        val e = OfferEvaluator.evaluate(OfferInput(45.0, 1.0, 9.0, 3.0, 20.0, 0.0, "Desconhecida"))
        assertFalse(e.explanation.contains("nota do passageiro"))
        assertTrue(e.verdict != "RUIM")
    }
    @Test fun genuinelyLowRatingStillRejectsOffer() {
        val e = OfferEvaluator.evaluate(OfferInput(45.0, 1.0, 9.0, 3.0, 20.0, 4.2, "Desconhecida"))
        assertEquals("RUIM", e.verdict)
        assertTrue(e.explanation.contains("nota do passageiro"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidDistance() { OfferEvaluator.evaluate(20.0,0.0,15.0,4.9) }
}
