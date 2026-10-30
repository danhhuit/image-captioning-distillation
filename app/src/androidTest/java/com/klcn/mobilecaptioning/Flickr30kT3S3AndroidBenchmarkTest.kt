package com.klcn.mobilecaptioning

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device benchmark for the locked Flickr30k T3->S3 package.
 *
 * The model files are test-only assets staged by
 * scripts/run_f30k_t3s3_android_benchmark.ps1. They are deliberately not
 * added to the production model registry until this benchmark is reviewed.
 */
@RunWith(AndroidJUnit4::class)
class Flickr30kT3S3AndroidBenchmarkTest {
    @Test
    fun benchmarkConditionallyAuthorizedPackageOnPhysicalDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val arguments = InstrumentationRegistry.getArguments()
        val warmupRuns = arguments.getString("warmupRuns")?.toIntOrNull() ?: 5
        val measuredRuns = arguments.getString("measuredRuns")?.toIntOrNull() ?: 30
        val allowEmulatorSmoke = arguments
            .getString("allowEmulatorSmoke")
            ?.toBooleanStrictOrNull() ?: false
        require(warmupRuns in 1..20)
        require(measuredRuns in 10..100)
        val emulator = isEmulator()
        requireAuthorizedDevice(
            applicationFlags = targetContext.applicationInfo.flags,
            emulator = emulator,
            allowEmulatorSmoke = allowEmulatorSmoke,
        )

        val bitmap = loadBenchmarkBitmap(testContext, targetContext)
        val powerManager = targetContext.getSystemService(PowerManager::class.java)
        val thermalBefore = currentThermalStatus(powerManager)
        val memoryBefore = processMemoryInfo()
        val engine = Flickr30kT3S3BenchmarkEngine(testContext)

        try {
            var expectedTokenIds: List<Long>? = null
            repeat(warmupRuns) {
                val result = engine.caption(bitmap)
                if (expectedTokenIds == null) {
                    expectedTokenIds = result.tokenIds
                } else {
                    assertEquals(expectedTokenIds, result.tokenIds)
                }
            }

            val measured = buildList {
                repeat(measuredRuns) {
                    val result = engine.caption(bitmap)
                    assertEquals(expectedTokenIds, result.tokenIds)
                    assertTrue(result.tokenIds.first() == 1L)
                    assertTrue(result.tokenIds.last() == 2L)
                    assertTrue(result.totalMs.isFinite() && result.totalMs > 0.0)
                    add(result)
                }
            }
            val memoryAfter = processMemoryInfo()
            val thermalAfter = currentThermalStatus(powerManager)
            val packageInfo = targetContext.packageManager.getPackageInfo(
                targetContext.packageName,
                0,
            )
            val apkBytes = File(targetContext.applicationInfo.sourceDir).length()

            val payload = JSONObject()
                .put("schema_version", "klcn_v2_f30k_t3s3_android_benchmark_v1")
                .put(
                    "status",
                    if (emulator) "complete_emulator_smoke" else "complete_physical_device",
                )
                .put("experiment_id", "KLCN_V2_F30K_T3S3_Android_Benchmark")
                .put(
                    "measurement_role",
                    if (emulator) {
                        "functional_emulator_smoke_not_scientific_benchmark"
                    } else {
                        "physical_device_scientific_benchmark"
                    },
                )
                .put(
                    "model",
                    JSONObject()
                        .put("pair", "T3 SigLIP ViT-B/16 adapted -> S3 TinyViT-5M")
                        .put("method", "Direct Logit-KD")
                        .put("seed", 42)
                        .put("encoder_precision", "FP32")
                        .put("decoder_precision", "dynamic per-channel QInt8 weights")
                        .put("encoder_sha256", Flickr30kT3S3BenchmarkEngine.ENCODER_SHA256)
                        .put("decoder_sha256", Flickr30kT3S3BenchmarkEngine.DECODER_SHA256)
                        .put("vocabulary_sha256", Flickr30kT3S3BenchmarkEngine.VOCABULARY_SHA256)
                        .put("model_only_bytes", engine.modelOnlyBytes),
                )
                .put(
                    "scientific_boundary",
                    JSONObject()
                        .put("validation_quality_gate_passed", true)
                        .put("strict_verbatim_stability_passed", false)
                        .put("noninferiority_passed", true)
                        .put("noninferiority_boundary_sensitive", true)
                        .put("held_out_test_reopened", false)
                        .put("unconditional_release_authorized", false),
                )
                .put(
                    "device",
                    JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("brand", Build.BRAND)
                        .put("model", Build.MODEL)
                        .put("device", Build.DEVICE)
                        .put("hardware", Build.HARDWARE)
                        .put("android_release", Build.VERSION.RELEASE)
                        .put("api_level", Build.VERSION.SDK_INT)
                        .put("is_emulator", emulator)
                        .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                        .put("thermal_status_before", thermalBefore)
                        .put("thermal_status_after", thermalAfter),
                )
                .put(
                    "app",
                    JSONObject()
                        .put("package", targetContext.packageName)
                        .put("version_name", packageInfo.versionName)
                        .put("version_code", packageVersionCode(packageInfo))
                        .put("build_type", "release")
                        .put("apk_bytes", apkBytes),
                )
                .put(
                    "protocol",
                    JSONObject()
                        .put("warmup_runs", warmupRuns)
                        .put("measured_runs", measuredRuns)
                        .put("beam_size", 3)
                        .put("length_penalty", 0.7)
                        .put("maximum_generated_tokens", 30)
                        .put("image_source", engine.imageSource)
                        .put("physical_android_required", !emulator)
                        .put("emulator_smoke_explicitly_authorized", allowEmulatorSmoke),
                )
                .put(
                    "memory",
                    JSONObject()
                        .put("pss_before_kib", memoryBefore.totalPss)
                        .put("pss_after_kib", memoryAfter.totalPss)
                        .put("pss_delta_kib", memoryAfter.totalPss - memoryBefore.totalPss)
                        .put("native_pss_after_kib", memoryAfter.nativePss)
                        .put("dalvik_pss_after_kib", memoryAfter.dalvikPss),
                )
                .put(
                    "latency_ms",
                    JSONObject()
                        .put("model_load", engine.modelLoadMs)
                        .put("preprocessing", distribution(measured.map { it.preprocessingMs }))
                        .put("encoder", distribution(measured.map { it.encoderMs }))
                        .put("beam3_decoder", distribution(measured.map { it.beamMs }))
                        .put("end_to_end", distribution(measured.map { it.totalMs })),
                )
                .put(
                    "repeatability",
                    JSONObject()
                        .put("token_ids_identical", true)
                        .put("caption", measured.first().caption)
                        .put("token_ids", JSONArray(measured.first().tokenIds)),
                )
                .put(
                    "runs",
                    JSONArray(
                        measured.mapIndexed { index, result ->
                            JSONObject()
                                .put("run", index + 1)
                                .put("preprocessing_ms", result.preprocessingMs)
                                .put("encoder_ms", result.encoderMs)
                                .put("beam3_decoder_ms", result.beamMs)
                                .put("end_to_end_ms", result.totalMs)
                        },
                    ),
                )

            val internalDirectory = File(targetContext.filesDir, "benchmarks").apply { mkdirs() }
            val externalDirectory = targetContext
                .getExternalFilesDir("benchmarks")
                ?.apply { mkdirs() }
            val fileName = if (emulator) {
                "KLCN_V2_F30K_T3S3_ANDROID_EMULATOR_SMOKE.json"
            } else {
                "KLCN_V2_F30K_T3S3_ANDROID_PHYSICAL_BENCHMARK.json"
            }
            val formatted = payload.toString(2)
            val internalFile = File(internalDirectory, fileName).apply {
                writeText(formatted, Charsets.UTF_8)
            }
            val externalFile = externalDirectory?.let { directory ->
                File(directory, fileName).apply { writeText(formatted, Charsets.UTF_8) }
            }

            println("KLCN_F30K_ANDROID_BENCHMARK_RESULT=$payload")
            println("KLCN_F30K_ANDROID_BENCHMARK_INTERNAL=${internalFile.absolutePath}")
            println("KLCN_F30K_ANDROID_BENCHMARK_EXTERNAL=${externalFile?.absolutePath}")

            assertEquals(measuredRuns, measured.size)
            assertTrue(
                payload.getJSONObject("latency_ms")
                    .getJSONObject("end_to_end")
                    .getDouble("p95") > 0.0,
            )
        } finally {
            bitmap.recycle()
            engine.close()
        }
    }

    private fun loadBenchmarkBitmap(testContext: Context, targetContext: Context): Bitmap {
        val staged = runCatching {
            testContext.assets.open("f30k_t3s3_benchmark/benchmark_image.jpg").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
        return staged ?: BitmapFactory.decodeResource(
            targetContext.resources,
            R.drawable.vision_brand_full,
        )
    }

    private fun currentThermalStatus(powerManager: PowerManager?): Any =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: JSONObject.NULL
        } else {
            JSONObject.NULL
        }

    private fun packageVersionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

    private fun processMemoryInfo(): Debug.MemoryInfo =
        Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.startsWith("generic") ||
                fingerprint.contains("emulator") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk_gphone")
    }

    private fun requireAuthorizedDevice(
        applicationFlags: Int,
        emulator: Boolean,
        allowEmulatorSmoke: Boolean,
    ) {
        require(!emulator || allowEmulatorSmoke) {
            "Emulator chi duoc dung khi bat allowEmulatorSmoke=true."
        }
        require(applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "Benchmark khoa hoc phai chay bang APK release khong-debuggable."
        }
    }

    private fun distribution(values: List<Double>): JSONObject {
        require(values.isNotEmpty() && values.all(Double::isFinite))
        val sorted = values.sorted()
        return JSONObject()
            .put("n", sorted.size)
            .put("min", sorted.first())
            .put("mean", sorted.average())
            .put("p50", percentile(sorted, 0.50))
            .put("p90", percentile(sorted, 0.90))
            .put("p95", percentile(sorted, 0.95))
            .put("max", sorted.last())
    }

    private fun percentile(sorted: List<Double>, quantile: Double): Double {
        val position = (sorted.size - 1) * quantile
        val lower = floor(position).toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val weight = position - lower
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight
    }
}

private data class FlickrBenchmarkInferenceResult(
    val caption: String,
    val tokenIds: List<Long>,
    val preprocessingMs: Double,
    val encoderMs: Double,
    val beamMs: Double,
    val totalMs: Double,
)

private class Flickr30kT3S3BenchmarkEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val ENCODER_SHA256 =
            "1ff7e46d053142bd1cccf0e02e9ffe0c6e38495274e4dc60aa5f059da03ae984"
        const val DECODER_SHA256 =
            "ece2a7b788529e515467f28188b6e6eca0c680fb11c04104b683c21973ea2973"
        const val VOCABULARY_SHA256 =
            "c94727d24d24130126ae7c88d9537714b88ff3dde12ac741f9cd4a0757287a83"

        private const val assetRoot = "f30k_t3s3_benchmark"
        private const val encoderName = "f30k_t3s3_seed42_encoder_fp32.onnx"
        private const val decoderName = "f30k_t3s3_seed42_decoder_dynamic_int8.onnx"
        private const val vocabularyName = "f30k_vocabulary_minfreq5.json"
        private const val vocabularySize = 7_456
        private const val sequenceLength = 31
        private const val memoryTokens = 49
        private const val hiddenSize = 256
        private const val beamSize = 3
        private const val bosId = 1L
        private const val eosId = 2L
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val encoderSession: OrtSession
    private val decoderSession: OrtSession
    private val tokens: List<String>
    val modelLoadMs: Double
    val modelOnlyBytes: Long
    val imageSource: String = if (assetExists("$assetRoot/benchmark_image.jpg")) {
        "androidTest asset benchmark_image.jpg"
    } else {
        "drawable/vision_brand_full"
    }

    init {
        val started = SystemClock.elapsedRealtimeNanos()
        val encoderBytes = readVerifiedAsset(encoderName, ENCODER_SHA256)
        val decoderBytes = readVerifiedAsset(decoderName, DECODER_SHA256)
        val vocabularyBytes = readVerifiedAsset(vocabularyName, VOCABULARY_SHA256)
        modelOnlyBytes = encoderBytes.size.toLong() + decoderBytes.size.toLong()

        val vocabulary = JSONObject(vocabularyBytes.toString(Charsets.UTF_8))
        val array = vocabulary.getJSONArray("tokens")
        check(array.length() == vocabularySize)
        tokens = List(array.length()) { array.getString(it) }
        check(tokens.take(4) == listOf("<pad>", "<bos>", "<eos>", "<unk>"))

        val options = OrtSession.SessionOptions()
        encoderSession = environment.createSession(encoderBytes, options)
        decoderSession = environment.createSession(decoderBytes, options)
        options.close()
        check(encoderSession.inputNames == setOf("images"))
        check(encoderSession.outputNames == setOf("memory"))
        check(decoderSession.inputNames == setOf("token_ids", "memory", "attention_mask"))
        check(decoderSession.outputNames == setOf("logits"))
        modelLoadMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
    }

    fun caption(bitmap: Bitmap): FlickrBenchmarkInferenceResult {
        val totalStarted = SystemClock.elapsedRealtimeNanos()
        val preprocessStarted = SystemClock.elapsedRealtimeNanos()
        val image = preprocess(bitmap)
        val preprocessingMs = elapsedMs(preprocessStarted)

        val encoderStarted = SystemClock.elapsedRealtimeNanos()
        val memory = runEncoder(image)
        val encoderMs = elapsedMs(encoderStarted)

        val decoderStarted = SystemClock.elapsedRealtimeNanos()
        val tokenIds = beamDecode(memory)
        val beamMs = elapsedMs(decoderStarted)
        val caption = decodeCaption(tokenIds)
        return FlickrBenchmarkInferenceResult(
            caption = caption,
            tokenIds = tokenIds,
            preprocessingMs = preprocessingMs,
            encoderMs = encoderMs,
            beamMs = beamMs,
            totalMs = elapsedMs(totalStarted),
        )
    }

    override fun close() {
        decoderSession.close()
        encoderSession.close()
    }

    private data class Beam(
        val tokenIds: List<Long>,
        val score: Double,
        val finished: Boolean,
    ) {
        val normalizedScore: Double
            get() = score / max(1, tokenIds.size - 1).toDouble().pow(0.7)
    }

    private fun beamDecode(memory: FloatArray): List<Long> {
        var beams = listOf(Beam(listOf(bosId), 0.0, false))
        repeat(30) {
            val candidates = beams.filter(Beam::finished).toMutableList()
            for (beam in beams.filterNot(Beam::finished)) {
                val inputIds = LongArray(sequenceLength)
                val attention = BooleanArray(sequenceLength)
                beam.tokenIds.forEachIndexed { index, tokenId ->
                    inputIds[index] = tokenId
                    attention[index] = true
                }
                val logits = runDecoder(inputIds, memory, attention)
                val position = beam.tokenIds.size - 1
                val offset = position * vocabularySize
                val maximum = (0 until vocabularySize)
                    .filterNot { it == 0 || it == 1 }
                    .maxOf { logits[offset + it] }
                var exponentialSum = 0.0
                for (tokenId in 0 until vocabularySize) {
                    if (tokenId == 0 || tokenId == 1) continue
                    exponentialSum += exp((logits[offset + tokenId] - maximum).toDouble())
                }
                val logNormalizer = maximum.toDouble() + ln(exponentialSum)
                val top = topThreeTokens(logits, offset, logNormalizer)
                for ((tokenId, logProbability) in top) {
                    val next = tokenId.toLong()
                    candidates += Beam(
                        tokenIds = beam.tokenIds + next,
                        score = beam.score + logProbability,
                        finished = next == eosId,
                    )
                }
            }
            beams = candidates.sortedWith(beamComparator).take(beamSize)
            if (beams.all(Beam::finished)) return@repeat
        }
        val best = beams.sortedWith(beamComparator).first()
        val eos = best.tokenIds.indexOf(eosId)
        check(eos >= 0) { "Beam-3 did not emit EOS within 30 generated tokens" }
        return best.tokenIds.take(eos + 1)
    }

    private val beamComparator = Comparator<Beam> { left, right ->
        val score = right.normalizedScore.compareTo(left.normalizedScore)
        if (score != 0) score else compareTokenLists(left.tokenIds, right.tokenIds)
    }

    private fun compareTokenLists(left: List<Long>, right: List<Long>): Int {
        for (index in 0 until min(left.size, right.size)) {
            val comparison = left[index].compareTo(right[index])
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size)
    }

    private fun topThreeTokens(
        logits: FloatArray,
        offset: Int,
        logNormalizer: Double,
    ): List<Pair<Int, Double>> {
        val ids = IntArray(beamSize) { -1 }
        val scores = DoubleArray(beamSize) { Double.NEGATIVE_INFINITY }
        for (tokenId in 0 until vocabularySize) {
            if (tokenId == 0 || tokenId == 1) continue
            val score = logits[offset + tokenId].toDouble() - logNormalizer
            for (rank in 0 until beamSize) {
                val better = score > scores[rank] ||
                    (score == scores[rank] && (ids[rank] < 0 || tokenId < ids[rank]))
                if (!better) continue
                for (shift in beamSize - 1 downTo rank + 1) {
                    scores[shift] = scores[shift - 1]
                    ids[shift] = ids[shift - 1]
                }
                scores[rank] = score
                ids[rank] = tokenId
                break
            }
        }
        return ids.indices.map { ids[it] to scores[it] }
    }

    private fun runEncoder(input: FloatArray): FloatArray {
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, 224, 224),
        ).use { tensor ->
            encoderSession.run(mapOf("images" to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                return output.floatBuffer.toFloatArray()
            }
        }
    }

    private fun runDecoder(
        inputIds: LongArray,
        memory: FloatArray,
        attentionMask: BooleanArray,
    ): FloatArray {
        val boolBuffer = ByteBuffer.allocateDirect(sequenceLength)
            .order(ByteOrder.nativeOrder())
        attentionMask.forEach { boolBuffer.put(if (it) 1.toByte() else 0.toByte()) }
        boolBuffer.flip()
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(inputIds),
            longArrayOf(1, sequenceLength.toLong()),
        ).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(memory),
                longArrayOf(1, memoryTokens.toLong(), hiddenSize.toLong()),
            ).use { memoryTensor ->
                OnnxTensor.createTensor(
                    environment,
                    boolBuffer,
                    longArrayOf(1, sequenceLength.toLong()),
                    OnnxJavaType.BOOL,
                ).use { maskTensor ->
                    decoderSession.run(
                        mapOf(
                            "token_ids" to idsTensor,
                            "memory" to memoryTensor,
                            "attention_mask" to maskTensor,
                        ),
                    ).use { result ->
                        val output = result.get(0) as OnnxTensor
                        val values = output.floatBuffer.toFloatArray()
                        check(values.size == sequenceLength * vocabularySize)
                        return values
                    }
                }
            }
        }
    }

    private fun decodeCaption(tokenIds: List<Long>): String {
        val words = tokenIds
            .takeWhile { it != eosId }
            .filter { it !in setOf(0L, 1L, 2L, 3L) }
            .map { tokens[it.toInt()] }
        var text = words.joinToString(" ")
        for (punctuation in listOf(".", ",", "!", "?", ";", ":")) {
            text = text.replace(" $punctuation", punctuation)
        }
        return text.trim()
    }

    private fun readVerifiedAsset(name: String, expectedSha256: String): ByteArray {
        val bytes = context.assets.open("$assetRoot/$name").use { it.readBytes() }
        check(sha256(bytes) == expectedSha256) { "$name SHA-256 mismatch" }
        return bytes
    }

    private fun assetExists(path: String): Boolean = runCatching {
        context.assets.open(path).use { }
        true
    }.getOrDefault(false)

    private fun preprocess(source: Bitmap): FloatArray {
        val resizeShorter = 256
        val targetSize = 224
        val scale = resizeShorter.toDouble() / min(source.width, source.height)
        val resizedWidth = max(resizeShorter, (source.width * scale).roundToInt())
        val resizedHeight = max(resizeShorter, (source.height * scale).roundToInt())
        val resized = resizeBicubic(source, resizedWidth, resizedHeight)
        val cropLeft = (resizedWidth - targetSize) / 2
        val cropTop = (resizedHeight - targetSize) / 2
        val pixels = IntArray(targetSize * targetSize)
        resized.getPixels(pixels, 0, targetSize, cropLeft, cropTop, targetSize, targetSize)
        resized.recycle()
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = targetSize * targetSize
        val output = FloatArray(3 * plane)
        pixels.forEachIndexed { index, pixel ->
            output[index] = (Color.red(pixel) / 255f - mean[0]) / std[0]
            output[plane + index] = (Color.green(pixel) / 255f - mean[1]) / std[1]
            output[2 * plane + index] = (Color.blue(pixel) / 255f - mean[2]) / std[2]
        }
        return output
    }

    private fun resizeBicubic(source: Bitmap, width: Int, height: Int): Bitmap {
        val sourcePixels = IntArray(source.width * source.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        val outputPixels = IntArray(width * height)
        val xScale = source.width.toDouble() / width
        val yScale = source.height.toDouble() / height
        for (targetY in 0 until height) {
            val sourceY = (targetY + 0.5) * yScale - 0.5
            val baseY = floor(sourceY).toInt()
            for (targetX in 0 until width) {
                val sourceX = (targetX + 0.5) * xScale - 0.5
                val baseX = floor(sourceX).toInt()
                var red = 0.0
                var green = 0.0
                var blue = 0.0
                var totalWeight = 0.0
                for (offsetY in -1..2) {
                    val sampledY = (baseY + offsetY).coerceIn(0, source.height - 1)
                    val weightY = cubicWeight(sourceY - (baseY + offsetY))
                    for (offsetX in -1..2) {
                        val sampledX = (baseX + offsetX).coerceIn(0, source.width - 1)
                        val weight = weightY * cubicWeight(sourceX - (baseX + offsetX))
                        val pixel = sourcePixels[sampledY * source.width + sampledX]
                        red += Color.red(pixel) * weight
                        green += Color.green(pixel) * weight
                        blue += Color.blue(pixel) * weight
                        totalWeight += weight
                    }
                }
                outputPixels[targetY * width + targetX] = Color.rgb(
                    (red / totalWeight).roundToInt().coerceIn(0, 255),
                    (green / totalWeight).roundToInt().coerceIn(0, 255),
                    (blue / totalWeight).roundToInt().coerceIn(0, 255),
                )
            }
        }
        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun cubicWeight(distance: Double): Double {
        val value = abs(distance)
        return when {
            value <= 1.0 -> 1.5 * value.pow(3) - 2.5 * value.pow(2) + 1.0
            value < 2.0 -> -0.5 * value.pow(3) + 2.5 * value.pow(2) - 4.0 * value + 2.0
            else -> 0.0
        }
    }

    private fun FloatBuffer.toFloatArray(): FloatArray =
        FloatArray(remaining()).also { get(it) }

    private fun elapsedMs(started: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
