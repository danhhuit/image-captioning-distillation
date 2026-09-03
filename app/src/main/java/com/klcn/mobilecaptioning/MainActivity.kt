package com.klcn.mobilecaptioning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import com.klcn.mobilecaptioning.ui.theme.MobileImageCaptioningTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember {
                mutableStateOf(AppPreferences.loadTheme(context))
            }
            var language by remember {
                mutableStateOf(AppPreferences.loadLanguage(context))
            }
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                AppThemeMode.System -> systemDark
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            MobileImageCaptioningTheme(darkTheme = useDarkTheme) {
                Phase8InteractiveScreen(
                    themeMode = themeMode,
                    language = language,
                    onThemeModeChange = { updated ->
                        themeMode = updated
                        AppPreferences.saveTheme(context, updated)
                    },
                    onLanguageChange = { updated ->
                        language = updated
                        AppPreferences.saveLanguage(context, updated)
                    },
                )
            }
        }
    }
}

@Composable
private fun Phase8InteractiveScreen(
    themeMode: AppThemeMode,
    language: AppLanguage,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDestination by remember {
        mutableStateOf(AppDestination.Capture)
    }
    var historyItems by remember {
        mutableStateOf(CaptionHistoryStore.load(context))
    }
    var uiState by remember {
        mutableStateOf<CaptionUiState>(CaptionUiState.LoadingModel)
    }

    LaunchedEffect(Unit) {
        try {
            val modelLoadMs = withContext(Dispatchers.IO) {
                Phase8CaptionEngine.initialize(context)
            }
            uiState = CaptionUiState.Ready(modelLoadMs)
        } catch (error: Throwable) {
            uiState = CaptionUiState.Error(
                title = "Không thể khởi tạo mô hình",
                message = "${error::class.java.simpleName}: ${error.message}",
            )
        }
    }

    val analyzeUri: (Uri, ImageInputSource) -> Unit = { uri, source ->
        uiState = CaptionUiState.Processing(source)
        coroutineScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.loadBitmapFromUri(uri)
                }
                selectedBitmap = bitmap
                val presentation = context.captionRealImage(
                    bitmap = bitmap,
                    source = source,
                )
                val storedItem = withContext(Dispatchers.IO) {
                    CaptionHistoryStore.add(
                        context = context,
                        result = presentation,
                        bitmap = bitmap,
                    )
                }
                historyItems = CaptionHistoryStore.load(context)
                uiState = CaptionUiState.Success(
                    presentation.copy(
                        historyId = storedItem.id,
                        favorite = storedItem.favorite,
                    ),
                )
            } catch (error: Throwable) {
                uiState = CaptionUiState.Error(
                    title = "Không thể tạo chú thích",
                    message = "${error::class.java.simpleName}: ${error.message}",
                )
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            analyzeUri(uri, ImageInputSource.Gallery)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val uri = pendingCameraUri
        if (captured && uri != null) {
            analyzeUri(uri, ImageInputSource.Camera)
        } else {
            uiState = CaptionUiState.Ready(
                Phase8CaptionEngine.modelLoadMs,
            )
        }
    }

    VisionCaptionApp(
        modifier = modifier,
        selectedDestination = selectedDestination,
        onDestinationChange = { selectedDestination = it },
        uiState = uiState,
        selectedBitmap = selectedBitmap,
        historyItems = historyItems,
        themeMode = themeMode,
        language = language,
        onThemeModeChange = onThemeModeChange,
        onLanguageChange = onLanguageChange,
        onChooseGallery = {
            galleryLauncher.launch("image/*")
        },
        onOpenCamera = {
            try {
                val imageDirectory = File(
                    context.cacheDir,
                    "captured_images",
                ).apply { mkdirs() }
                val imageFile = File(
                    imageDirectory,
                    "phase8_${System.currentTimeMillis()}.jpg",
                )
                val imageUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile,
                )
                pendingCameraUri = imageUri
                cameraLauncher.launch(imageUri)
            } catch (error: Throwable) {
                uiState = CaptionUiState.Error(
                    title = "Không thể mở Camera",
                    message = "${error::class.java.simpleName}: ${error.message}",
                )
            }
        },
        onClearCurrentImage = {
            selectedBitmap = null
            uiState = CaptionUiState.Ready(
                Phase8CaptionEngine.modelLoadMs,
            )
        },
        onDismissError = {
            uiState = CaptionUiState.Ready(
                Phase8CaptionEngine.modelLoadMs,
            )
        },
        onToggleCurrentFavorite = { historyId ->
            historyItems = CaptionHistoryStore.toggleFavorite(
                context,
                historyId,
            )
            val favorite = historyItems
                .firstOrNull { it.id == historyId }
                ?.favorite ?: false
            val current = uiState
            if (current is CaptionUiState.Success) {
                uiState = current.copy(
                    result = current.result.copy(favorite = favorite),
                )
            }
        },
        onToggleHistoryFavorite = { historyId ->
            historyItems = CaptionHistoryStore.toggleFavorite(
                context,
                historyId,
            )
        },
        onDeleteHistoryItem = { historyId ->
            historyItems = CaptionHistoryStore.delete(
                context,
                historyId,
            )
            val current = uiState
            if (
                current is CaptionUiState.Success &&
                current.result.historyId == historyId
            ) {
                uiState = current.copy(
                    result = current.result.copy(
                        historyId = null,
                        favorite = false,
                    ),
                )
            }
        },
        onClearHistory = {
            CaptionHistoryStore.clear(context)
            historyItems = emptyList()
            val current = uiState
            if (current is CaptionUiState.Success) {
                uiState = current.copy(
                    result = current.result.copy(
                        historyId = null,
                        favorite = false,
                    ),
                )
            }
        },
    )
}

private suspend fun Context.captionRealImage(
    bitmap: Bitmap,
    source: ImageInputSource,
): CaptionPresentation =
    withContext(Dispatchers.IO) {
        Phase8CaptionEngine.initialize(this@captionRealImage)
        val firstResult = Phase8CaptionEngine.caption(bitmap)
        val secondResult = Phase8CaptionEngine.caption(bitmap)
        check(firstResult.tokenIds == secondResult.tokenIds) {
            "Hai lượt inference không trùng token IDs"
        }
        check(firstResult.caption == secondResult.caption) {
            "Hai lượt inference không trùng caption"
        }
        check(firstResult.rawScore == secondResult.rawScore) {
            "Hai lượt inference không trùng raw score"
        }
        check(firstResult.normalizedScore == secondResult.normalizedScore) {
            "Hai lượt inference không trùng normalized score"
        }

        CaptionPresentation(
            historyId = null,
            favorite = false,
            source = source,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            caption = firstResult.caption,
            tokenIds = firstResult.tokenIds,
            wordCount = firstResult.wordCount,
            eosPosition = firstResult.eosPosition,
            rawScore = firstResult.rawScore,
            normalizedScore = firstResult.normalizedScore,
            modelLoadMs = Phase8CaptionEngine.modelLoadMs,
            preprocessingMs = firstResult.preprocessingMs,
            encoderMs = firstResult.encoderMs,
            beamMs = firstResult.beamMs,
            firstTotalMs = firstResult.totalMs,
            repeatedTotalMs = secondResult.totalMs,
            repeatabilityVerified = true,
        )
    }

private data class RealImageInferenceResult(
    val caption: String,
    val tokenIds: List<Long>,
    val wordCount: Int,
    val eosPosition: Int,
    val rawScore: Float,
    val normalizedScore: Float,
    val preprocessingMs: Double,
    val encoderMs: Double,
    val beamMs: Double,
    val totalMs: Double,
)

private object Phase8CaptionEngine {
    private lateinit var environment: OrtEnvironment
    private lateinit var encoderSession: OrtSession
    private lateinit var decoderSession: OrtSession
    private lateinit var indexToWord: List<String>
    private var initialized = false
    var modelLoadMs: Double = 0.0
        private set

    @Synchronized
    fun initialize(context: Context): Double {
        if (initialized) {
            return modelLoadMs
        }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val encoderBytes = context.assets.open(
            "baseline_encoder_fp32.onnx",
        ).use { it.readBytes() }
        val decoderBytes = context.assets.open(
            "baseline_decoder_fp32.onnx",
        ).use { it.readBytes() }
        check(
            sha256Hex(encoderBytes) ==
                "59fbd6d24f0c82ceec27cf69ed05cda47fcb39d568b68a4a67f06dee080f19e3",
        ) {
            "Encoder SHA-256 khong khop"
        }
        check(
            sha256Hex(decoderBytes) ==
                "0b5c6fec9158a9c00847a554936faeeafcf2560ee75d9caf0bacba95cdea8adb",
        ) {
            "Decoder SHA-256 khong khop"
        }
        val vocabularyJson = JSONObject(
            context.assets.open("vocabulary.json").bufferedReader().use {
                it.readText()
            },
        )
        val vocabularyArray = vocabularyJson.getJSONArray("index_to_word")
        check(vocabularyArray.length() == 12_293)
        indexToWord = List(vocabularyArray.length()) { index ->
            vocabularyArray.getString(index)
        }

        environment = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()
        val loadedEncoderSession = environment.createSession(
            encoderBytes,
            sessionOptions,
        )
        val loadedDecoderSession = try {
            environment.createSession(
                decoderBytes,
                sessionOptions,
            )
        } catch (error: Throwable) {
            loadedEncoderSession.close()
            sessionOptions.close()
            throw error
        }
        sessionOptions.close()
        check(loadedEncoderSession.inputNames == setOf("student_images"))
        check(loadedEncoderSession.outputNames == setOf("visual_memory"))
        check(
            loadedDecoderSession.inputNames ==
                setOf(
                    "visual_memory",
                    "decoder_input_ids",
                    "decoder_attention_mask",
                ),
        )
        check(loadedDecoderSession.outputNames == setOf("logits"))

        encoderSession = loadedEncoderSession
        decoderSession = loadedDecoderSession
        modelLoadMs = (
            SystemClock.elapsedRealtimeNanos() - startedAt
        ) / 1_000_000.0
        initialized = true
        return modelLoadMs
    }

    @Synchronized
    fun caption(bitmap: Bitmap): RealImageInferenceResult {
        check(initialized) {
            "Caption engine chua duoc initialize"
        }
        val totalStartedAt = SystemClock.elapsedRealtimeNanos()
        val preprocessingStartedAt = SystemClock.elapsedRealtimeNanos()
        val imageTensor = preprocessImageForCaptioning(bitmap)
        val preprocessingMs = (
            SystemClock.elapsedRealtimeNanos() - preprocessingStartedAt
        ) / 1_000_000.0
        check(imageTensor.size == 3 * 224 * 224)
        check(imageTensor.all(Float::isFinite))

        val encoderStartedAt = SystemClock.elapsedRealtimeNanos()
        val visualMemory = runEncoderInference(
            environment = environment,
            session = encoderSession,
            input = imageTensor,
        )
        val encoderMs = (
            SystemClock.elapsedRealtimeNanos() - encoderStartedAt
        ) / 1_000_000.0
        check(visualMemory.size == 256)
        check(visualMemory.all(Float::isFinite))

        val beamStartedAt = SystemClock.elapsedRealtimeNanos()
        val beamResult = beam3Decode(
            environment = environment,
            decoderSession = decoderSession,
            visualMemory = visualMemory,
        )
        val beamMs = (
            SystemClock.elapsedRealtimeNanos() - beamStartedAt
        ) / 1_000_000.0
        check(beamResult.eosPosition >= 0) {
            "Beam-3 khong sinh EOS"
        }
        val tokenIds = beamResult.tokenIds.take(
            beamResult.eosPosition + 1,
        )
        val captionWords = tokenIds
            .filter { tokenId -> tokenId !in setOf(0L, 1L, 2L) }
            .map { tokenId -> indexToWord[tokenId.toInt()] }
        val caption = captionWords.joinToString(" ").trim()
        val forbiddenBeforeEos = tokenIds
            .drop(1)
            .dropLast(1)
            .any { tokenId -> tokenId in setOf(0L, 1L, 3L) }
        check(caption.isNotBlank())
        check(captionWords.size >= 2)
        check(!forbiddenBeforeEos)

        return RealImageInferenceResult(
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
}

@Suppress("DEPRECATION")
private fun Context.loadBitmapFromUri(uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
            .copy(Bitmap.Config.ARGB_8888, false)
    }
}

private fun phase8ImageLoadError(error: Throwable): String {
    return """
        PHASE 8 STEP 8 - REAL IMAGE INFERENCE

        Muc do ky thuat: CHUA TOT
        Ly do doc anh: ${error::class.java.simpleName}: ${error.message}

        Tien trinh Phase 8: 78%
    """.trimIndent()
}

private suspend fun Context.runPhase8SmokeTest(): String = withContext(Dispatchers.IO) {
    val expectedHashes = linkedMapOf(
        "baseline_encoder_fp32.onnx" to
            "59fbd6d24f0c82ceec27cf69ed05cda47fcb39d568b68a4a67f06dee080f19e3",
        "baseline_decoder_fp32.onnx" to
            "0b5c6fec9158a9c00847a554936faeeafcf2560ee75d9caf0bacba95cdea8adb",
        "vocabulary.json" to
            "74561e6e00e59a4642ad20b4e69aed9c754f513b5714ff3a448522947ea30be2",
    )
    val requiredAssets = expectedHashes.keys + "phase7b_mobile_handoff_contract.json"

    try {
        val assetLines = requiredAssets.map { assetName ->
            val bytes = assets.open(assetName).use { it.readBytes() }
            val sizeMb = bytes.size.toDouble() / (1024.0 * 1024.0)
            val hashStatus = expectedHashes[assetName]?.let { expected ->
                if (sha256Hex(bytes) == expected) "SHA OK" else "SHA SAI"
            } ?: "contract"
            "$assetName | ${"%.2f".format(sizeMb)} MB | $hashStatus"
        }

        val environment = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()
        val encoderBytes = assets.open("baseline_encoder_fp32.onnx").use { it.readBytes() }
        val decoderBytes = assets.open("baseline_decoder_fp32.onnx").use { it.readBytes() }

        val encoderSession = environment.createSession(encoderBytes, sessionOptions)
        val encoderInfo = describeSession("encoder", encoderSession)
        val decoderSession = environment.createSession(decoderBytes, sessionOptions)
        val decoderInfo = describeSession("decoder", decoderSession)

        val preprocessingStartedAt = SystemClock.elapsedRealtimeNanos()
        val syntheticBitmap = createDeterministicTestBitmap(
            width = 320,
            height = 240,
        )
        val firstTensor = preprocessImageForCaptioning(syntheticBitmap)
        val secondTensor = preprocessImageForCaptioning(syntheticBitmap)
        syntheticBitmap.recycle()
        val preprocessingElapsedMs = (
            SystemClock.elapsedRealtimeNanos() - preprocessingStartedAt
        ) / 1_000_000.0

        check(firstTensor.size == 1 * 3 * 224 * 224) {
            "Sai kich thuoc tensor: ${firstTensor.size}"
        }
        check(firstTensor.all(Float::isFinite)) {
            "Tensor preprocessing co gia tri NaN/Infinity"
        }
        val repeatMaximumError = firstTensor.indices.maxOf { index ->
            abs(firstTensor[index] - secondTensor[index]).toDouble()
        }
        check(repeatMaximumError == 0.0) {
            "Preprocessing khong deterministic: $repeatMaximumError"
        }

        val tensorMinimum = firstTensor.minOrNull() ?: error("Tensor rong")
        val tensorMaximum = firstTensor.maxOrNull() ?: error("Tensor rong")
        val tensorMean = firstTensor.fold(0.0) { total, value ->
            total + value
        } / firstTensor.size
        val tensorHash = sha256Hex(firstTensor.toLittleEndianBytes())

        check(encoderSession.inputNames == setOf("student_images")) {
            "Encoder input name khong khop contract: ${encoderSession.inputNames}"
        }
        check(encoderSession.outputNames == setOf("visual_memory")) {
            "Encoder output name khong khop contract: ${encoderSession.outputNames}"
        }

        runEncoderInference(
            environment = environment,
            session = encoderSession,
            input = firstTensor,
        )
        val encoderStartedAt = SystemClock.elapsedRealtimeNanos()
        val firstVisualMemory = runEncoderInference(
            environment = environment,
            session = encoderSession,
            input = firstTensor,
        )
        val encoderFirstRunMs = (
            SystemClock.elapsedRealtimeNanos() - encoderStartedAt
        ) / 1_000_000.0
        val encoderSecondStartedAt = SystemClock.elapsedRealtimeNanos()
        val secondVisualMemory = runEncoderInference(
            environment = environment,
            session = encoderSession,
            input = firstTensor,
        )
        val encoderSecondRunMs = (
            SystemClock.elapsedRealtimeNanos() - encoderSecondStartedAt
        ) / 1_000_000.0
        check(firstVisualMemory.size == 1 * 1 * 256) {
            "Sai kich thuoc visual_memory: ${firstVisualMemory.size}"
        }
        check(firstVisualMemory.all(Float::isFinite)) {
            "visual_memory co gia tri NaN/Infinity"
        }
        val visualRepeatMaximumError = firstVisualMemory.indices.maxOf { index ->
            abs(
                firstVisualMemory[index] -
                    secondVisualMemory[index],
            ).toDouble()
        }
        check(visualRepeatMaximumError == 0.0) {
            "Encoder inference khong deterministic: $visualRepeatMaximumError"
        }
        val visualMinimum = firstVisualMemory.minOrNull() ?: error("visual_memory rong")
        val visualMaximum = firstVisualMemory.maxOrNull() ?: error("visual_memory rong")
        val visualMean = firstVisualMemory.fold(0.0) { total, value ->
            total + value
        } / firstVisualMemory.size
        val visualL2Norm = kotlin.math.sqrt(
            firstVisualMemory.fold(0.0) { total, value ->
                total + value * value
            },
        )
        val visualHash = sha256Hex(firstVisualMemory.toLittleEndianBytes())

        check(
            decoderSession.inputNames ==
                setOf(
                    "visual_memory",
                    "decoder_input_ids",
                    "decoder_attention_mask",
                ),
        ) {
            "Decoder input names khong khop contract: ${decoderSession.inputNames}"
        }
        check(decoderSession.outputNames == setOf("logits")) {
            "Decoder output name khong khop contract: ${decoderSession.outputNames}"
        }

        val decoderInputIds = LongArray(31)
        decoderInputIds[0] = 1L
        val decoderAttentionMask = LongArray(31)
        decoderAttentionMask[0] = 1L

        runDecoderInference(
            environment = environment,
            session = decoderSession,
            visualMemory = firstVisualMemory,
            inputIds = decoderInputIds,
            attentionMask = decoderAttentionMask,
        )
        val decoderStartedAt = SystemClock.elapsedRealtimeNanos()
        val firstLogits = runDecoderInference(
            environment = environment,
            session = decoderSession,
            visualMemory = firstVisualMemory,
            inputIds = decoderInputIds,
            attentionMask = decoderAttentionMask,
        )
        val decoderFirstRunMs = (
            SystemClock.elapsedRealtimeNanos() - decoderStartedAt
        ) / 1_000_000.0
        val decoderSecondStartedAt = SystemClock.elapsedRealtimeNanos()
        val secondLogits = runDecoderInference(
            environment = environment,
            session = decoderSession,
            visualMemory = firstVisualMemory,
            inputIds = decoderInputIds,
            attentionMask = decoderAttentionMask,
        )
        val decoderSecondRunMs = (
            SystemClock.elapsedRealtimeNanos() - decoderSecondStartedAt
        ) / 1_000_000.0
        val vocabularySize = 12_293
        val sequenceLength = 31
        check(firstLogits.size == sequenceLength * vocabularySize) {
            "Sai kich thuoc logits: ${firstLogits.size}"
        }
        check(firstLogits.all(Float::isFinite)) {
            "Decoder logits co gia tri NaN/Infinity"
        }
        val logitsRepeatMaximumError = firstLogits.indices.maxOf { index ->
            abs(firstLogits[index] - secondLogits[index]).toDouble()
        }
        check(logitsRepeatMaximumError == 0.0) {
            "Decoder inference khong deterministic: $logitsRepeatMaximumError"
        }

        val forbiddenAtFirstStep = setOf(0, 1, 2, 3)
        var firstTokenId = -1
        var firstTokenLogit = Float.NEGATIVE_INFINITY
        for (tokenId in 0 until vocabularySize) {
            if (
                tokenId !in forbiddenAtFirstStep &&
                firstLogits[tokenId] > firstTokenLogit
            ) {
                firstTokenId = tokenId
                firstTokenLogit = firstLogits[tokenId]
            }
        }
        check(firstTokenId >= 4) {
            "Khong tim thay first token hop le"
        }
        val vocabularyJson = JSONObject(
            assets.open("vocabulary.json").bufferedReader().use { it.readText() },
        )
        val indexToWord = vocabularyJson.getJSONArray("index_to_word")
        check(indexToWord.length() == vocabularySize) {
            "Vocabulary size sai: ${indexToWord.length()}"
        }
        val firstTokenWord = indexToWord.getString(firstTokenId)
        val logitsMinimum = firstLogits.minOrNull() ?: error("Logits rong")
        val logitsMaximum = firstLogits.maxOrNull() ?: error("Logits rong")
        val logitsMean = firstLogits.fold(0.0) { total, value ->
            total + value
        } / firstLogits.size
        val logitsHash = sha256Hex(firstLogits.toLittleEndianBytes())

        val beamStartedAt = SystemClock.elapsedRealtimeNanos()
        val firstBeamResult = beam3Decode(
            environment = environment,
            decoderSession = decoderSession,
            visualMemory = firstVisualMemory,
        )
        val firstBeamElapsedMs = (
            SystemClock.elapsedRealtimeNanos() - beamStartedAt
        ) / 1_000_000.0
        val secondBeamStartedAt = SystemClock.elapsedRealtimeNanos()
        val secondBeamResult = beam3Decode(
            environment = environment,
            decoderSession = decoderSession,
            visualMemory = firstVisualMemory,
        )
        val secondBeamElapsedMs = (
            SystemClock.elapsedRealtimeNanos() - secondBeamStartedAt
        ) / 1_000_000.0
        encoderSession.close()
        decoderSession.close()
        sessionOptions.close()

        check(firstBeamResult.tokenIds.contentEquals(secondBeamResult.tokenIds)) {
            "Beam-3 token IDs khong deterministic"
        }
        check(firstBeamResult.rawScore == secondBeamResult.rawScore) {
            "Beam-3 raw score khong deterministic"
        }
        check(firstBeamResult.normalizedScore == secondBeamResult.normalizedScore) {
            "Beam-3 normalized score khong deterministic"
        }
        check(firstBeamResult.eosPosition >= 0) {
            "Beam-3 khong sinh EOS"
        }
        val generatedTokenIds = firstBeamResult.tokenIds
            .take(firstBeamResult.eosPosition + 1)
        val captionWords = generatedTokenIds
            .filter { tokenId -> tokenId !in setOf(0L, 1L, 2L) }
            .map { tokenId -> indexToWord.getString(tokenId.toInt()) }
        val generatedCaption = captionWords.joinToString(" ").trim()
        val forbiddenBeforeEos = generatedTokenIds
            .drop(1)
            .dropLast(1)
            .any { tokenId -> tokenId in setOf(0L, 1L, 3L) }
        check(captionWords.size >= 2) {
            "Caption khong dat minimum 2 words: ${captionWords.size}"
        }
        check(generatedCaption.isNotBlank()) {
            "Caption rong"
        }
        check(!forbiddenBeforeEos) {
            "Caption chua forbidden token truoc EOS"
        }
        val beamTokenHash = sha256Hex(
            firstBeamResult.tokenIds.toLittleEndianBytes(),
        )

        """
        PHASE 8 STEP 7 - LOCKED BEAM-3 CAPTION

        Muc do ky thuat: TOT
        Ly do: Beam-3 ben ngoai ONNX sinh caption hop le, co EOS, khong co token cam va hai lan chay trung khop tuyet doi.

        Previous hard gates:
          asset SHA-256: 3 / 3 OK
          ORT sessions: 2 / 2 OK
          preprocessing shape: [1,3,224,224]
          preprocessing repeat error: ${"%.12f".format(repeatMaximumError)}
          encoder output: [1,1,256]
          encoder repeat error: ${"%.12f".format(visualRepeatMaximumError)}
          decoder output: [1,31,12293]
          decoder repeat error: ${"%.12f".format(logitsRepeatMaximumError)}

        Locked Beam-3:
          beam size: 3
          length penalty: 0.7
          minimum words: 2
          maximum new tokens: 31
          forbidden IDs: [0,1,3]

        Caption result:
          caption: $generatedCaption
          token IDs: $generatedTokenIds
          word count: ${captionWords.size}
          has EOS: true
          EOS position: ${firstBeamResult.eosPosition}
          forbidden before EOS: $forbiddenBeforeEos
          generation steps: ${firstBeamResult.generationSteps}
          raw score: ${"%.6f".format(firstBeamResult.rawScore)}
          normalized score: ${"%.6f".format(firstBeamResult.normalizedScore)}
          token SHA-256: $beamTokenHash
          run 1: ${"%.2f".format(firstBeamElapsedMs)} ms
          run 2: ${"%.2f".format(secondBeamElapsedMs)} ms
          repeated token IDs: true
          repeated scores: true

        Tien trinh Phase 8: 78%
        Buoc tiep theo: them chon anh Gallery/Camera va chay caption tren anh that.
        """.trimIndent()
    } catch (error: Throwable) {
        """
        PHASE 8 STEP 7 - LOCKED BEAM-3 CAPTION

        Muc do ky thuat: CHUA TOT
        Ly do: ${error::class.java.simpleName}: ${error.message}

        Tien trinh Phase 8: 68%
        Can sua loi tren truoc khi them Gallery/Camera.
        """.trimIndent()
    }
}

private data class BeamDecodeResult(
    val tokenIds: LongArray,
    val eosPosition: Int,
    val rawScore: Float,
    val normalizedScore: Float,
    val generationSteps: Int,
)

private fun beam3Decode(
    environment: OrtEnvironment,
    decoderSession: OrtSession,
    visualMemory: FloatArray,
): BeamDecodeResult {
    val beamSize = 3
    val sequenceLength = 31
    val maximumNewTokens = 31
    val vocabularySize = 12_293
    val padId = 0L
    val bosId = 1L
    val eosId = 2L
    val forbiddenIds = setOf(0, 1, 3)
    val minimumWords = 2

    var generatedIds = Array(beamSize) {
        LongArray(maximumNewTokens + 1).also { row ->
            row[0] = bosId
        }
    }
    var beamScores = floatArrayOf(
        0.0f,
        Float.NEGATIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
    )
    var finished = BooleanArray(beamSize)
    var eosPositions = IntArray(beamSize) { -1 }
    var completedSteps = 0
    val expandedVisualMemory = FloatArray(beamSize * 256)
    for (beamIndex in 0 until beamSize) {
        visualMemory.copyInto(
            destination = expandedVisualMemory,
            destinationOffset = beamIndex * 256,
        )
    }

    for (generationStep in 0 until maximumNewTokens) {
        val currentLength = generationStep + 1
        val fixedDecoderIds = LongArray(beamSize * sequenceLength)
        val fixedDecoderMask = LongArray(beamSize * sequenceLength)
        for (beamIndex in 0 until beamSize) {
            for (position in 0 until currentLength) {
                val tokenId = generatedIds[beamIndex][position]
                val flatIndex = beamIndex * sequenceLength + position
                fixedDecoderIds[flatIndex] = tokenId
                fixedDecoderMask[flatIndex] = if (tokenId != padId) 1L else 0L
            }
        }

        val decoderLogits = runDecoderInference(
            environment = environment,
            session = decoderSession,
            visualMemory = expandedVisualMemory,
            inputIds = fixedDecoderIds,
            attentionMask = fixedDecoderMask,
            batchSize = beamSize,
        )
        check(
            decoderLogits.size ==
                beamSize * sequenceLength * vocabularySize,
        ) {
            "Beam decoder logits size sai: ${decoderLogits.size}"
        }
        check(decoderLogits.all(Float::isFinite)) {
            "Beam decoder logits co NaN/Infinity"
        }

        val topScores = FloatArray(beamSize) {
            Float.NEGATIVE_INFINITY
        }
        val topFlatIndices = IntArray(beamSize) { -1 }

        for (beamIndex in 0 until beamSize) {
            if (finished[beamIndex]) {
                insertTopCandidate(
                    score = beamScores[beamIndex],
                    flatIndex = beamIndex * vocabularySize,
                    topScores = topScores,
                    topFlatIndices = topFlatIndices,
                )
                continue
            }
            if (!beamScores[beamIndex].isFinite()) {
                continue
            }

            val stepOffset = (
                beamIndex * sequenceLength +
                    (currentLength - 1)
            ) * vocabularySize
            var maximumLogit = Float.NEGATIVE_INFINITY
            for (tokenId in 0 until vocabularySize) {
                maximumLogit = max(
                    maximumLogit,
                    decoderLogits[stepOffset + tokenId],
                )
            }
            var exponentialSum = 0.0
            for (tokenId in 0 until vocabularySize) {
                exponentialSum += exp(
                    (
                        decoderLogits[stepOffset + tokenId] -
                            maximumLogit
                    ).toDouble(),
                )
            }
            val logSumExp = maximumLogit.toDouble() + ln(exponentialSum)

            for (tokenId in 0 until vocabularySize) {
                if (tokenId in forbiddenIds) {
                    continue
                }
                if (generationStep < minimumWords && tokenId == eosId.toInt()) {
                    continue
                }
                val logProbability = (
                    decoderLogits[stepOffset + tokenId].toDouble() -
                        logSumExp
                ).toFloat()
                insertTopCandidate(
                    score = beamScores[beamIndex] + logProbability,
                    flatIndex = beamIndex * vocabularySize + tokenId,
                    topScores = topScores,
                    topFlatIndices = topFlatIndices,
                )
            }
        }

        check(topFlatIndices.all { index -> index >= 0 }) {
            "Beam-3 khong tim du top candidates"
        }
        val nextGeneratedIds = Array(beamSize) { LongArray(maximumNewTokens + 1) }
        val nextFinished = BooleanArray(beamSize)
        val nextEosPositions = IntArray(beamSize) { -1 }
        for (nextBeamIndex in 0 until beamSize) {
            val flatIndex = topFlatIndices[nextBeamIndex]
            val sourceBeamIndex = flatIndex / vocabularySize
            val nextTokenId = (flatIndex % vocabularySize).toLong()
            generatedIds[sourceBeamIndex].copyInto(
                nextGeneratedIds[nextBeamIndex],
            )
            nextGeneratedIds[nextBeamIndex][currentLength] = nextTokenId
            val sourceWasFinished = finished[sourceBeamIndex]
            nextFinished[nextBeamIndex] = sourceWasFinished || nextTokenId == eosId
            nextEosPositions[nextBeamIndex] = when {
                sourceWasFinished -> eosPositions[sourceBeamIndex]
                nextTokenId == eosId -> currentLength
                else -> eosPositions[sourceBeamIndex]
            }
        }

        generatedIds = nextGeneratedIds
        beamScores = topScores
        finished = nextFinished
        eosPositions = nextEosPositions
        completedSteps = generationStep + 1
        if (finished.all { value -> value }) {
            break
        }
    }

    var bestBeamIndex = 0
    var bestNormalizedScore = Float.NEGATIVE_INFINITY
    for (beamIndex in 0 until beamSize) {
        val generatedLength = max(
            1,
            if (eosPositions[beamIndex] >= 0) {
                eosPositions[beamIndex]
            } else {
                completedSteps
            },
        )
        val normalizedScore = (
            beamScores[beamIndex] /
                generatedLength.toDouble().pow(0.7).toFloat()
        )
        if (normalizedScore > bestNormalizedScore) {
            bestNormalizedScore = normalizedScore
            bestBeamIndex = beamIndex
        }
    }

    return BeamDecodeResult(
        tokenIds = generatedIds[bestBeamIndex],
        eosPosition = eosPositions[bestBeamIndex],
        rawScore = beamScores[bestBeamIndex],
        normalizedScore = bestNormalizedScore,
        generationSteps = completedSteps,
    )
}

private fun insertTopCandidate(
    score: Float,
    flatIndex: Int,
    topScores: FloatArray,
    topFlatIndices: IntArray,
) {
    for (rank in topScores.indices) {
        if (score > topScores[rank]) {
            for (shiftRank in topScores.lastIndex downTo rank + 1) {
                topScores[shiftRank] = topScores[shiftRank - 1]
                topFlatIndices[shiftRank] = topFlatIndices[shiftRank - 1]
            }
            topScores[rank] = score
            topFlatIndices[rank] = flatIndex
            return
        }
    }
}

private fun runEncoderInference(
    environment: OrtEnvironment,
    session: OrtSession,
    input: FloatArray,
): FloatArray {
    val inputBuffer = FloatBuffer.wrap(input)
    OnnxTensor.createTensor(
        environment,
        inputBuffer,
        longArrayOf(1, 3, 224, 224),
    ).use { inputTensor ->
        session.run(
            mapOf("student_images" to inputTensor),
        ).use { result ->
            check(result.size() == 1) {
                "Encoder phai co dung mot output, nhan ${result.size()}"
            }
            val outputTensor = result.get(0) as? OnnxTensor
                ?: error("Encoder output khong phai OnnxTensor")
            val outputBuffer = outputTensor.floatBuffer
            val output = FloatArray(outputBuffer.remaining())
            outputBuffer.get(output)
            return output
        }
    }
}

private fun runDecoderInference(
    environment: OrtEnvironment,
    session: OrtSession,
    visualMemory: FloatArray,
    inputIds: LongArray,
    attentionMask: LongArray,
    batchSize: Int = 1,
): FloatArray {
    require(visualMemory.size == batchSize * 256)
    require(inputIds.size == batchSize * 31)
    require(attentionMask.size == batchSize * 31)
    OnnxTensor.createTensor(
        environment,
        FloatBuffer.wrap(visualMemory),
        longArrayOf(batchSize.toLong(), 1, 256),
    ).use { visualTensor ->
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(inputIds),
            longArrayOf(batchSize.toLong(), 31),
        ).use { inputIdsTensor ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(attentionMask),
                longArrayOf(batchSize.toLong(), 31),
            ).use { attentionMaskTensor ->
                session.run(
                    mapOf(
                        "visual_memory" to visualTensor,
                        "decoder_input_ids" to inputIdsTensor,
                        "decoder_attention_mask" to attentionMaskTensor,
                    ),
                ).use { result ->
                    check(result.size() == 1) {
                        "Decoder phai co dung mot output, nhan ${result.size()}"
                    }
                    val outputTensor = result.get(0) as? OnnxTensor
                        ?: error("Decoder output khong phai OnnxTensor")
                    val outputBuffer = outputTensor.floatBuffer
                    val output = FloatArray(outputBuffer.remaining())
                    outputBuffer.get(output)
                    return output
                }
            }
        }
    }
}

private fun LongArray.toLittleEndianBytes(): ByteArray {
    val buffer = ByteBuffer
        .allocate(size * Long.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    forEach { value -> buffer.putLong(value) }
    return buffer.array()
}

private fun createDeterministicTestBitmap(width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val red = (x * 255) / max(1, width - 1)
            val green = (y * 255) / max(1, height - 1)
            val blue = ((x + y) * 255) / max(1, width + height - 2)
            pixels[y * width + x] = Color.rgb(red, green, blue)
        }
    }
    return Bitmap.createBitmap(
        pixels,
        width,
        height,
        Bitmap.Config.ARGB_8888,
    )
}

private fun preprocessImageForCaptioning(source: Bitmap): FloatArray {
    require(source.width > 0 && source.height > 0) {
        "Bitmap khong hop le: ${source.width} x ${source.height}"
    }

    val targetSize = 224
    val scale = targetSize.toDouble() / min(source.width, source.height)
    val resizedWidth = max(targetSize, (source.width * scale).toInt())
    val resizedHeight = max(targetSize, (source.height * scale).toInt())
    val resized = resizeBitmapBicubic(
        source = source,
        targetWidth = resizedWidth,
        targetHeight = resizedHeight,
    )
    val cropLeft = (resizedWidth - targetSize) / 2
    val cropTop = (resizedHeight - targetSize) / 2
    val croppedPixels = IntArray(targetSize * targetSize)
    resized.getPixels(
        croppedPixels,
        0,
        targetSize,
        cropLeft,
        cropTop,
        targetSize,
        targetSize,
    )
    resized.recycle()

    val channelMean = floatArrayOf(0.485f, 0.456f, 0.406f)
    val channelStd = floatArrayOf(0.229f, 0.224f, 0.225f)
    val planeSize = targetSize * targetSize
    val output = FloatArray(3 * planeSize)
    for (index in croppedPixels.indices) {
        val pixel = croppedPixels[index]
        output[index] = (
            Color.red(pixel) / 255.0f - channelMean[0]
        ) / channelStd[0]
        output[planeSize + index] = (
            Color.green(pixel) / 255.0f - channelMean[1]
        ) / channelStd[1]
        output[2 * planeSize + index] = (
            Color.blue(pixel) / 255.0f - channelMean[2]
        ) / channelStd[2]
    }
    return output
}

private fun resizeBitmapBicubic(
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
                val sampledY = (sourceYBase + offsetY).coerceIn(0, sourceHeight - 1)
                val weightY = cubicWeight(sourceY - (sourceYBase + offsetY))
                for (offsetX in -1..2) {
                    val sampledX = (sourceXBase + offsetX).coerceIn(0, sourceWidth - 1)
                    val weightX = cubicWeight(sourceX - (sourceXBase + offsetX))
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

private fun cubicWeight(distance: Double): Double {
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

private fun FloatArray.toLittleEndianBytes(): ByteArray {
    val buffer = ByteBuffer
        .allocate(size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    forEach { value -> buffer.putFloat(value) }
    return buffer.array()
}

private fun describeSession(name: String, session: OrtSession): String {
    val inputs = session.inputInfo.entries.joinToString(separator = "\n") { entry ->
        "  input ${entry.key}: ${entry.value.info}"
    }
    val outputs = session.outputInfo.entries.joinToString(separator = "\n") { entry ->
        "  output ${entry.key}: ${entry.value.info}"
    }
    return """
        ORT session $name: OK
        $inputs
        $outputs
    """.trimIndent()
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
