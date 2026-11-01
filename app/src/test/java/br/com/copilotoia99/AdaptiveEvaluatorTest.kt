package br.com.copilotoia99
import org.junit.Assert.*
import org.junit.Test
class AdaptiveEvaluatorTest {
    @Test fun totalPickupIncluded() {
        val result=OfferEvaluator.evaluate(OfferInput(30.0,2.0,10.0,6.0,24.0,4.95,"Centro"))
        assertEquals(2.5,result.perKm,0.0001)
        assertEquals(60.0,result.perHour,0.0001)
    }
    @Test fun mandatoryLimitsNeverOverriddenByDestination() {
        val result=OfferEvaluator.evaluate(OfferInput(100.0,1.0,9.0,2.0,20.0,4.0,"Centro",0.0))
        assertEquals("RUIM",result.verdict)
        assertTrue(result.explanation.contains("nota do passageiro"))
    }
    @Test fun unknownDestinationLowersConfidence() {
        val result=OfferEvaluator.evaluate(OfferInput(30.0,2.0,10.0,6.0,24.0,4.95,"Nova"))
        assertNull(result.destinationScore)
        assertTrue(result.confidence.startsWith("BAIXA"))
    }
    @Test fun historyInformsDestinationScore() {
        val result=OfferEvaluator.evaluate(OfferInput(30.0,2.0,10.0,6.0,24.0,4.95,"Centro"),history=RegionLearning("Centro",15,25.0))
        assertEquals(50,result.destinationScore)
        assertNotNull(result.projectedPerHourWithWait)
    }
}
