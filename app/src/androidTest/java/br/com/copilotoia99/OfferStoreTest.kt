package br.com.copilotoia99
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class OfferStoreTest {
    @Test fun persistsAcrossDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = OfferStore(context)
        val id = db.add(33.0,11.0,25.0,4.9,80,"BOA")
        db.close()
        val reopened = OfferStore(context)
        assertTrue(reopened.all().any { it.id == id && it.price == 33.0 })
        reopened.close()
    }
}
