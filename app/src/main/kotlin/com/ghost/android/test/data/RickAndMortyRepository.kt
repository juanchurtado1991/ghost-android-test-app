package com.ghost.android.test.data

import android.annotation.SuppressLint
import com.ghost.android.test.domain.CharacterResponse
import com.ghost.android.test.ui.model.EngineResult
import com.ghost.android.test.util.forceGC
import com.ghost.android.test.util.getCurrentThreadAllocatedBytes
import com.ghost.serialization.Ghost
import com.ghost.serialization.retrofit.GhostConverterFactory
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Sink
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import com.google.gson.stream.JsonReader as GsonStreamReader

class RickAndMortyRepository {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val moshiAdapter = moshi.adapter(CharacterResponse::class.java)
    private val kser = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val gson = Gson()

    init { Ghost.prewarm() }

    // ── Public API ──────────────────────────────────────────────────────────────

    @SuppressLint("CheckResult")
    suspend fun runBenchmark(
        pageCount: Int,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> = withContext(Dispatchers.IO) {
        val stressData = downloadStressData(pageCount, onStatusChange)

        warmUpEngines(stressData, onStatusChange)

        // Network benchmark uses the first page's raw bytes for local replay
        val networkBytes = stressData.bytes
        val networkResults = benchmarkNetworkStack(networkBytes, onStatusChange)

        val parseStringResults = benchmarkParseString(stressData, onStatusChange)
        val parseBytesResults = benchmarkParseBytes(stressData, onStatusChange)
        val parseStreamResults = benchmarkParseStreaming(stressData, onStatusChange)

        val writeStringResults = benchmarkWriteString(stressData, onStatusChange)
        val writeBytesResults = benchmarkWriteBytes(stressData, onStatusChange)
        val writeBufferResults = benchmarkWriteBuffer(stressData, onStatusChange)

        networkResults +
            parseStringResults + parseBytesResults + parseStreamResults +
            writeStringResults + writeBytesResults + writeBufferResults
    }

    // ── Data Download ───────────────────────────────────────────────────────────

    private suspend fun downloadStressData(
        pageCount: Int,
        onStatusChange: suspend (String) -> Unit
    ): StressData {
        val allBytes = mutableListOf<ByteArray>()

        onStatusChange("Downloading Stress Data ($pageCount pages)...")
        for (i in 1..pageCount) {
            val request = Request.Builder()
                .url("$API_BASE_URL?page=$i")
                .build()

            val responseBytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("API Error: ${response.code}")
                response.body.bytes()
            }
            allBytes.add(responseBytes)
        }

        val jsonString = mergePages(allBytes, pageCount)
        val responseBytes = jsonString.encodeToByteArray()
        return StressData(jsonString, responseBytes)
    }

    private fun mergePages(allBytes: List<ByteArray>, pageCount: Int): String {
        if (pageCount == 1) return allBytes[0].decodeToString()

        val firstPage = allBytes[0].decodeToString()
        val infoPart = firstPage.substringBefore("\"results\":")
        val resultsList = allBytes.joinToString(",") {
            val s = it.decodeToString()
            s.substringAfter("\"results\":")
                .substringBeforeLast("}")
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
        }
        return "$infoPart\"results\": [$resultsList]}"
    }

    // ── Network Stack Benchmarks (Retrofit / Ktor) ──────────────────────────────
    //
    // Uses FakeResponseInterceptor (OkHttp) and MockEngine (Ktor) to replay
    // pre-downloaded bytes locally. This eliminates HTTP 429 errors and
    // network variability — only the converter stack is measured.

    private suspend fun benchmarkNetworkStack(
        networkBytes: ByteArray,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        onStatusChange("Benchmarking NETWORK STACKS (converter only, local replay)...")

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor(FakeResponseInterceptor(networkBytes))
            .build()

        val kserFakeEngine = MockEngine { _ ->
            respond(
                content = networkBytes,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // Ghost Retrofit
        val ghostService = Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(fakeClient)
            .addConverterFactory(GhostConverterFactory.create())
            .build()
            .create(RickAndMortyGhostService::class.java)

        // Moshi Retrofit
        val moshiService = Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RickAndMortyService::class.java)

        // Gson Retrofit
        val gsonService = Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(fakeClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RickAndMortyGsonService::class.java)

        // KSer Ktorfit
        val kserHttpClient = HttpClient(kserFakeEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val kserService = Ktorfit.Builder()
            .baseUrl(API_BASE_URL)
            .httpClient(kserHttpClient)
            .build()
            .createRickAndMortyKtorfitService()

        // Ghost Ktorfit
        val ghostKtorHttpClient = HttpClient(kserFakeEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostKtor3Converter())
            }
        }
        val ghostKtorfitService = Ktorfit.Builder()
            .baseUrl(API_BASE_URL)
            .httpClient(ghostKtorHttpClient)
            .build()
            .createRickAndMortyKtorfitService()

        val ghostNet = measureSuspendEngine("[NETWORK] GHOST (Retrofit)", onStatusChange) {
            ghostService.getCharacters(1)
        }
        val moshiNet = measureSuspendEngine("[NETWORK] MOSHI (Retrofit)", onStatusChange) {
            moshiService.getCharacters(1)
        }
        val gsonNet = measureSuspendEngine("[NETWORK] GSON (Retrofit)", onStatusChange) {
            gsonService.getCharacters(1)
        }
        val kserNet = measureSuspendEngine("[NETWORK] KSER (Ktorfit)", onStatusChange) {
            kserService.getCharacters(1)
        }
        val ghostKtorNet = measureSuspendEngine("[NETWORK] GHOST (Ktorfit)", onStatusChange) {
            ghostKtorfitService.getCharacters(1)
        }

        kserHttpClient.close()
        ghostKtorHttpClient.close()
        return listOf(ghostNet, moshiNet, gsonNet, kserNet, ghostKtorNet)
    }

    /** Short-circuits OkHttp network calls, returning pre-canned bytes as HTTP 200. */
    private class FakeResponseInterceptor(private val body: ByteArray) : Interceptor {
        private val mediaType = "application/json; charset=UTF-8".toMediaType()
        override fun intercept(chain: Interceptor.Chain): Response {
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(mediaType))
                .build()
        }
    }

    // ── Warm-Up ─────────────────────────────────────────────────────────────────

    @SuppressLint("CheckResult")
    private suspend fun warmUpEngines(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ) {
        onStatusChange("JIT Warmup (${WARMUP_ITERATIONS}x)...")
        repeat(WARMUP_ITERATIONS) {
            // String mode
            Ghost.deserialize<CharacterResponse>(data.jsonString)
            moshiAdapter.fromJson(data.jsonString)
            kser.decodeFromString<CharacterResponse>(data.jsonString)
            gson.fromJson(data.jsonString, CharacterResponse::class.java)
            // Bytes mode
            Ghost.deserialize<CharacterResponse>(data.bytes)
            // Streaming mode
            Ghost.deserialize<CharacterResponse>(Buffer().write(data.bytes))
            moshiAdapter.fromJson(Buffer().write(data.bytes))
        }
    }

    // ── Deserialization: STRING Mode ────────────────────────────────────────────

    @SuppressLint("CheckResult")
    private suspend fun benchmarkParseString(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_STRING] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.jsonString)
        }
        val kserR = measureEngine("[PARSE_STRING] KSER", onStatusChange) {
            kser.decodeFromString<CharacterResponse>(data.jsonString)
        }
        val moshiR = measureEngine("[PARSE_STRING] MOSHI", onStatusChange) {
            moshiAdapter.fromJson(data.jsonString)
        }
        val gsonR = measureEngine("[PARSE_STRING] GSON", onStatusChange) {
            gson.fromJson(data.jsonString, CharacterResponse::class.java)
        }
        return listOf(ghost, kserR, moshiR, gsonR)
    }

    // ── Deserialization: BYTES Mode ─────────────────────────────────────────────

    @SuppressLint("CheckResult")
    private suspend fun benchmarkParseBytes(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_BYTES] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.bytes)
        }
        val kserR = measureEngine("[PARSE_BYTES] KSER", onStatusChange) {
            kser.decodeFromString<CharacterResponse>(String(data.bytes, Charsets.UTF_8))
        }
        val moshiR = measureEngine("[PARSE_BYTES] MOSHI", onStatusChange) {
            moshiAdapter.fromJson(String(data.bytes, Charsets.UTF_8))
        }
        val gsonR = measureEngine("[PARSE_BYTES] GSON", onStatusChange) {
            gson.fromJson(String(data.bytes, Charsets.UTF_8), CharacterResponse::class.java)
        }
        return listOf(ghost, kserR, moshiR, gsonR)
    }

    // ── Deserialization: STREAMING Mode ─────────────────────────────────────────

    @SuppressLint("CheckResult")
    private suspend fun benchmarkParseStreaming(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_STREAM] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(Buffer().write(data.bytes))
        }
        val kserR = measureEngine("[PARSE_STREAM] KSER", onStatusChange) {
            kser.decodeFromString<CharacterResponse>(String(data.bytes, Charsets.UTF_8))
        }
        val moshiR = measureEngine("[PARSE_STREAM] MOSHI", onStatusChange) {
            moshiAdapter.fromJson(Buffer().write(data.bytes))
        }
        val gsonR = measureEngine("[PARSE_STREAM] GSON", onStatusChange) {
            gson.fromJson(
                GsonStreamReader(InputStreamReader(ByteArrayInputStream(data.bytes))),
                CharacterResponse::class.java
            )
        }
        return listOf(ghost, kserR, moshiR, gsonR)
    }

    // ── Serialization: STRING Mode ──────────────────────────────────────────────

    @SuppressLint("CheckResult")
    private suspend fun benchmarkWriteString(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val preDecoded = Ghost.deserialize<CharacterResponse>(data.jsonString)

        val ghost = measureEngine("[WRITE_STRING] GHOST", onStatusChange) {
            Ghost.serialize(preDecoded)
        }
        val kserR = measureEngine("[WRITE_STRING] KSER", onStatusChange) {
            kser.encodeToString(CharacterResponse.serializer(), preDecoded)
        }
        val moshiR = measureEngine("[WRITE_STRING] MOSHI", onStatusChange) {
            moshiAdapter.toJson(preDecoded)
        }
        val gsonR = measureEngine("[WRITE_STRING] GSON", onStatusChange) {
            gson.toJson(preDecoded)
        }
        return listOf(ghost, kserR, moshiR, gsonR)
    }

    // ── Serialization: BYTES Mode ───────────────────────────────────────────────

    private suspend fun benchmarkWriteBytes(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val preDecoded = Ghost.deserialize<CharacterResponse>(data.jsonString)

        val ghost = measureEngine("[WRITE_BYTES] GHOST", onStatusChange) {
            Ghost.encodeToBytes(preDecoded)
        }
        val kserR = measureEngine("[WRITE_BYTES] KSER", onStatusChange) {
            kser.encodeToString(CharacterResponse.serializer(), preDecoded).toByteArray()
        }
        val moshiR = measureEngine("[WRITE_BYTES] MOSHI", onStatusChange) {
            moshiAdapter.toJson(preDecoded).toByteArray()
        }
        val gsonR = measureEngine("[WRITE_BYTES] GSON", onStatusChange) {
            gson.toJson(preDecoded).toByteArray()
        }
        return listOf(ghost, kserR, moshiR, gsonR)
    }

    // ── Serialization: BUFFER Mode ──────────────────────────────────────────────

    private suspend fun benchmarkWriteBuffer(
        data: StressData,
        onStatusChange: suspend (String) -> Unit
    ): List<EngineResult> {
        val preDecoded = Ghost.deserialize<CharacterResponse>(data.jsonString)

        val ghost = measureEngine("[WRITE_BUFFER] GHOST", onStatusChange) {
            val buffer = Buffer()
            Ghost.encodeToSink(buffer, preDecoded)
            buffer.clear()
        }
        val moshiR = measureEngine("[WRITE_BUFFER] MOSHI", onStatusChange) {
            val buffer = Buffer()
            val sink = (buffer as Sink).buffer()
            moshiAdapter.toJson(sink, preDecoded)
            sink.flush()
            buffer.clear()
        }
        val gsonR = measureEngine("[WRITE_BUFFER] GSON", onStatusChange) {
            val buffer = Buffer()
            buffer.writeUtf8(gson.toJson(preDecoded))
            buffer.clear()
        }
        return listOf(ghost, moshiR, gsonR)
    }

    // ── Measurement Utilities ───────────────────────────────────────────────────

    private suspend fun measureSuspendEngine(
        name: String,
        onStatus: suspend (String) -> Unit,
        block: suspend () -> Unit
    ): EngineResult {
        onStatus("Benchmarking $name...")
        var totalTimeNs = 0L
        var totalMem = 0L
        forceGC()
        repeat(BENCHMARK_ITERATIONS) {
            val startMem = getCurrentThreadAllocatedBytes()
            val start = System.nanoTime()
            block()
            val end = System.nanoTime()
            val endMem = getCurrentThreadAllocatedBytes()
            totalTimeNs += (end - start)
            totalMem += (endMem - startMem).coerceAtLeast(0)
        }
        return EngineResult(
            name,
            (totalTimeNs / BENCHMARK_ITERATIONS.toDouble()) / NANOS_PER_MILLI,
            totalMem / BENCHMARK_ITERATIONS
        )
    }

    private suspend fun measureEngine(
        name: String,
        onStatus: suspend (String) -> Unit,
        block: () -> Unit
    ): EngineResult {
        onStatus("Benchmarking $name...")
        var totalTimeNs = 0L
        var totalMem = 0L

        forceGC()

        repeat(BENCHMARK_ITERATIONS) {
            val startMem = getCurrentThreadAllocatedBytes()
            val start = System.nanoTime()
            block()
            val end = System.nanoTime()
            val endMem = getCurrentThreadAllocatedBytes()
            totalTimeNs += (end - start)
            totalMem += (endMem - startMem).coerceAtLeast(0)
        }

        return EngineResult(
            name,
            (totalTimeNs / BENCHMARK_ITERATIONS.toDouble()) / NANOS_PER_MILLI,
            totalMem / BENCHMARK_ITERATIONS
        )
    }

    // ── Internal Data Holder ────────────────────────────────────────────────────

    @Suppress("ArrayInDataClass")
    private data class StressData(
        val jsonString: String,
        val bytes: ByteArray
    )

    companion object {
        private const val API_BASE_URL = "https://rickandmortyapi.com/api/character/"
        private const val WARMUP_ITERATIONS = 2000
        private const val BENCHMARK_ITERATIONS = 500
        private const val NANOS_PER_MILLI = 1_000_000.0
    }
}
