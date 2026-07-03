package dev.horex.moneytracker.core.currency

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KtorExchangeRateUpdateServiceTest {
    @Test
    fun updateLatestRatesSavesOnlyExplicitlyRequestedSnapshots() = runBlocking {
        val repository = FakeCurrencyRatesRepository()
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("https://example.test/latest/USD", request.url.toString())
                respond(
                    content = """
                        {
                          "result": "success",
                          "time_last_update_unix": 1783036800,
                          "rates": {
                            "USD": 1,
                            "EUR": 0.93,
                            "TJS": 10.7,
                            "RUB": 78.5
                          }
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val service = KtorExchangeRateUpdateService(
            ratesRepository = repository,
            client = client,
            endpointBaseUrl = "https://example.test/latest",
        )

        val result = service.updateLatestRates(
            baseCurrency = "usd",
            targetCurrencies = listOf("eur", "USD", "tjs", "EUR"),
        )

        assertEquals("2026-07-03", result.snapshotDate)
        assertEquals("USD", result.baseCurrency)
        assertEquals(2, result.savedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(
            listOf(
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-07-03",
                    baseCurrency = "USD",
                    targetCurrency = "EUR",
                    rateE8 = 93_000_000L,
                ),
                SaveExchangeRateSnapshotInput(
                    snapshotDate = "2026-07-03",
                    baseCurrency = "USD",
                    targetCurrency = "TJS",
                    rateE8 = 1_070_000_000L,
                ),
            ),
            repository.savedSnapshots,
        )
    }

    @Test
    fun updateLatestRatesDoesNotPersistWhenNetworkFails() = runBlocking {
        val repository = FakeCurrencyRatesRepository()
        val client = HttpClient(
            MockEngine {
                respondError(HttpStatusCode.ServiceUnavailable)
            },
        )
        val service = KtorExchangeRateUpdateService(
            ratesRepository = repository,
            client = client,
            endpointBaseUrl = "https://example.test/latest",
        )

        try {
            service.updateLatestRates(baseCurrency = "USD", targetCurrencies = listOf("EUR"))
            fail("Expected online update failure")
        } catch (error: Throwable) {
            assertTrue(error is OnlineExchangeRateUpdateException)
        }

        assertEquals(emptyList<SaveExchangeRateSnapshotInput>(), repository.savedSnapshots)
    }

    @Test
    fun updateLatestRatesDoesNotPersistWhenTargetRateIsMissing() = runBlocking {
        val repository = FakeCurrencyRatesRepository()
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "result": "success",
                          "time_last_update_unix": 1783036800,
                          "rates": {
                            "EUR": 0.93
                          }
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val service = KtorExchangeRateUpdateService(
            ratesRepository = repository,
            client = client,
            endpointBaseUrl = "https://example.test/latest",
        )

        try {
            service.updateLatestRates(baseCurrency = "USD", targetCurrencies = listOf("EUR", "TJS"))
            fail("Expected online response failure")
        } catch (error: Throwable) {
            assertTrue(error is OnlineExchangeRateResponseException)
        }

        assertEquals(0, repository.saveSnapshotsCallCount)
        assertEquals(emptyList<SaveExchangeRateSnapshotInput>(), repository.savedSnapshots)
    }

    @Test
    fun updateLatestRatesDoesNotPersistWhenTargetRateIsInvalid() = runBlocking {
        val repository = FakeCurrencyRatesRepository()
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "result": "success",
                          "time_last_update_unix": 1783036800,
                          "rates": {
                            "TJS": "bad",
                            "RUB": 78.5
                          }
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val service = KtorExchangeRateUpdateService(
            ratesRepository = repository,
            client = client,
            endpointBaseUrl = "https://example.test/latest",
        )

        try {
            service.updateLatestRates(baseCurrency = "USD", targetCurrencies = listOf("TJS", "RUB"))
            fail("Expected online response failure")
        } catch (error: Throwable) {
            assertTrue(error is OnlineExchangeRateResponseException)
        }

        assertEquals(0, repository.saveSnapshotsCallCount)
        assertEquals(emptyList<SaveExchangeRateSnapshotInput>(), repository.savedSnapshots)
    }

    @Test
    fun updateLatestRatesDoesNotPersistWhenTargetRateIsNonPositive() = runBlocking {
        val repository = FakeCurrencyRatesRepository()
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "result": "success",
                          "time_last_update_unix": 1783036800,
                          "rates": {
                            "EUR": 0,
                            "RUB": 78.5
                          }
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val service = KtorExchangeRateUpdateService(
            ratesRepository = repository,
            client = client,
            endpointBaseUrl = "https://example.test/latest",
        )

        try {
            service.updateLatestRates(baseCurrency = "USD", targetCurrencies = listOf("EUR", "RUB"))
            fail("Expected online response failure")
        } catch (error: Throwable) {
            assertTrue(error is OnlineExchangeRateResponseException)
        }

        assertEquals(0, repository.saveSnapshotsCallCount)
        assertEquals(emptyList<SaveExchangeRateSnapshotInput>(), repository.savedSnapshots)
    }

    private class FakeCurrencyRatesRepository : CurrencyRatesRepository {
        val savedSnapshots = mutableListOf<SaveExchangeRateSnapshotInput>()
        var saveSnapshotsCallCount = 0

        override suspend fun saveSnapshot(input: SaveExchangeRateSnapshotInput): ExchangeRateSnapshot {
            return saveSnapshots(listOf(input)).single()
        }

        override suspend fun saveSnapshots(
            inputs: List<SaveExchangeRateSnapshotInput>,
        ): List<ExchangeRateSnapshot> {
            saveSnapshotsCallCount += 1
            savedSnapshots += inputs
            return inputs.mapIndexed { index, input ->
                ExchangeRateSnapshot(
                    id = index + 1L,
                    snapshotDate = input.snapshotDate,
                    baseCurrency = input.baseCurrency,
                    targetCurrency = input.targetCurrency,
                    rateE8 = input.rateE8,
                    createdAtEpochMillis = 1L,
                )
            }
        }

        override suspend fun getSnapshot(
            baseCurrency: String,
            targetCurrency: String,
            snapshotDate: String,
        ): ExchangeRateSnapshot? = null

        override suspend fun getLatestSnapshotAtOrBefore(
            baseCurrency: String,
            targetCurrency: String,
            snapshotDate: String,
        ): ExchangeRateSnapshot? = null

        override suspend fun getLatestSnapshotDate(): String = "1970-01-01"

        override suspend fun listBaseCurrencies(profileId: Long): List<String> = emptyList()

        override suspend fun saveManualOverride(
            profileId: Long,
            input: SaveExchangeRateOverrideInput,
            overwriteExisting: Boolean,
        ): ExchangeRateOverride {
            throw NotImplementedError()
        }

        override suspend fun listManualOverrides(profileId: Long): List<ExchangeRateOverride> = emptyList()

        override suspend fun deleteManualOverride(profileId: Long, overrideId: Long) = Unit

        override suspend fun getRate(
            profileId: Long,
            baseCurrency: String,
            targetCurrency: String,
            effectiveDate: String,
        ): ResolvedExchangeRate {
            throw NotImplementedError()
        }
    }
}
