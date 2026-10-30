package com.klcn.mobilecaptioning

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that selecting T1-S1 Sequence KD invokes its locked ONNX package. */
@RunWith(AndroidJUnit4::class)
class T1S1SequenceModelIntegrationTest {
    @After
    fun releaseRuntime() {
        Phase7KV2CaptionEngine.release()
    }

    @Test
    fun sequenceKdPackageRunsThroughProductionEngine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mode = CaptionModelMode.T1_S1_SEQUENCE
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(127, 127, 127))
        }

        try {
            val loadMs = Phase7KV2CaptionEngine.initialize(context, mode)
            val result = Phase7KV2CaptionEngine.caption(bitmap)

            assertTrue(loadMs > 0.0)
            assertEquals(mode, Phase7KV2CaptionEngine.selectedMode)
            assertTrue(result.caption.isNotBlank())
            assertTrue(result.eosPosition in 1..31)
            assertEquals(1L, result.tokenIds.first())
            assertEquals(2L, result.tokenIds.last())
            assertTrue(result.tokenIds.all { it in 0 until mode.vocabularySize.toLong() })
        } finally {
            bitmap.recycle()
        }
    }
}
