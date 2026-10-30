package com.klcn.mobilecaptioning

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Runtime modes exposed by the Phase 07K research integration.
 *
 * V1 remains the default and is deliberately kept as a fallback until the
 * physical-device gate has been completed. V2 modes are the immutable
 * artifacts selected by Phases 07G-07J.
 */
internal enum class CaptionModelMode(
    val pairId: String,
    val displayName: String,
    val teacherName: String,
    val studentName: String,
    val encoderAsset: String?,
    val decoderAsset: String?,
    val expectedEncoderSha256: String?,
    val expectedDecoderSha256: String?,
    val normalizationMean: FloatArray,
    val normalizationStd: FloatArray,
    val validationCiderD: Double?,
    val validationBleu4: Double?,
    val validationCe: Double?,
    val packageBytes: Long?,
    val packageReductionFraction: Double?,
    val kdMethodVi: String,
    val kdMethodEn: String,
    val teacherPackageMb: Double?,
    val studentFp32PackageMb: Double?,
    val decoderFixedBatchSize: Int = 1,
    val decoderAttentionMaskInt64: Boolean = false,
    val studentParameters: Long? = null,
    val vocabularyAsset: String = "v2/v2_word_vocabulary_minfreq5.json",
    val vocabularyArrayKey: String = "id_to_token",
    val vocabularySize: Int = 10_080,
    val encoderInputName: String = "image",
    val encoderOutputName: String = "visual_memory",
    val decoderTokenIdsName: String = "decoder_input_ids",
    val decoderMemoryName: String = "visual_memory",
    val decoderAttentionMaskName: String = "decoder_attention_mask",
    val decoderOutputName: String = "logits",
    val runtimePrecision: String = "Dynamic INT8",
    val maximumGeneratedTokens: Int = 31,
    val minimumWordsBeforeEos: Int = 2,
    val forbidUnknownToken: Boolean = true,
    val experimental: Boolean = false,
    val comingSoon: Boolean = false,
    val datasetName: String = "MS COCO",
) {
    V1_FALLBACK(
        pairId = "V1",
        displayName = "V1 FP32",
        teacherName = "Mô hình tham chiếu V1",
        studentName = "Bộ suy luận V1",
        encoderAsset = "baseline_encoder_fp32.onnx",
        decoderAsset = "baseline_decoder_fp32.onnx",
        expectedEncoderSha256 =
            "59fbd6d24f0c82ceec27cf69ed05cda47fcb39d568b68a4a67f06dee080f19e3",
        expectedDecoderSha256 =
            "0b5c6fec9158a9c00847a554936faeeafcf2560ee75d9caf0bacba95cdea8adb",
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = null,
        validationBleu4 = null,
        validationCe = null,
        packageBytes = 34_569_336L,
        packageReductionFraction = null,
        kdMethodVi = "Không áp dụng",
        kdMethodEn = "Not applicable",
        teacherPackageMb = null,
        studentFp32PackageMb = null,
        decoderFixedBatchSize = 3,
        decoderAttentionMaskInt64 = true,
        studentParameters = 8_480_816L,
        vocabularySize = 12_293,
        runtimePrecision = "FP32",
        experimental = true,
    ),
    T1_S1(
        pairId = "T1-S1",
        displayName = "T1→S1 Dense Map",
        teacherName = "T1 · ExpansionNet v2",
        studentName = "S1 · MobileNetV3-Large",
        encoderAsset = "v2/t1_s1/encoder_int8.onnx",
        decoderAsset = "v2/t1_s1/decoder_int8.onnx",
        expectedEncoderSha256 =
            "b015b503e8af8e9d86db04cbb00bd7a1f80d92e3274c590f84cfedf7f237efd4",
        expectedDecoderSha256 =
            "ee7a324290aeae95613b5de1f8bcb88f1e88bf1652c98a9fe55389412c25d2cd",
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = 1.0844795254322963,
        validationBleu4 = 0.3397668328851314,
        validationCe = 2.99363002553186,
        packageBytes = 28_534_954L,
        packageReductionFraction = 0.38371905577875276,
        kdMethodVi = "Dense Mapped Logit-KD",
        kdMethodEn = "Dense Mapped Logit-KD",
        teacherPackageMb = 2_819.735367,
        studentFp32PackageMb = 46.301860,
        decoderFixedBatchSize = 3,
        decoderAttentionMaskInt64 = true,
        studentParameters = 8_990_352L,
    ),
    T1_S1_SEQUENCE(
        pairId = "T1-S1-SEQ",
        displayName = "T1→S1 Sequence KD",
        teacherName = "T1 · ExpansionNet v2",
        studentName = "S1 · MobileNetV3-Large",
        encoderAsset = "v2/t1_s1_sequence/encoder_int8.onnx",
        decoderAsset = "v2/t1_s1_sequence/decoder_int8.onnx",
        expectedEncoderSha256 =
            "16390358c41161a54e7af0598ff87336d22bcdb3ad837f7d6917d75edcd217eb",
        expectedDecoderSha256 =
            "0184a94a7ae7dfc5560dfba9aebae9222337e34de4d242f2b3b5c912adcec261",
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = 1.1092022261075705,
        validationBleu4 = 0.3464033873013923,
        validationCe = 2.354636709833465,
        packageBytes = 27_946_229L,
        packageReductionFraction = 0.23013484706032017,
        kdMethodVi = "Sequence KD",
        kdMethodEn = "Sequence KD",
        teacherPackageMb = 2_819.735367,
        studentFp32PackageMb = 36.300161,
        decoderFixedBatchSize = 1,
        decoderAttentionMaskInt64 = false,
        studentParameters = 8_990_352L,
    ),
    T2_S2(
        pairId = "T2-S2",
        displayName = "T2→S2 INT8",
        teacherName = "T2 · BLIP",
        studentName = "S2 · MobileViT-S",
        encoderAsset = "v2/t2_s2/encoder_int8.onnx",
        decoderAsset = "v2/t2_s2/decoder_int8.onnx",
        expectedEncoderSha256 =
            "c49e2f29b051ae2db294d4ba358de2df45104828c3058276a29839ab0e04a5c5",
        expectedDecoderSha256 =
            "e46e47ec3ee8087413aa81dcf2a99379f0f3485193e4a660d76d2cca62ecb7c9",
        normalizationMean = floatArrayOf(0.0f, 0.0f, 0.0f),
        normalizationStd = floatArrayOf(1.0f, 1.0f, 1.0f),
        validationCiderD = 1.0206967277666594,
        validationBleu4 = 0.323713356763339,
        validationCe = 2.310971689034545,
        packageBytes = 26_898_053L,
        packageReductionFraction = 0.3830937847658409,
        kdMethodVi = "Sequence KD",
        kdMethodEn = "Sequence KD",
        teacherPackageMb = 989.988954,
        studentFp32PackageMb = 43.601527,
        studentParameters = 10_874_112L,
    ),
    T3_S3(
        pairId = "T3-S3",
        displayName = "T3→S3 INT8",
        teacherName = "T3 · SigLIP ViT-B/16",
        studentName = "S3 · TinyViT-5M",
        encoderAsset = "v2/t3_s3/encoder_int8.onnx",
        decoderAsset = "v2/t3_s3/decoder_int8.onnx",
        expectedEncoderSha256 =
            "305860fd6c25b5b8c095aa538544a9011395d46dcc653b1d72717844e183edf0",
        expectedDecoderSha256 =
            "72c0b16086cfb53210d35dfd48b12c8b4bd82d8223115805f41cef382f0e5304",
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = 1.0940243158427037,
        validationBleu4 = 0.34342086890197177,
        validationCe = 2.1172064699553945,
        packageBytes = 38_311_909L,
        packageReductionFraction = 0.1658590243100897,
        kdMethodVi = "Direct Logit KD",
        kdMethodEn = "Direct Logit KD",
        teacherPackageMb = 1_055.013770,
        studentFp32PackageMb = 45.929777,
        studentParameters = 10_925_684L,
    ),
    F30K_T3_S3_EXPERIMENTAL(
        pairId = "F30K-T3-S3",
        displayName = "F30K T3→S3 EXP",
        teacherName = "T3 · SigLIP ViT-B/16 thích nghi Flickr30k",
        studentName = "S3 · TinyViT-5M",
        encoderAsset = "v2/f30k_t3_s3/f30k_t3s3_seed42_encoder_fp32.onnx",
        decoderAsset = "v2/f30k_t3_s3/f30k_t3s3_seed42_decoder_dynamic_int8.onnx",
        expectedEncoderSha256 =
            "1ff7e46d053142bd1cccf0e02e9ffe0c6e38495274e4dc60aa5f059da03ae984",
        expectedDecoderSha256 =
            "ece2a7b788529e515467f28188b6e6eca0c680fb11c04104b683c21973ea2973",
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = 0.6171285226440146,
        validationBleu4 = 0.2799816946180795,
        validationCe = 2.7555685628753697,
        packageBytes = 28_503_127L,
        packageReductionFraction = 0.42130889789589887,
        kdMethodVi = "Chưng cất logit trực tiếp",
        kdMethodEn = "Direct Logit-KD",
        teacherPackageMb = 1_055.013770,
        studentFp32PackageMb = 49.254476,
        studentParameters = 10_251_316L,
        vocabularyAsset = "v2/f30k_t3_s3/f30k_vocabulary_minfreq5.json",
        vocabularyArrayKey = "tokens",
        vocabularySize = 7_456,
        encoderInputName = "images",
        encoderOutputName = "memory",
        decoderTokenIdsName = "token_ids",
        decoderMemoryName = "memory",
        decoderAttentionMaskName = "attention_mask",
        decoderOutputName = "logits",
        runtimePrecision = "FP32 encoder + Dynamic INT8 decoder",
        maximumGeneratedTokens = 30,
        minimumWordsBeforeEos = 0,
        forbidUnknownToken = false,
        experimental = true,
        datasetName = "Flickr30k",
    ),
    T1_S1_HYBRID_COMING_SOON(
        pairId = "T1-S1-HYBRID",
        displayName = "T1→S1 Hybrid KD",
        teacherName = "T1 · ExpansionNet v2",
        studentName = "S1 · MobileNetV3-Large",
        encoderAsset = null,
        decoderAsset = null,
        expectedEncoderSha256 = null,
        expectedDecoderSha256 = null,
        normalizationMean = floatArrayOf(0.485f, 0.456f, 0.406f),
        normalizationStd = floatArrayOf(0.229f, 0.224f, 0.225f),
        validationCiderD = null,
        validationBleu4 = null,
        validationCe = null,
        packageBytes = null,
        packageReductionFraction = null,
        kdMethodVi = "Sequence KD + Dense Mapped Logit-KD",
        kdMethodEn = "Sequence KD + Dense Mapped Logit-KD",
        teacherPackageMb = 2_819.735367,
        studentFp32PackageMb = null,
        studentParameters = 8_990_352L,
        comingSoon = true,
    );

    val isV2: Boolean
        get() = this != V1_FALLBACK

    val studentInt8PackageMb: Double?
        get() = packageBytes?.div(1_000_000.0)

    val teacherToStudentInt8Ratio: Double?
        get() = teacherPackageMb?.let { teacherMb ->
            studentInt8PackageMb
                ?.takeIf { it > 0.0 }
                ?.let { studentMb -> teacherMb / studentMb }
        }

    fun kdMethod(vietnamese: Boolean): String = if (vietnamese) {
        kdMethodVi
    } else {
        kdMethodEn
    }

    companion object {
        val mainModes: List<CaptionModelMode> = listOf(
            T1_S1,
            T1_S1_SEQUENCE,
            T1_S1_HYBRID_COMING_SOON,
            T2_S2,
            T3_S3,
        )
        val researchModes: List<CaptionModelMode> = mainModes
        val experimentalModes: List<CaptionModelMode> = listOf(
            F30K_T3_S3_EXPERIMENTAL,
            V1_FALLBACK,
        )
        val visibleModes: List<CaptionModelMode> = mainModes + experimentalModes
        val selectableModes: List<CaptionModelMode> = visibleModes.filterNot { it.comingSoon }
    }
}

/**
 * Separate V2 engine. It intentionally does not reuse Phase8CaptionEngine
 * because the V1 and V2 tensor contracts are incompatible.
 */
internal object Phase7KV2CaptionEngine {
    private const val sequenceLength = 31
    private const val memoryTokens = 49
    private const val hiddenSize = 256
    private const val beamSize = 3
    private const val padId = 0L
    private const val bosId = 1L
    private const val eosId = 2L

    private val environment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var activeMode: CaptionModelMode? = null
    private var indexToWord: List<String> = emptyList()

    var modelLoadMs: Double = 0.0
        private set

    val selectedMode: CaptionModelMode?
        get() = activeMode

    @Synchronized
    fun initialize(context: Context, mode: CaptionModelMode): Double {
        require(mode.isV2) { "Phase7K V2 engine requires a V2 model mode" }
        require(mode in CaptionModelMode.selectableModes) {
            "${mode.displayName} is not available for inference"
        }
        if (
            activeMode == mode &&
            encoderSession != null &&
            decoderSession != null
        ) {
            return modelLoadMs
        }

        release()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val encoderAsset = requireNotNull(mode.encoderAsset)
        val decoderAsset = requireNotNull(mode.decoderAsset)
        val encoderBytes = context.assets.open(encoderAsset).use { it.readBytes() }
        val decoderBytes = context.assets.open(decoderAsset).use { it.readBytes() }
        check(sha256Hex(encoderBytes) == mode.expectedEncoderSha256) {
            "V2 encoder SHA-256 mismatch for ${mode.pairId}"
        }
        check(sha256Hex(decoderBytes) == mode.expectedDecoderSha256) {
            "V2 decoder SHA-256 mismatch for ${mode.pairId}"
        }

        val vocabularyJson = JSONObject(
            context.assets.open(mode.vocabularyAsset)
                .bufferedReader()
                .use { it.readText() },
        )
        val vocabularyArray = vocabularyJson.getJSONArray(mode.vocabularyArrayKey)
        check(vocabularyArray.length() == mode.vocabularySize) {
            "${mode.pairId} vocabulary must contain exactly ${mode.vocabularySize} tokens"
        }
        val loadedVocabulary = List(vocabularyArray.length()) { index ->
            vocabularyArray.getString(index)
        }
        check(loadedVocabulary.take(4) == listOf("<pad>", "<bos>", "<eos>", "<unk>"))

        val options = OrtSession.SessionOptions()
        val loadedEncoder = environment.createSession(encoderBytes, options)
        val loadedDecoder = try {
            environment.createSession(decoderBytes, options)
        } catch (error: Throwable) {
            loadedEncoder.close()
            options.close()
            throw error
        }
        options.close()

        try {
            check(loadedEncoder.inputNames == setOf(mode.encoderInputName))
            check(loadedEncoder.outputNames == setOf(mode.encoderOutputName))
            check(
                loadedDecoder.inputNames ==
                    setOf(
                        mode.decoderTokenIdsName,
                        mode.decoderMemoryName,
                        mode.decoderAttentionMaskName,
                    ),
            )
            check(loadedDecoder.outputNames == setOf(mode.decoderOutputName))
        } catch (error: Throwable) {
            loadedDecoder.close()
            loadedEncoder.close()
            throw error
        }

        encoderSession = loadedEncoder
        decoderSession = loadedDecoder
        indexToWord = loadedVocabulary
        activeMode = mode
        modelLoadMs = (
            SystemClock.elapsedRealtimeNanos() - startedAt
        ) / 1_000_000.0
        return modelLoadMs
    }

    @Synchronized
    fun release() {
        decoderSession?.close()
        encoderSession?.close()
        decoderSession = null
        encoderSession = null
        activeMode = null
        indexToWord = emptyList()
        modelLoadMs = 0.0
    }

    @Synchronized
    fun caption(bitmap: Bitmap): CaptionInferenceResult {
        val mode = checkNotNull(activeMode) {
            "Phase7K V2 engine has not been initialized"
        }
        val encoder = checkNotNull(encoderSession)
        val decoder = checkNotNull(decoderSession)
        val totalStartedAt = SystemClock.elapsedRealtimeNanos()

        val preprocessingStartedAt = SystemClock.elapsedRealtimeNanos()
        val imageTensor = preprocessV2Image(
            source = bitmap,
            mean = mode.normalizationMean,
            std = mode.normalizationStd,
        )
        val preprocessingMs = (
            SystemClock.elapsedRealtimeNanos() - preprocessingStartedAt
        ) / 1_000_000.0
        check(imageTensor.size == 3 * 224 * 224)
        check(imageTensor.all(Float::isFinite))

        val encoderStartedAt = SystemClock.elapsedRealtimeNanos()
        val visualMemory = runV2Encoder(encoder, imageTensor, mode)
        val encoderMs = (
            SystemClock.elapsedRealtimeNanos() - encoderStartedAt
        ) / 1_000_000.0
        check(visualMemory.size == memoryTokens * hiddenSize)
        check(visualMemory.all(Float::isFinite))

        val beamStartedAt = SystemClock.elapsedRealtimeNanos()
        val beamResult = when {
            mode.decoderFixedBatchSize == 3 && mode.decoderAttentionMaskInt64 ->
                decodeBeam3FixedBatch(decoder, visualMemory, mode)
            mode.decoderFixedBatchSize == 1 && !mode.decoderAttentionMaskInt64 ->
                decodeBeam3Sequential(decoder, visualMemory, mode)
            else -> error("Unsupported decoder contract for ${mode.pairId}")
        }
        val beamMs = (
            SystemClock.elapsedRealtimeNanos() - beamStartedAt
        ) / 1_000_000.0
        check(beamResult.eosPosition >= 0) {
            "V2 Beam-3 did not emit EOS for ${mode.pairId}"
        }
        val tokenIds = beamResult.tokenIds.take(beamResult.eosPosition + 1)
        val captionWords = tokenIds
            .filter { tokenId -> tokenId !in setOf(0L, 1L, 2L, 3L) }
            .map { tokenId ->
                check(tokenId in 0 until indexToWord.size.toLong())
                indexToWord[tokenId.toInt()]
            }
        val rawCaption = captionWords.joinToString(" ").trim()
        val caption = if (mode.experimental) {
            listOf(".", ",", "!", "?", ";", ":").fold(rawCaption) { text, mark ->
                text.replace(" $mark", mark)
            }
        } else {
            rawCaption
        }
        val forbiddenBeforeEos = tokenIds
            .drop(1)
            .dropLast(1)
            .any { tokenId ->
                tokenId == 0L || tokenId == 1L ||
                    (mode.forbidUnknownToken && tokenId == 3L)
            }
        check(captionWords.size >= mode.minimumWordsBeforeEos)
        check(caption.isNotBlank())
        check(!forbiddenBeforeEos)

        return CaptionInferenceResult(
            caption = caption,
            tokenIds = tokenIds,
            wordCount = captionWords.size,
            eosPosition = beamResult.eosPosition,
            rawScore = beamResult.rawScore,
            normalizedScore = beamResult.normalizedScore,
            preprocessingMs = preprocessingMs,
            encoderMs = encoderMs,
            beamMs = beamMs,
            totalMs = (
                SystemClock.elapsedRealtimeNanos() - totalStartedAt
            ) / 1_000_000.0,
        )
    }

    private data class Beam(
        val tokens: List<Long>,
        val score: Float,
        val finished: Boolean,
    ) {
        val normalizedScore: Float
            get() {
                val generatedLength = max(1, tokens.size - 1)
                return score / generatedLength.toDouble().pow(0.7).toFloat()
            }
    }

    private data class BeamResult(
        val tokenIds: List<Long>,
        val eosPosition: Int,
        val rawScore: Float,
        val normalizedScore: Float,
    )

    /** Compatibility path for the earlier T2-S2 and T3-S3 batch-1 graphs. */
    private fun decodeBeam3Sequential(
        decoder: OrtSession,
        visualMemory: FloatArray,
        mode: CaptionModelMode,
    ): BeamResult {
        var beams = listOf(Beam(tokens = listOf(bosId), score = 0f, finished = false))
        for (generationStep in 0 until mode.maximumGeneratedTokens) {
            val candidates = mutableListOf<Beam>()
            for (beam in beams) {
                if (beam.finished) {
                    candidates += beam
                    continue
                }
                val inputIds = LongArray(sequenceLength)
                val attentionMask = BooleanArray(sequenceLength)
                val length = min(beam.tokens.size, sequenceLength)
                for (position in 0 until length) {
                    inputIds[position] = beam.tokens[position]
                    attentionMask[position] = true
                }
                val logits = runV2DecoderBatch1Bool(
                    session = decoder,
                    visualMemory = visualMemory,
                    inputIds = inputIds,
                    attentionMask = attentionMask,
                    mode = mode,
                )
                val position = min(beam.tokens.size - 1, sequenceLength - 1)
                val offset = position * mode.vocabularySize
                val normalizerStart = if (mode.experimental) 2 else 0
                val normalizerTokenIds = normalizerStart until mode.vocabularySize
                val maximumLogit = normalizerTokenIds
                    .maxOf { tokenId -> logits[offset + tokenId] }
                var exponentialSum = 0.0
                for (tokenId in normalizerTokenIds) {
                    exponentialSum += exp(
                        (logits[offset + tokenId] - maximumLogit).toDouble(),
                    )
                }
                val logSumExp = maximumLogit.toDouble() + ln(exponentialSum)
                val topScores = FloatArray(beamSize) { Float.NEGATIVE_INFINITY }
                val topTokenIds = IntArray(beamSize) { -1 }
                val lexicalCount = max(0, beam.tokens.size - 1)
                for (tokenId in 0 until mode.vocabularySize) {
                    if (tokenId == 0 || tokenId == 1) continue
                    if (mode.forbidUnknownToken && tokenId == 3) continue
                    if (
                        lexicalCount < mode.minimumWordsBeforeEos &&
                        tokenId == eosId.toInt()
                    ) continue
                    insertTopToken(
                        score = (logits[offset + tokenId].toDouble() - logSumExp).toFloat(),
                        tokenId = tokenId,
                        topScores = topScores,
                        topTokenIds = topTokenIds,
                    )
                }
                for (rank in 0 until beamSize) {
                    check(topTokenIds[rank] >= 0)
                    val nextTokenId = topTokenIds[rank].toLong()
                    candidates += Beam(
                        tokens = beam.tokens + nextTokenId,
                        score = beam.score + topScores[rank],
                        finished = nextTokenId == eosId,
                    )
                }
            }
            beams = candidates.sortedByDescending(Beam::normalizedScore).take(beamSize)
            if (beams.all(Beam::finished)) break
        }
        val best = beams.maxBy(Beam::normalizedScore)
        return BeamResult(
            tokenIds = best.tokens,
            eosPosition = best.tokens.indexOf(eosId),
            rawScore = best.score,
            normalizedScore = best.normalizedScore,
        )
    }

    /**
     * Beam-3 matching the locked Validation implementation.
     *
     * The production decoder graph has a fixed batch of three. Active beams
     * are therefore evaluated in one batch and unused rows are padded. Only
     * logits belonging to active rows participate in candidate selection.
     */
    private fun decodeBeam3FixedBatch(
        decoder: OrtSession,
        visualMemory: FloatArray,
        mode: CaptionModelMode,
    ): BeamResult {
        var beams = listOf(Beam(tokens = listOf(bosId), score = 0f, finished = false))
        for (generationStep in 0 until mode.maximumGeneratedTokens) {
            val active = beams.filterNot(Beam::finished)
            if (active.isEmpty()) {
                break
            }
            check(active.size <= beamSize)
            val inputIds = Array(beamSize) { LongArray(sequenceLength) }
            val attentionMask = Array(beamSize) { LongArray(sequenceLength) }
            active.forEachIndexed { row, beam ->
                val length = min(beam.tokens.size, sequenceLength)
                for (position in 0 until length) {
                    inputIds[row][position] = beam.tokens[position]
                    attentionMask[row][position] = 1L
                }
            }
            val logits = runV2DecoderFixedBatch3(
                session = decoder,
                visualMemory = visualMemory,
                inputIds = inputIds,
                attentionMask = attentionMask,
                mode = mode,
            )
            val candidates = beams.filter(Beam::finished).toMutableList()
            active.forEachIndexed { row, beam ->
                val position = min(beam.tokens.size - 1, sequenceLength - 1)
                val offset = (row * sequenceLength + position) * mode.vocabularySize
                val maximumLogit = (0 until mode.vocabularySize)
                    .maxOf { tokenId -> logits[offset + tokenId] }
                var exponentialSum = 0.0
                for (tokenId in 0 until mode.vocabularySize) {
                    exponentialSum += exp(
                        (logits[offset + tokenId] - maximumLogit).toDouble(),
                    )
                }
                val logSumExp = maximumLogit.toDouble() + ln(exponentialSum)
                val topScores = FloatArray(beamSize) { Float.NEGATIVE_INFINITY }
                val topTokenIds = IntArray(beamSize) { -1 }
                val lexicalCount = max(0, beam.tokens.size - 1)
                for (tokenId in 0 until mode.vocabularySize) {
                    if (
                        tokenId == 0 || tokenId == 1 ||
                        (mode.forbidUnknownToken && tokenId == 3)
                    ) {
                        continue
                    }
                    if (
                        lexicalCount < mode.minimumWordsBeforeEos &&
                        tokenId == eosId.toInt()
                    ) {
                        continue
                    }
                    val logProbability = (
                        logits[offset + tokenId].toDouble() - logSumExp
                    ).toFloat()
                    insertTopToken(
                        score = logProbability,
                        tokenId = tokenId,
                        topScores = topScores,
                        topTokenIds = topTokenIds,
                    )
                }
                for (rank in 0 until beamSize) {
                    check(topTokenIds[rank] >= 0)
                    val nextTokenId = topTokenIds[rank].toLong()
                    candidates += Beam(
                        tokens = beam.tokens + nextTokenId,
                        score = beam.score + topScores[rank],
                        finished = nextTokenId == eosId,
                    )
                }
            }
            beams = candidates
                .sortedByDescending(Beam::normalizedScore)
                .take(beamSize)
            if (beams.all(Beam::finished)) {
                break
            }
        }
        val best = beams.maxBy(Beam::normalizedScore)
        return BeamResult(
            tokenIds = best.tokens,
            eosPosition = best.tokens.indexOf(eosId),
            rawScore = best.score,
            normalizedScore = best.normalizedScore,
        )
    }

    private fun insertTopToken(
        score: Float,
        tokenId: Int,
        topScores: FloatArray,
        topTokenIds: IntArray,
    ) {
        for (rank in topScores.indices) {
            if (score > topScores[rank]) {
                for (shift in topScores.lastIndex downTo rank + 1) {
                    topScores[shift] = topScores[shift - 1]
                    topTokenIds[shift] = topTokenIds[shift - 1]
                }
                topScores[rank] = score
                topTokenIds[rank] = tokenId
                return
            }
        }
    }

    private fun runV2Encoder(
        session: OrtSession,
        input: FloatArray,
        mode: CaptionModelMode,
    ): FloatArray {
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, 224, 224),
        ).use { inputTensor ->
            session.run(mapOf(mode.encoderInputName to inputTensor)).use { result ->
                check(result.size() == 1)
                val tensor = result.get(0) as? OnnxTensor
                    ?: error("V2 encoder output is not an OnnxTensor")
                val outputBuffer = tensor.floatBuffer
                val output = FloatArray(outputBuffer.remaining())
                outputBuffer.get(output)
                return output
            }
        }
    }

    private fun runV2DecoderBatch1Bool(
        session: OrtSession,
        visualMemory: FloatArray,
        inputIds: LongArray,
        attentionMask: BooleanArray,
        mode: CaptionModelMode,
    ): FloatArray {
        require(visualMemory.size == memoryTokens * hiddenSize)
        require(inputIds.size == sequenceLength)
        require(attentionMask.size == sequenceLength)
        val boolBuffer = ByteBuffer
            .allocateDirect(sequenceLength)
            .order(ByteOrder.nativeOrder())
        attentionMask.forEach { value ->
            boolBuffer.put(if (value) 1.toByte() else 0.toByte())
        }
        boolBuffer.flip()

        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(inputIds),
            longArrayOf(1, sequenceLength.toLong()),
        ).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(visualMemory),
                longArrayOf(1, memoryTokens.toLong(), hiddenSize.toLong()),
            ).use { memoryTensor ->
                OnnxTensor.createTensor(
                    environment,
                    boolBuffer,
                    longArrayOf(1, sequenceLength.toLong()),
                    OnnxJavaType.BOOL,
                ).use { maskTensor ->
                    session.run(
                        mapOf(
                            mode.decoderTokenIdsName to idsTensor,
                            mode.decoderMemoryName to memoryTensor,
                            mode.decoderAttentionMaskName to maskTensor,
                        ),
                    ).use { result ->
                        check(result.size() == 1)
                        val tensor = result.get(0) as? OnnxTensor
                            ?: error("V2 decoder output is not an OnnxTensor")
                        val outputBuffer = tensor.floatBuffer
                        val output = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(output)
                        return output
                    }
                }
            }
        }
    }

    private fun runV2DecoderFixedBatch3(
        session: OrtSession,
        visualMemory: FloatArray,
        inputIds: Array<LongArray>,
        attentionMask: Array<LongArray>,
        mode: CaptionModelMode,
    ): FloatArray {
        require(visualMemory.size == memoryTokens * hiddenSize)
        require(inputIds.size == beamSize && attentionMask.size == beamSize)
        require(inputIds.all { it.size == sequenceLength })
        require(attentionMask.all { it.size == sequenceLength })

        val flatInputIds = LongArray(beamSize * sequenceLength)
        val flatAttentionMask = LongArray(beamSize * sequenceLength)
        val batchedMemory = FloatArray(beamSize * visualMemory.size)
        for (row in 0 until beamSize) {
            inputIds[row].copyInto(flatInputIds, row * sequenceLength)
            attentionMask[row].copyInto(flatAttentionMask, row * sequenceLength)
            visualMemory.copyInto(batchedMemory, row * visualMemory.size)
        }

        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(flatInputIds),
            longArrayOf(beamSize.toLong(), sequenceLength.toLong()),
        ).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(batchedMemory),
                longArrayOf(
                    beamSize.toLong(),
                    memoryTokens.toLong(),
                    hiddenSize.toLong(),
                ),
            ).use { memoryTensor ->
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(flatAttentionMask),
                    longArrayOf(beamSize.toLong(), sequenceLength.toLong()),
                ).use { maskTensor ->
                    session.run(
                        mapOf(
                            mode.decoderTokenIdsName to idsTensor,
                            mode.decoderMemoryName to memoryTensor,
                            mode.decoderAttentionMaskName to maskTensor,
                        ),
                    ).use { result ->
                        check(result.size() == 1)
                        val tensor = result.get(0) as? OnnxTensor
                            ?: error("V2 decoder output is not an OnnxTensor")
                        val outputBuffer = tensor.floatBuffer
                        val output = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(output)
                        check(
                            output.size ==
                                beamSize * sequenceLength * mode.vocabularySize,
                        )
                        return output
                    }
                }
            }
        }
    }
}

private fun preprocessV2Image(
    source: Bitmap,
    mean: FloatArray,
    std: FloatArray,
): FloatArray {
    require(source.width > 0 && source.height > 0)
    require(mean.size == 3 && std.size == 3)
    val resizeShorter = 256
    val targetSize = 224
    val scale = resizeShorter.toDouble() / min(source.width, source.height)
    val resizedWidth = max(resizeShorter, (source.width * scale).roundToInt())
    val resizedHeight = max(resizeShorter, (source.height * scale).roundToInt())
    val resized = resizeV2BitmapBicubic(source, resizedWidth, resizedHeight)
    val cropLeft = (resizedWidth - targetSize) / 2
    val cropTop = (resizedHeight - targetSize) / 2
    val pixels = IntArray(targetSize * targetSize)
    resized.getPixels(
        pixels,
        0,
        targetSize,
        cropLeft,
        cropTop,
        targetSize,
        targetSize,
    )
    resized.recycle()

    val planeSize = targetSize * targetSize
    val output = FloatArray(3 * planeSize)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        output[index] = (Color.red(pixel) / 255f - mean[0]) / std[0]
        output[planeSize + index] =
            (Color.green(pixel) / 255f - mean[1]) / std[1]
        output[2 * planeSize + index] =
            (Color.blue(pixel) / 255f - mean[2]) / std[2]
    }
    return output
}

private fun resizeV2BitmapBicubic(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap {
    val sourceWidth = source.width
    val sourceHeight = source.height
    val sourcePixels = IntArray(sourceWidth * sourceHeight)
    source.getPixels(
        sourcePixels,
        0,
        sourceWidth,
        0,
        0,
        sourceWidth,
        sourceHeight,
    )
    val outputPixels = IntArray(targetWidth * targetHeight)
    val xScale = sourceWidth.toDouble() / targetWidth
    val yScale = sourceHeight.toDouble() / targetHeight
    for (targetY in 0 until targetHeight) {
        val sourceY = (targetY + 0.5) * yScale - 0.5
        val sourceYBase = floor(sourceY).toInt()
        for (targetX in 0 until targetWidth) {
            val sourceX = (targetX + 0.5) * xScale - 0.5
            val sourceXBase = floor(sourceX).toInt()
            var red = 0.0
            var green = 0.0
            var blue = 0.0
            var weightTotal = 0.0
            for (offsetY in -1..2) {
                val sampledY = (sourceYBase + offsetY)
                    .coerceIn(0, sourceHeight - 1)
                val weightY = cubicV2Weight(
                    sourceY - (sourceYBase + offsetY),
                )
                for (offsetX in -1..2) {
                    val sampledX = (sourceXBase + offsetX)
                        .coerceIn(0, sourceWidth - 1)
                    val weightX = cubicV2Weight(
                        sourceX - (sourceXBase + offsetX),
                    )
                    val weight = weightX * weightY
                    val pixel = sourcePixels[sampledY * sourceWidth + sampledX]
                    red += Color.red(pixel) * weight
                    green += Color.green(pixel) * weight
                    blue += Color.blue(pixel) * weight
                    weightTotal += weight
                }
            }
            outputPixels[targetY * targetWidth + targetX] = Color.rgb(
                (red / weightTotal).roundToInt().coerceIn(0, 255),
                (green / weightTotal).roundToInt().coerceIn(0, 255),
                (blue / weightTotal).roundToInt().coerceIn(0, 255),
            )
        }
    }
    return Bitmap.createBitmap(
        outputPixels,
        targetWidth,
        targetHeight,
        Bitmap.Config.ARGB_8888,
    )
}

private fun cubicV2Weight(distance: Double): Double {
    val absoluteDistance = abs(distance)
    return when {
        absoluteDistance <= 1.0 ->
            1.5 * absoluteDistance * absoluteDistance * absoluteDistance -
                2.5 * absoluteDistance * absoluteDistance + 1.0
        absoluteDistance < 2.0 ->
            -0.5 * absoluteDistance * absoluteDistance * absoluteDistance +
                2.5 * absoluteDistance * absoluteDistance -
                4.0 * absoluteDistance + 2.0
        else -> 0.0
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
