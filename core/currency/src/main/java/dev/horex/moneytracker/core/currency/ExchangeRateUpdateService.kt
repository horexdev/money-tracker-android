package dev.horex.moneytracker.core.currency

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

interface ExchangeRateUpdateService {
    suspend fun updateLatestRates(
        baseCurrency: String,
        targetCurrencies: List<String>,
    ): ExchangeRateUpdateResult
}

data class ExchangeRateUpdateResult(
    val snapshotDate: String,
    val baseCurrency: String,
    val savedCount: Int,
    val skippedCount: Int,
)

class KtorExchangeRateUpdateService(
    private val ratesRepository: CurrencyRatesRepository,
    private val client: HttpClient = defaultHttpClient(),
    private val endpointBaseUrl: String = DEFAULT_ENDPOINT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ExchangeRateUpdateService, AutoCloseable {
    override suspend fun updateLatestRates(
        baseCurrency: String,
        targetCurrencies: List<String>,
    ): ExchangeRateUpdateResult {
        val normalizedBase = baseCurrency.normalizedCurrencyCode()
        val normalizedTargets = targetCurrencies
            .map { it.normalizedCurrencyCode() }
            .filterNot { it == normalizedBase }
            .distinct()

        if (normalizedTargets.isEmpty()) {
            return ExchangeRateUpdateResult(
                snapshotDate = todayUtc(),
                baseCurrency = normalizedBase,
                savedCount = 0,
                skippedCount = targetCurrencies.size,
            )
        }

        val response = try {
            client.get("${endpointBaseUrl.trimEnd('/')}/${normalizedBase.encodeURLPathPart()}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw OnlineExchangeRateUpdateException(error)
        }
        if (!response.status.isSuccess()) {
            throw OnlineExchangeRateUpdateException()
        }

        val body = try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw OnlineExchangeRateUpdateException(error)
        }
        val payload = parsePayload(body)
        val snapshotDate = payload.snapshotDate ?: todayUtc()
        val rates = normalizedTargets.map { target ->
            val rateE8 = payload.rateFor(target)?.toRateE8()
                ?: throw OnlineExchangeRateResponseException()
            SaveExchangeRateSnapshotInput(
                snapshotDate = snapshotDate,
                baseCurrency = normalizedBase,
                targetCurrency = target,
                rateE8 = rateE8,
            )
        }

        try {
            ratesRepository.saveSnapshots(rates)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CurrencyException) {
            throw error
        } catch (error: Throwable) {
            throw OnlineExchangeRateUpdateException(error)
        }

        return ExchangeRateUpdateResult(
            snapshotDate = snapshotDate,
            baseCurrency = normalizedBase,
            savedCount = rates.size,
            skippedCount = normalizedTargets.size - rates.size,
        )
    }

    override fun close() {
        client.close()
    }

    private fun parsePayload(body: String): ExchangeRatePayload {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (error: RuntimeException) {
            throw OnlineExchangeRateResponseException()
        }

        val result = root.stringOrNull("result")
        if (result != null && result != "success") {
            throw OnlineExchangeRateResponseException()
        }

        val rates = root["rates"]?.jsonObjectOrNull()
            ?: throw OnlineExchangeRateResponseException()
        return ExchangeRatePayload(
            snapshotDate = root.longOrNull("time_last_update_unix")?.toUtcDate(),
            rates = rates,
        )
    }

    private fun String.normalizedCurrencyCode(): String {
        val normalized = trim().uppercase(Locale.US)
        if (normalized.length != CURRENCY_CODE_LENGTH || !normalized.all(Char::isLetter)) {
            throw InvalidCurrencyCodeException()
        }
        return normalized
    }

    private data class ExchangeRatePayload(
        val snapshotDate: String?,
        val rates: JsonObject,
    ) {
        fun rateFor(currencyCode: String): Double? {
            return rates[currencyCode]?.doubleOrNull()
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT_BASE_URL = "https://open.er-api.com/v6/latest"
        const val CURRENCY_CODE_LENGTH = 3
    }
}

private fun defaultHttpClient(): HttpClient {
    return HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 15_000L
        }
    }
}

private fun JsonObject.stringOrNull(name: String): String? {
    return this[name]?.jsonPrimitive?.content
}

private fun JsonObject.longOrNull(name: String): Long? {
    return this[name]?.jsonPrimitive?.content?.toLongOrNull()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun JsonElement.doubleOrNull(): Double? {
    return (this as? JsonPrimitive)?.doubleOrNull
}

private fun Double.toRateE8(): Long? {
    if (!isFinite() || this <= 0.0) {
        return null
    }
    return BigDecimal.valueOf(this)
        .multiply(BigDecimal.valueOf(RATE_SCALE_E8))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
        .takeIf { it > 0L }
}

private fun Long.toUtcDate(): String {
    return Instant.ofEpochSecond(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun todayUtc(): String {
    return Instant.now()
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}
