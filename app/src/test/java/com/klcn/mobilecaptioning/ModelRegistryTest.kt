package com.klcn.mobilecaptioning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {
    @Test
    fun mainAndExperimentalGroupsRemainDisjoint() {
        assertTrue(
            CaptionModelMode.mainModes
                .intersect(CaptionModelMode.experimentalModes.toSet())
                .isEmpty(),
        )
        assertTrue(CaptionModelMode.T1_S1_SEQUENCE in CaptionModelMode.mainModes)
        assertTrue(
            CaptionModelMode.T1_S1_HYBRID_COMING_SOON in CaptionModelMode.mainModes,
        )
        assertTrue(CaptionModelMode.F30K_T3_S3_EXPERIMENTAL in CaptionModelMode.experimentalModes)
        assertTrue(CaptionModelMode.V1_FALLBACK in CaptionModelMode.experimentalModes)
        assertEquals(2, CaptionModelMode.experimentalModes.size)
    }

    @Test
    fun hybridIsVisibleButCannotBeSelectedForInference() {
        val hybrid = CaptionModelMode.T1_S1_HYBRID_COMING_SOON

        assertTrue(hybrid.comingSoon)
        assertTrue(hybrid in CaptionModelMode.visibleModes)
        assertFalse(hybrid in CaptionModelMode.selectableModes)
    }

    @Test
    fun sequenceKdHasACompleteRuntimeContract() {
        val sequence = CaptionModelMode.T1_S1_SEQUENCE

        assertTrue(sequence.encoderAsset?.endsWith("encoder_int8.onnx") == true)
        assertTrue(sequence.decoderAsset?.endsWith("decoder_int8.onnx") == true)
        assertTrue(sequence.validationCiderD != null)
        assertTrue(sequence.packageBytes != null)
        assertFalse(sequence.experimental)
        assertTrue(sequence in CaptionModelMode.selectableModes)
    }
}
