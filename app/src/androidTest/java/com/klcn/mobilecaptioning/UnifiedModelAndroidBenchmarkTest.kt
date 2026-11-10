package com.klcn.mobilecaptioning

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
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

/**
 * One benchmark protocol for every on-device S1/S2/S3 package.
 *
 * Invoke this test once per modelId so that every run starts in a fresh
 * instrumentation process. Emulator measurements are engineering references
 * only; physical-device measurements remain the scientific deployment gate.
 */
@RunWith(AndroidJUnit4::class)
class UnifiedModelAndroidBenchmarkTest {
    @Test
    fun benchmarkSelectedStudentPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(arguments.getString("modelId")) {
            "modelId is required"
        }
        val warmupRuns = arguments.getString("warmupRuns")?.toIntOrNull() ?: 5
        val measuredRuns = arguments.getString("measuredRuns")?.toIntOrNull() ?: 30
        val allowEmulatorSmoke = arguments
            .getString("allowEmulatorSmoke")
            ?.toBooleanStrictOrNull() ?: false
        require(warmupRuns in 1..20)
        require(measuredRuns in 10..100)

        val mode = requireNotNull(
            CaptionModelMode.selectableModes.singleOrNull {
                it.name.equals(modelId, ignoreCase = true) ||
                    it.pairId.equals(modelId, ignoreCase = true)
            },
        ) {
            "Unknown or unavailable modelId: $modelId"
        }
        require(mode != CaptionModelMode.V1_FALLBACK) {
            "V1 uses a separate inference engine and is outside the S1/S2/S3 benchmark."
        }

        val emulator = isEmulator()
        requireAuthorizedRuntime(
            applicationFlags = context.applicationInfo.flags,
            emulator = emulator,
            allowEmulatorSmoke = allowEmulatorSmoke,
        )

        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.vision_brand_full,
        )
        requireNotNull(bitmap)
        val memoryBefore = processMemoryInfo()
        val thermalBefore = currentThermalStatus(context)
        val modelLoadMs = Phase7KV2CaptionEngine.initialize(context, mode)

        try {
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
                    assertTrue(result.totalMs.isFinite() && result.totalMs > 0.0)
                    add(result)
                }
            }
            val memoryAfter = processMemoryInfo()
            val thermalAfter = currentThermalStatus(context)
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0,
            )

            val payload = JSONObject()
                .put("schema_version", "unified_student_android_benchmark_v1")
                .put(
                    "status",
                    if (emulator) "complete_emulator_smoke" else "complete_physical_device",
                )
                .put(
                    "measurement_role",
                    if (emulator) {
                        "engineering_emulator_reference_not_scientific_benchmark"
                    } else {
                        "physical_device_scientific_benchmark"
                    },
                )
                .put(
                    "model",
                    JSONObject()
                        .put("model_id", mode.name)
                        .put("pair_id", mode.pairId)
                        .put("teacher", mode.teacherName)
                        .put("student", mode.studentName)
                        .put("dataset", mode.datasetName)
                        .put("kd_method", mode.kdMethodEn)
                        .put("runtime_precision", mode.runtimePrecision)
                        .put("student_parameters", mode.studentParameters)
                        .put("package_bytes", mode.packageBytes)
                        .put("encoder_asset", mode.encoderAsset)
                        .put("decoder_asset", mode.decoderAsset)
                        .put("encoder_sha256", mode.expectedEncoderSha256)
                        .put("decoder_sha256", mode.expectedDecoderSha256),
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
                        .put("apk_bytes", File(context.applicationInfo.sourceDir).length()),
                )
                .put(
                    "protocol",
                    JSONObject()
                        .put("warmup_runs", warmupRuns)
                        .put("measured_runs", measuredRuns)
                        .put("beam_size", 3)
                        .put("length_penalty", 0.7)
                        .put("maximum_generated_tokens", mode.maximumGeneratedTokens)
                        .put("image_source", "drawable/vision_brand_full")
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
                        .put("model_load", modelLoadMs)
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

            val fileName = "UNIFIED_BENCH_${mode.name}.json"
            val formatted = payload.toString(2)
            val internalDirectory = File(context.filesDir, "benchmarks").apply { mkdirs() }
            File(internalDirectory, fileName).writeText(formatted, Charsets.UTF_8)
            context.getExternalFilesDir("benchmarks")?.let { externalDirectory ->
                externalDirectory.mkdirs()
                File(externalDirectory, fileName).writeText(formatted, Charsets.UTF_8)
            }

            println("UNIFIED_ANDROID_BENCHMARK_RESULT=$payload")
            assertEquals(measuredRuns, measured.size)
            assertTrue(
                payload.getJSONObject("latency_ms")
                    .getJSONObject("end_to_end")
                    .getDouble("p95") > 0.0,
            )
        } finally {
            bitmap.recycle()
            Phase7KV2CaptionEngine.release()
        }
    }

    private fun processMemoryInfo(): Debug.MemoryInfo =
        Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

    private fun currentThermalStatus(context: Context): Any =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
                ?: JSONObject.NULL
        } else {
            JSONObject.NULL
        }

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

    private fun requireAuthorizedRuntime(
        applicationFlags: Int,
        emulator: Boolean,
        allowEmulatorSmoke: Boolean,
    ) {
        require(!emulator || allowEmulatorSmoke) {
            "Emulator requires allowEmulatorSmoke=true."
        }
        require(applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "Benchmark must use a non-debuggable release APK."
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
