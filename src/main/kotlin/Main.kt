import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            val service = ExchangeRateService()

            get("/exchangeRates/{baseCur}") {
                val base = call.parameters["baseCur"]?.uppercase() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val symbols = call.request.queryParameters["symbols"]?.split(",")?.map { it.uppercase() } ?: return@get call.respond(HttpStatusCode.BadRequest)

                try {
                    val result = service.getExchangeRates(base, symbols)
                    call.respond(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.BadGateway, mapOf("error" to e.message))
                }
            }

            get("/metrics") {
                call.respond(service.getMetrics())
            }
        }
    }.start(wait = true)
}

class ExchangeRateService {
    private val cache = ConcurrentHashMap<String, ExchangeRateResponse>()
    private val metrics = Metrics()
    private val client = HttpClient.newBuilder().build()

    suspend fun getExchangeRates(base: String, symbols: List<String>): ExchangeRateResponse = coroutineScope {
        val key = "$base:${symbols.sorted().joinToString(",")}"
        cache[key]?.let { return@coroutineScope it }

        val apis: List<Pair<String, suspend (String, List<String>) -> Map<String, Double>>> = listOf(
            "freeCurrencyRates" to { b, s -> getFromFawaz(b, s) },
            "frankfurter" to { b, s -> getFromFrankfurter(b, s) }
        )

        val results = apis.map { (name, api) ->
            metrics.incrementRequest(name)
            try {
                val result = async { api(base, symbols) }.await()
                metrics.incrementResponse(name)
                result
            } catch (e: Exception) {
                emptyMap<String, Double>()
            }
        }

        val averagedRates = symbols.associateWith { symbol ->
            val rates = results.mapNotNull { it[symbol] }
            if (rates.isEmpty()) throw Exception("No rates found for $symbol")
            rates.average()
        }

        val response = ExchangeRateResponse(base, averagedRates)
        cache[key] = response
        metrics.incrementQuery()
        response
    }

    fun getMetrics(): MetricsResponse = metrics.snapshot()

    private suspend fun getFromFawaz(base: String, symbols: List<String>): Map<String, Double> {
        val url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/${base.lowercase()}.json"
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        val json = Json.parseToJsonElement(response.body()).jsonObject
        val rates = json[base.lowercase()]?.jsonObject ?: return emptyMap()

        return symbols.mapNotNull { sym ->
            val value = rates[sym.lowercase()]?.jsonPrimitive?.content?.toDoubleOrNull()
            value?.let { sym to it }
        }.toMap()
    }

    private suspend fun getFromFrankfurter(base: String, symbols: List<String>): Map<String, Double> {
        val url = "https://api.frankfurter.app/latest?from=$base&to=${symbols.joinToString(",")}"
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        val json = Json.parseToJsonElement(response.body()).jsonObject
        val rates = json["rates"]?.jsonObject ?: return emptyMap()

        return symbols.mapNotNull { sym ->
            val value = rates[sym]?.jsonPrimitive?.content?.toDoubleOrNull()
            value?.let { sym to it }
        }.toMap()
    }
}

@Serializable
data class ExchangeRateResponse(val base: String, val rates: Map<String, Double>)

class Metrics {
    private var totalQueries = 0
    private val apiMetrics = ConcurrentHashMap<String, ApiMetrics>()

    fun incrementQuery() {
        totalQueries++
    }

    fun incrementRequest(api: String) {
        apiMetrics.computeIfAbsent(api) { ApiMetrics() }.totalRequests++
    }

    fun incrementResponse(api: String) {
        apiMetrics.computeIfAbsent(api) { ApiMetrics() }.totalResponses++
    }

    fun snapshot(): MetricsResponse = MetricsResponse(
        totalQueries,
        apiMetrics.map { (name, m) -> ApiMetricsResponse(name, m.totalRequests, m.totalResponses) }
    )
}

data class ApiMetrics(var totalRequests: Int = 0, var totalResponses: Int = 0)

@Serializable
data class MetricsResponse(val totalQueries: Int, val apis: List<ApiMetricsResponse>)

@Serializable
data class ApiMetricsResponse(val name: String, val totalRequests: Int, val totalResponses: Int)
