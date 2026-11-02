package com.klcn.mobilecaptioning

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke test for the five packages authorized by
 * KLCN_V2_MOB_Expansion_Final_Audit.
 *
 * Sessions are released after every model because the expansion APK contains
 * several independent ONNX packages and low-memory Android devices must never
 * keep all of them resident at the same time.
 */
@RunWith(AndroidJUnit4::class)
class MobileExpansionModelIntegrationTest {
    private val expansionModes = listOf(
        CaptionModelMode.MSCOCO_CLIP_S1_LOGIT,
        CaptionModelMode.F30K_T1_S1_SEQUENCE,
        CaptionModelMode.F30K_T2_S2_SEQUENCE,
        CaptionModelMode.F30K_T1_S1_DENSE_MAP,
        CaptionModelMode.F30K_T1_S1_HYBRID,
    )

    @Test
    fun allAuthorizedExpansionPackagesRunThroughProductionEngine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = createDeterministicInput()

        try {
            expansionModes.forEach { mode ->
                try {
                    assertFalse("${mode.name} must be selectable", mode.comingSoon)
                    assertNotNull("${mode.name} encoder asset", mode.encoderAsset)
                    assertNotNull("${mode.name} decoder asset", mode.decoderAsset)

                    val loadMs = Phase7KV2CaptionEngine.initialize(context, mode)
                    val result = Phase7KV2CaptionEngine.caption(bitmap)

                    assertTrue("${mode.name} load time", loadMs > 0.0)
                    assertEquals(mode, Phase7KV2CaptionEngine.selectedMode)
                    assertTrue("${mode.name} caption", result.caption.isNotBlank())
                    assertTrue(
                        "${mode.name} EOS position",
                        result.eosPosition in 1..mode.maximumGeneratedTokens,
                    )
                    assertEquals("${mode.name} BOS", 1L, result.tokenIds.first())
                    assertEquals("${mode.name} EOS", 2L, result.tokenIds.last())
                    assertTrue(
                        "${mode.name} vocabulary bounds",
                        result.tokenIds.all { it in 0 until mode.vocabularySize.toLong() },
                    )
                    assertTrue("${mode.name} encoder latency", result.encoderMs >= 0.0)
                    assertTrue("${mode.name} decoder latency", result.beamMs >= 0.0)
                } finally {
                    Phase7KV2CaptionEngine.release()
                }
            }
        } finally {
            bitmap.recycle()
            Phase7KV2CaptionEngine.release()
        }
    }

    private fun createDeterministicInput(): Bitmap =
        Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val red = (x * 255) / (width - 1)
                    val green = (y * 255) / (height - 1)
                    val blue = if (((x / 28) + (y / 28)) % 2 == 0) 64 else 192
                    setPixel(x, y, Color.rgb(red, green, blue))
                }
            }
        }
}
