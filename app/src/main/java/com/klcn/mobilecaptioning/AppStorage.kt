package com.klcn.mobilecaptioning

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max

internal enum class AppThemeMode {
    System,
    Light,
    Dark,
}

internal enum class AppLanguage {
    Vietnamese,
    English,
}

internal data class CaptionHistoryItem(
    val id: String,
    val caption: String,
    val createdAtMillis: Long,
    val source: ImageInputSource,
    val thumbnailPath: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val tokenIds: List<Long>,
    val wordCount: Int,
    val eosPosition: Int,
    val rawScore: Float,
    val normalizedScore: Float,
    val modelLoadMs: Double,
    val preprocessingMs: Double,
    val encoderMs: Double,
    val beamMs: Double,
    val firstTotalMs: Double,
    val repeatedTotalMs: Double,
    val repeatabilityVerified: Boolean,
    val latencyMs: Double,
    val favorite: Boolean,
)

internal object AppPreferences {
    private const val PreferencesName = "vision_caption_preferences"
    private const val ThemeKey = "theme"
    private const val LanguageKey = "language"

    fun loadTheme(context: Context): AppThemeMode {
        val stored = context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).getString(ThemeKey, AppThemeMode.System.name)
        return runCatching {
            AppThemeMode.valueOf(stored ?: AppThemeMode.System.name)
        }.getOrDefault(AppThemeMode.System)
    }

    fun saveTheme(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).edit().putString(ThemeKey, mode.name).apply()
    }

    fun loadLanguage(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).getString(LanguageKey, AppLanguage.Vietnamese.name)
        return runCatching {
            AppLanguage.valueOf(stored ?: AppLanguage.Vietnamese.name)
        }.getOrDefault(AppLanguage.Vietnamese)
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).edit().putString(LanguageKey, language.name).apply()
    }
}

internal object CaptionHistoryStore {
    private const val PreferencesName = "vision_caption_history"
    private const val HistoryKey = "items"
    private const val MaximumHistoryItems = 100

    fun load(context: Context): List<CaptionHistoryItem> {
        val raw = context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).getString(HistoryKey, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val parsed = runCatching {
                    CaptionHistoryItem(
                        id = item.getString("id"),
                        caption = item.getString("caption"),
                        createdAtMillis = item.getLong("created_at"),
                        source = ImageInputSource.valueOf(
                            item.getString("source"),
                        ),
                        thumbnailPath = item.getString("thumbnail_path"),
                        sourceWidth = item.optInt("source_width", 0),
                        sourceHeight = item.optInt("source_height", 0),
                        tokenIds = item.optJSONArray("token_ids")
                            ?.let(::jsonLongList)
                            ?: emptyList(),
                        wordCount = item.getInt("word_count"),
                        eosPosition = item.optInt("eos_position", -1),
                        rawScore = item.optDouble("raw_score", 0.0)
                            .toFloat(),
                        normalizedScore = item.optDouble(
                            "normalized_score",
                            0.0,
                        ).toFloat(),
                        modelLoadMs = item.optDouble("model_load_ms", 0.0),
                        preprocessingMs = item.optDouble(
                            "preprocessing_ms",
                            0.0,
                        ),
                        encoderMs = item.optDouble("encoder_ms", 0.0),
                        beamMs = item.optDouble("beam_ms", 0.0),
                        firstTotalMs = item.optDouble(
                            "first_total_ms",
                            0.0,
                        ),
                        repeatedTotalMs = item.optDouble(
                            "repeated_total_ms",
                            item.optDouble("latency_ms", 0.0),
                        ),
                        repeatabilityVerified = item.optBoolean(
                            "repeatability_verified",
                            false,
                        ),
                        latencyMs = item.getDouble("latency_ms"),
                        favorite = item.optBoolean("favorite", false),
                    )
                }.getOrNull()
                if (parsed != null) {
                    add(parsed)
                }
            }
        }.sortedByDescending(CaptionHistoryItem::createdAtMillis)
    }

    fun add(
        context: Context,
        result: CaptionPresentation,
        bitmap: Bitmap,
    ): CaptionHistoryItem {
        val id = UUID.randomUUID().toString()
        val thumbnailPath = saveThumbnail(
            context = context,
            id = id,
            bitmap = bitmap,
        )
        val item = CaptionHistoryItem(
            id = id,
            caption = result.caption,
            createdAtMillis = System.currentTimeMillis(),
            source = result.source,
            thumbnailPath = thumbnailPath,
            sourceWidth = result.sourceWidth,
            sourceHeight = result.sourceHeight,
            tokenIds = result.tokenIds,
            wordCount = result.wordCount,
            eosPosition = result.eosPosition,
            rawScore = result.rawScore,
            normalizedScore = result.normalizedScore,
            modelLoadMs = result.modelLoadMs,
            preprocessingMs = result.preprocessingMs,
            encoderMs = result.encoderMs,
            beamMs = result.beamMs,
            firstTotalMs = result.firstTotalMs,
            repeatedTotalMs = result.repeatedTotalMs,
            repeatabilityVerified = result.repeatabilityVerified,
            latencyMs = result.repeatedTotalMs,
            favorite = false,
        )
        val updated = listOf(item) + load(context)
        val retained = updated.take(MaximumHistoryItems)
        updated.drop(MaximumHistoryItems).forEach { removed ->
            File(removed.thumbnailPath).delete()
        }
        save(context, retained)
        return item
    }

    fun toggleFavorite(
        context: Context,
        id: String,
    ): List<CaptionHistoryItem> {
        val updated = load(context).map { item ->
            if (item.id == id) {
                item.copy(favorite = !item.favorite)
            } else {
                item
            }
        }
        save(context, updated)
        return updated
    }

    fun delete(
        context: Context,
        id: String,
    ): List<CaptionHistoryItem> {
        val current = load(context)
        current.firstOrNull { it.id == id }?.let { item ->
            File(item.thumbnailPath).delete()
        }
        val updated = current.filterNot { it.id == id }
        save(context, updated)
        return updated
    }

    fun clear(context: Context) {
        load(context).forEach { item ->
            File(item.thumbnailPath).delete()
        }
        save(context, emptyList())
    }

    private fun save(
        context: Context,
        items: List<CaptionHistoryItem>,
    ) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("caption", item.caption)
                    put("created_at", item.createdAtMillis)
                    put("source", item.source.name)
                    put("thumbnail_path", item.thumbnailPath)
                    put("source_width", item.sourceWidth)
                    put("source_height", item.sourceHeight)
                    put(
                        "token_ids",
                        JSONArray().apply {
                            item.tokenIds.forEach { tokenId ->
                                put(tokenId)
                            }
                        },
                    )
                    put("word_count", item.wordCount)
                    put("eos_position", item.eosPosition)
                    put("raw_score", item.rawScore.toDouble())
                    put(
                        "normalized_score",
                        item.normalizedScore.toDouble(),
                    )
                    put("model_load_ms", item.modelLoadMs)
                    put("preprocessing_ms", item.preprocessingMs)
                    put("encoder_ms", item.encoderMs)
                    put("beam_ms", item.beamMs)
                    put("first_total_ms", item.firstTotalMs)
                    put("repeated_total_ms", item.repeatedTotalMs)
                    put(
                        "repeatability_verified",
                        item.repeatabilityVerified,
                    )
                    put("latency_ms", item.latencyMs)
                    put("favorite", item.favorite)
                },
            )
        }
        context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        ).edit().putString(HistoryKey, array.toString()).apply()
    }

    private fun saveThumbnail(
        context: Context,
        id: String,
        bitmap: Bitmap,
    ): String {
        val directory = File(
            context.filesDir,
            "caption_history_thumbnails",
        ).apply { mkdirs() }
        val longestSide = max(bitmap.width, bitmap.height)
        val scale = if (longestSide > 480) {
            480.0 / longestSide
        } else {
            1.0
        }
        val targetWidth = max(1, (bitmap.width * scale).toInt())
        val targetHeight = max(1, (bitmap.height * scale).toInt())
        val thumbnail = Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            targetHeight,
            true,
        )
        val destination = File(directory, "$id.jpg")
        destination.outputStream().use { output ->
            check(
                thumbnail.compress(
                    Bitmap.CompressFormat.JPEG,
                    86,
                    output,
                ),
            ) {
                "Không thể lưu thumbnail lịch sử"
            }
        }
        if (thumbnail !== bitmap) {
            thumbnail.recycle()
        }
        return destination.absolutePath
    }

    private fun jsonLongList(array: JSONArray): List<Long> =
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.optLong(index))
            }
        }
}
