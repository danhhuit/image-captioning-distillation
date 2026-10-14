package com.klcn.mobilecaptioning

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.speech.tts.TextToSpeech
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * English captions are translated locally after ML Kit downloads its EN/VI
 * language packs once. No source image is sent to the translation service.
 */
internal suspend fun translateCaptionToVietnamese(caption: String): String {
    val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.VIETNAMESE)
        .build()
    val translator = Translation.getClient(options)
    return try {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        translator.translate(caption).await()
    } finally {
        translator.close()
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

internal class CaptionSpeaker(context: Context) {
    private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, language: Locale): Boolean {
        if (!ready || text.isBlank()) return false
        val availability = engine.isLanguageAvailable(language)
        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return false
        }
        engine.language = language
        engine.setSpeechRate(0.92f)
        return engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "vision-caption-${UUID.randomUUID()}",
        ) == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}

internal fun Context.createCaptionShareIntent(
    source: Bitmap,
    englishCaption: String,
    vietnameseCaption: String?,
): Intent {
    val maxWidth = 1_440
    val scale = minOf(1f, maxWidth.toFloat() / source.width.coerceAtLeast(1))
    val imageWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val imageHeight = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (imageWidth == source.width && imageHeight == source.height) {
        source
    } else {
        Bitmap.createScaledBitmap(source, imageWidth, imageHeight, true)
    }

    val horizontalPadding = (imageWidth * 0.055f).toInt().coerceAtLeast(28)
    val textWidth = imageWidth - horizontalPadding * 2
    val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(37, 99, 235)
        textSize = (imageWidth * 0.032f).coerceIn(28f, 48f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 27, 45)
        textSize = (imageWidth * 0.040f).coerceIn(34f, 58f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(68, 75, 92)
        textSize = (imageWidth * 0.033f).coerceIn(29f, 49f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val brand = staticText("VISION CAPTION", headingPaint, textWidth)
    val english = staticText(englishCaption, captionPaint, textWidth)
    val vietnamese = vietnameseCaption
        ?.takeIf(String::isNotBlank)
        ?.let { staticText(it, translationPaint, textWidth) }
    val spacing = (imageWidth * 0.025f).toInt().coerceAtLeast(18)
    val panelHeight = horizontalPadding +
        brand.height + spacing +
        english.height +
        (if (vietnamese != null) spacing + vietnamese.height else 0) +
        horizontalPadding

    val sharedBitmap = Bitmap.createBitmap(
        imageWidth,
        imageHeight + panelHeight,
        Bitmap.Config.ARGB_8888,
    )
    Canvas(sharedBitmap).apply {
        drawColor(Color.WHITE)
        drawBitmap(scaled, 0f, 0f, null)
        drawRect(
            0f,
            imageHeight.toFloat(),
            imageWidth.toFloat(),
            (imageHeight + panelHeight).toFloat(),
            Paint().apply { color = Color.WHITE },
        )
        var y = imageHeight + horizontalPadding
        save()
        translate(horizontalPadding.toFloat(), y.toFloat())
        brand.draw(this)
        restore()
        y += brand.height + spacing
        save()
        translate(horizontalPadding.toFloat(), y.toFloat())
        english.draw(this)
        restore()
        if (vietnamese != null) {
            y += english.height + spacing
            save()
            translate(horizontalPadding.toFloat(), y.toFloat())
            vietnamese.draw(this)
            restore()
        }
    }

    val directory = File(cacheDir, "shared_captions").apply { mkdirs() }
    val output = File(directory, "vision_caption_${System.currentTimeMillis()}.jpg")
    FileOutputStream(output).use { stream ->
        check(sharedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
            "Unable to encode shared caption image"
        }
    }
    if (scaled !== source) scaled.recycle()
    sharedBitmap.recycle()

    val uri = FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        output,
    )
    val shareText = buildString {
        append(englishCaption)
        vietnameseCaption?.takeIf(String::isNotBlank)?.let {
            append("\n\n")
            append(it)
        }
        append("\n\n— Vision Caption")
    }
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, shareText)
        clipData = ClipData.newUri(contentResolver, "Vision Caption", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun staticText(
    text: String,
    paint: TextPaint,
    width: Int,
): StaticLayout = StaticLayout.Builder
    .obtain(text, 0, text.length, paint, width)
    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
    .setIncludePad(false)
    .setLineSpacing(0f, 1.12f)
    .build()
