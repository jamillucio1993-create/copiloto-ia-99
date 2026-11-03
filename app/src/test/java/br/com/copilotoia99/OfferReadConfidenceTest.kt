package br.com.copilotoia99

import org.junit.Assert.*
import org.junit.Test

class OfferReadConfidenceTest {
    @Test fun completeOfferCanBeEvaluated() {
        val result = OfferReadConfidence.assess(20.0, 2.0, 7.0, 5.0, 18.0)
        assertEquals(OfferReadConfidence.Level.COMPLETE, result.level)
        assertTrue(result.canEvaluate)
    }

    @Test fun missingFieldsPreventVerdict() {
        val result = OfferReadConfidence.assess(20.0, null, 7.0, null, 18.0)
        assertEquals(OfferReadConfidence.Level.PARTIAL, result.level)
        assertFalse(result.canEvaluate)
        assertTrue(result.missing.contains("km até embarque"))
    }

    @Test fun invalidFarePreventsVerdict() {
        val result = OfferReadConfidence.assess(-1.0, 2.0, 7.0, 5.0, 18.0)
        assertFalse(result.canEvaluate)
        assertTrue(result.invalid.contains("valor"))
    }

    @Test fun noDataIsInsufficient() {
        val result = OfferReadConfidence.assess(null, null, null, null, null)
        assertEquals(OfferReadConfidence.Level.INSUFFICIENT, result.level)
    }

    @Test fun nonFiniteValueIsInvalid() {
        val result = OfferReadConfidence.assess(Double.NaN, 2.0, 7.0, 5.0, 18.0)
        assertFalse(result.canEvaluate)
    }
}
