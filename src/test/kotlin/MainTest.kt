import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith

class MainTest {

    private lateinit var service: ExchangeRateService

    @BeforeTest
    fun setup() {
        service = ExchangeRateService()
    }

    @Test
    fun `test exchange rate caching`() = runBlocking {
        val base = "USD"
        val symbols = listOf("EUR", "JPY")

        val result1 = service.getExchangeRates(base, symbols)
        val result2 = service.getExchangeRates(base, symbols)

        // Should be exactly equal and second one comes from cache
        assertEquals(result1, result2)
    }

    @Test
    fun `test metrics increase on requests`() = runBlocking {
        val base = "USD"
        val symbols = listOf("EUR", "GBP")

        val initialMetrics = service.getMetrics()
        service.getExchangeRates(base, symbols)
        val updatedMetrics = service.getMetrics()

        assertTrue(updatedMetrics.totalQueries > initialMetrics.totalQueries)
        assertTrue(updatedMetrics.apis.any { it.totalRequests > 0 })
    }

    @Test
    fun `test response contains all requested symbols`() = runBlocking {
        val base = "USD"
        val symbols = listOf("EUR", "GBP", "JPY")

        val result = service.getExchangeRates(base, symbols)
        assertEquals(symbols.toSet(), result.rates.keys)
        result.rates.values.forEach {
            assertTrue("Exchange rate must be positive", it > 0.0)
        }
    }

    @Test
    fun `test exception for invalid currency`() {
        runBlocking {
            assertFailsWith<Exception> {
                service.getExchangeRates("XXX", listOf("YYY"))
            }
        }
    }


    @Test
    fun `test multiple symbols metrics tracking`() = runBlocking {
        service.getExchangeRates("USD", listOf("EUR", "GBP", "JPY"))
        val metrics = service.getMetrics()
        assertTrue(metrics.totalQueries > 0)
        assertTrue(metrics.apis.any { it.totalRequests > 0 })
        assertTrue(metrics.apis.any { it.totalResponses > 0 })
    }
}
