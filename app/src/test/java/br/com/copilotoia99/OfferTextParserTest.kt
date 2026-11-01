package br.com.copilotoia99
import org.junit.Assert.*
import org.junit.Test
class OfferTextParserTest {
 @Test fun parsesObservedExample(){
  val p=OfferTextParser.parse("R$ 11,98\n4 min 841 m\n7 min 2,3 km")
  assertTrue(p.complete); assertEquals(11.98,p.price!!,0.001); assertEquals(0.841,p.pickupKm!!,0.001)
 }
 @Test fun refusesPartial(){assertFalse(OfferTextParser.parse("R$ 16,90 7 min").complete)}
}
