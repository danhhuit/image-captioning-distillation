package com.klcn.mobilecaptioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            CaptionModelMode.T1_S1_HYBRID in CaptionModelMode.mainModes,
        )
        assertTrue(CaptionModelMode.F30K_T3_S3_EXPERIMENTAL in CaptionModelMode.experimentalModes)
        assertTrue(CaptionModelMode.V1_FALLBACK in CaptionModelMode.experimentalModes)
        assertEquals(7, CaptionModelMode.experimentalModes.size)
    }

    @Test
    fun mscocoHybridIsIntegratedAndSelectable() {
        val hybrid = CaptionModelMode.T1_S1_HYBRID

        assertFalse(hybrid.comingSoon)
        assertTrue(hybrid in CaptionModelMode.visibleModes)
        assertTrue(hybrid in CaptionModelMode.selectableModes)
        assertTrue(hybrid.encoderAsset?.endsWith("encoder_int8.onnx") == true)
        assertTrue(hybrid.decoderAsset?.endsWith("decoder_int8.onnx") == true)
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

    @Test
    fun modelGroupsFollowTheRequestedDisplayOrder() {
        assertEquals(
            listOf(
                CaptionModelMode.T1_S1_SEQUENCE,
                CaptionModelMode.T1_S1,
                CaptionModelMode.T1_S1_HYBRID,
                CaptionModelMode.T2_S2,
                CaptionModelMode.T3_S3,
            ),
            CaptionModelMode.mainModes,
        )
        assertEquals(
            listOf(
                CaptionModelMode.V1_FALLBACK,
                CaptionModelMode.MSCOCO_CLIP_S1_LOGIT,
                CaptionModelMode.F30K_T1_S1_SEQUENCE,
                CaptionModelMode.F30K_T1_S1_DENSE_MAP,
                CaptionModelMode.F30K_T1_S1_HYBRID,
                CaptionModelMode.F30K_T2_S2_SEQUENCE,
                CaptionModelMode.F30K_T3_S3_EXPERIMENTAL,
            ),
            CaptionModelMode.experimentalModes,
        )
    }

    @Test
    fun newExperimentalEntriesExposeDatasetMethodAndCiderComparison() {
        CaptionModelMode.experimentalModes
            .filterNot { it == CaptionModelMode.V1_FALLBACK }
            .forEach { mode ->
                assertTrue(mode.datasetName.isNotBlank())
                assertTrue(mode.kdMethodVi.isNotBlank())
                assertTrue(mode.baselineCiderD != null)
                assertTrue(mode.validationCiderD != null)
            }
    }

    @Test
    fun mobileExpansionModelsAreCompleteAndSelectable() {
        val integratedModes = listOf(
            CaptionModelMode.MSCOCO_CLIP_S1_LOGIT,
            CaptionModelMode.F30K_T1_S1_SEQUENCE,
            CaptionModelMode.F30K_T2_S2_SEQUENCE,
            CaptionModelMode.F30K_T1_S1_DENSE_MAP,
            CaptionModelMode.F30K_T1_S1_HYBRID,
        )

        integratedModes.forEach { mode ->
            assertFalse("${mode.pairId} must not remain Coming soon", mode.comingSoon)
            assertTrue(mode in CaptionModelMode.experimentalModes)
            assertTrue(mode in CaptionModelMode.selectableModes)
            assertTrue(mode.encoderAsset?.startsWith("v2/mobile_expansion/") == true)
            assertTrue(mode.decoderAsset?.startsWith("v2/mobile_expansion/") == true)
            assertTrue(mode.vocabularyAsset.startsWith("v2/mobile_expansion/"))
            assertEquals(64, mode.expectedEncoderSha256?.length)
            assertEquals(64, mode.expectedDecoderSha256?.length)
            assertTrue((mode.packageBytes ?: 0L) > 0L)
            assertTrue((mode.packageReductionFraction ?: 0.0) > 0.0)
            assertTrue(mode.scientificProfile().ciderD != null)
        }
    }
}
