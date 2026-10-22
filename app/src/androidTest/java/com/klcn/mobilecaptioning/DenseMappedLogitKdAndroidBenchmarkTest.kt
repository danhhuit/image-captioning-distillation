package com.klcn.mobilecaptioning

import android.graphics.BitmapFactory
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DenseMappedLogitKdAndroidBenchmarkTest {
    @Test
    fun benchmarkAuthorizedDynamicInt8OnPhysicalDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val warmupRuns = arguments.getString("warmupRuns")?.toIntOrNull() ?: 5
        val measuredRuns = arguments.getString("measuredRuns")?.toIntOrNull() ?: 30
        require(warmupRuns in 1..20)
        require(measuredRuns in 10..100)
        requirePhysicalReleaseDevice(context.applicationInfo.flags)

        val imagePath = arguments.getString("benchmarkImagePath")
        val bitmap = imagePath
            ?.takeIf { File(it).isFile }
            ?.let { path -> BitmapFactory.decodeFile(path) }
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.vision_brand_full)
        requireNotNull(bitmap) { "Không đọc được ảnh benchmark" }

        val beforeMemory = processMemoryInfo()
        val modelLoadMs = Phase7KV2CaptionEngine.initialize(
            context = context,
            mode = CaptionModelMode.T1_S1,
        )

        var expectedTokenIds: List<Long>? = null
        repeat(warmupRuns) {
            val result = Phase7KV2CaptionEngine.caption(bitmap)
            if (expectedTokenIds == null) {
                expectedTokenIds = result.tokenIds
            } else {
                assertEquals(expectedTokenIds, result.tokenIds)
            }
        }

        val measured = buildList {
            repeat(measuredRuns) {
                val result = Phase7KV2CaptionEngine.caption(bitmap)
                assertEquals(expectedTokenIds, result.tokenIds)
                assertTrue(result.caption.isNotBlank())
                assertTrue(result.eosPosition >= 0)
                assertTrue(result.totalMs.isFinite() && result.totalMs > 0.0)
                add(result)
            }
        }
        val afterMemory = processMemoryInfo()
        val mode = requireNotNull(Phase7KV2CaptionEngine.selectedMode)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val apkBytes = File(context.applicationInfo.sourceDir).length()
        val powerManager = context.getSystemService(PowerManager::class.java)

        val payload = JSONObject()
            .put("schema_version", "dense_mapped_logit_kd_android_physical_benchmark_v1")
            .put("status", "complete")
            .put("model", JSONObject()
                .put("pair", mode.pairId)
                .put("student", mode.studentName)
                .put("precision", "INT8_DYNAMIC")
                .put("seed", 123)
                .put("encoder_asset", mode.encoderAsset)
                .put("decoder_asset", mode.decoderAsset)
                .put("encoder_sha256", mode.expectedEncoderSha256)
                .put("decoder_sha256", mode.expectedDecoderSha256)
                .put("package_bytes", mode.packageBytes)
                .put("package_reduction_fraction", mode.packageReductionFraction))
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("hardware", Build.HARDWARE)
                .put("android_release", Build.VERSION.RELEASE)
                .put("api_level", Build.VERSION.SDK_INT)
                .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put(
                    "thermal_status",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        powerManager?.currentThermalStatus
                    } else {
                        JSONObject.NULL
                    },
                ))
            .put("app", JSONObject()
                .put("package", context.packageName)
                .put("version_name", packageInfo.versionName)
                .put(
                    "version_code",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                )
                .put("build_type", "release")
                .put("apk_bytes", apkBytes))
            .put("protocol", JSONObject()
                .put("warmup_runs", warmupRuns)
                .put("measured_runs", measuredRuns)
                .put("beam_size", 3)
                .put("length_penalty", 0.7)
                .put("maximum_steps", 31)
                .put("image_source", imagePath ?: "drawable/vision_brand_full")
                .put("physical_android_required", true))
            .put("memory", JSONObject()
                .put("pss_before_kib", beforeMemory.totalPss)
                .put("pss_after_kib", afterMemory.totalPss)
                .put("pss_delta_kib", afterMemory.totalPss - beforeMemory.totalPss)
                .put("native_pss_after_kib", afterMemory.nativePss)
                .put("dalvik_pss_after_kib", afterMemory.dalvikPss))
            .put("latency_ms", JSONObject()
                .put("model_load", modelLoadMs)
                .put("preprocessing", distribution(measured.map { it.preprocessingMs }))
                .put("encoder", distribution(measured.map { it.encoderMs }))
                .put("beam3_decoder", distribution(measured.map { it.beamMs }))
                .put("end_to_end", distribution(measured.map { it.totalMs })))
            .put("repeatability", JSONObject()
                .put("token_ids_identical", true)
                .put("caption", measured.first().caption)
                .put("token_ids", JSONArray(measured.first().tokenIds)))
            .put("runs", JSONArray(measured.mapIndexed { index, result ->
                JSONObject()
                    .put("run", index + 1)
                    .put("preprocessing_ms", result.preprocessingMs)
                    .put("encoder_ms", result.encoderMs)
                    .put("beam3_decoder_ms", result.beamMs)
                    .put("end_to_end_ms", result.totalMs)
            }))
            .put("validation_quality_gate_passed", true)
            .put("held_out_test_reopened", false)

        val benchmarkDirectory = File(context.filesDir, "benchmarks").apply { mkdirs() }
        val resultFile = File(
            benchmarkDirectory,
            "KLCN_V2_XLKD05_Dense_Mapped_LogitKD_ANDROID_PHYSICAL_BENCHMARK.json",
        )
        resultFile.writeText(payload.toString(2), Charsets.UTF_8)
        File(benchmarkDirectory, "latest.json").writeText(payload.toString(2), Charsets.UTF_8)

        println("KLCN_ANDROID_BENCHMARK_RESULT=${payload}")
        println("KLCN_ANDROID_BENCHMARK_FILE=${resultFile.absolutePath}")

        assertEquals(measuredRuns, measured.size)
        assertTrue(payload.getJSONObject("latency_ms")
            .getJSONObject("end_to_end").getDouble("p95") > 0.0)

        bitmap.recycle()
        Phase7KV2CaptionEngine.release()
    }

    private fun processMemoryInfo(): Debug.MemoryInfo =
        Debug.MemoryInfo().also { memoryInfo -> Debug.getMemoryInfo(memoryInfo) }

    private fun requirePhysicalReleaseDevice(applicationFlags: Int) {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val isEmulator =
            fingerprint.startsWith("generic") ||
                fingerprint.contains("emulator") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk_gphone")
        require(!isEmulator) {
            "Benchmark khoa học phải chạy trên thiết bị Android vật lý, không dùng emulator."
        }
        require(applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "Benchmark khoa học phải chạy bằng APK release không-debuggable."
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
