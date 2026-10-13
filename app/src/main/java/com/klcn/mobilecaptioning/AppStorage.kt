package com.klcn.mobilecaptioning

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
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
    val modelLabel: String,
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
    fun loadTheme(context: Context, ownerId: String): AppThemeMode {
        migrateLegacyPreferences(context, ownerId)
        val stored = VisionDatabase.get(context).loadPreference(ownerId, "theme")
        return runCatching {
            AppThemeMode.valueOf(stored ?: AppThemeMode.System.name)
        }.getOrDefault(AppThemeMode.System)
    }

    fun saveTheme(context: Context, ownerId: String, mode: AppThemeMode) {
        val database = VisionDatabase.get(context)
        database.savePreference(ownerId, "theme", mode.name)
        enqueuePreferences(context, ownerId)
    }

    fun loadLanguage(context: Context, ownerId: String): AppLanguage {
        migrateLegacyPreferences(context, ownerId)
        val stored = VisionDatabase.get(context).loadPreference(ownerId, "language")
        return runCatching {
            AppLanguage.valueOf(stored ?: AppLanguage.Vietnamese.name)
        }.getOrDefault(AppLanguage.Vietnamese)
    }

    fun saveLanguage(context: Context, ownerId: String, language: AppLanguage) {
        val database = VisionDatabase.get(context)
        database.savePreference(ownerId, "language", language.name)
        enqueuePreferences(context, ownerId)
    }

    fun loadModel(context: Context, ownerId: String): CaptionModelMode {
        val stored = VisionDatabase.get(context).loadPreference(ownerId, "selected_model")
        return CaptionModelMode.entries.firstOrNull { it.name == stored }
            ?: CaptionModelMode.V1_FALLBACK
    }

    fun saveModel(context: Context, ownerId: String, model: CaptionModelMode) {
        val database = VisionDatabase.get(context)
        database.savePreference(ownerId, "selected_model", model.name)
        enqueuePreferences(context, ownerId)
    }

    private fun enqueuePreferences(context: Context, ownerId: String) {
        if (ownerId == VisionDatabase.GuestUserId) return
        val database = VisionDatabase.get(context)
        database.enqueueSync(
            ownerId = ownerId,
            entityType = "preferences",
            entityId = ownerId,
            operation = "UPSERT",
            payloadJson = JSONObject()
                .put("theme", database.loadPreference(ownerId, "theme") ?: AppThemeMode.System.name)
                .put("language", database.loadPreference(ownerId, "language") ?: AppLanguage.Vietnamese.name)
                .put("selected_model", database.loadPreference(ownerId, "selected_model") ?: CaptionModelMode.V1_FALLBACK.name)
                .toString(),
        )
    }

    private fun migrateLegacyPreferences(context: Context, ownerId: String) {
        val legacy = context.getSharedPreferences(
            "vision_caption_preferences",
            Context.MODE_PRIVATE,
        )
        if (legacy.getBoolean("sqlite_v1_migrated", false)) return
        val database = VisionDatabase.get(context)
        legacy.getString("theme", null)?.let {
            database.savePreference(ownerId, "theme", it)
        }
        legacy.getString("language", null)?.let {
            database.savePreference(ownerId, "language", it)
        }
        legacy.edit().putBoolean("sqlite_v1_migrated", true).apply()
    }
}

internal object CaptionHistoryStore {
    private const val PreferencesName = "vision_caption_history"
    private const val HistoryKey = "items"
    private const val MigrationKey = "sqlite_v1_migrated"

    fun load(context: Context, ownerId: String): List<CaptionHistoryItem> {
        migrateLegacyHistory(context)
        return VisionDatabase.get(context).loadCaptions(ownerId)
    }

    fun enqueueExistingForSync(context: Context, ownerId: String) {
        val database = VisionDatabase.get(context)
        val migrationKey = "central_history_enqueued_$ownerId"
        if (database.loadState(migrationKey) == "1") return
        database.loadCaptions(ownerId).forEach { item ->
            val thumbnail = File(item.thumbnailPath)
            if (thumbnail.isFile) {
                database.enqueueSync(
                    ownerId,
                    "media",
                    item.id,
                    "UPSERT",
                    JSONObject()
                        .put("media_id", item.id)
                        .put("mime_type", "image/jpeg")
                        .put(
                            "content_base64",
                            Base64.encodeToString(thumbnail.readBytes(), Base64.NO_WRAP),
                        ).toString(),
                )
            }
            val payload = item.toCentralJson()
            if (!thumbnail.isFile) payload.put("thumbnail_object_key", JSONObject.NULL)
            database.enqueueSync(ownerId, "caption", item.id, "UPSERT", payload.toString())
        }
        database.saveState(migrationKey, "1")
    }

    private fun loadLegacy(context: Context): List<CaptionHistoryItem> {
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
                        modelLabel = item.optString(
                            "model_label",
                            CaptionModelMode.V1_FALLBACK.displayName,
                        ),
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
        ownerId: String,
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
            modelLabel = result.modelLabel,
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
        val database = VisionDatabase.get(context)
        val before = database.loadCaptions(ownerId)
        database.insertCaption(ownerId, item)
        if (ownerId != VisionDatabase.GuestUserId) {
            database.enqueueSync(
                ownerId = ownerId,
                entityType = "media",
                entityId = item.id,
                operation = "UPSERT",
                payloadJson = JSONObject()
                    .put("media_id", item.id)
                    .put("mime_type", "image/jpeg")
                    .put(
                        "content_base64",
                        Base64.encodeToString(
                            File(item.thumbnailPath).readBytes(),
                            Base64.NO_WRAP,
                        ),
                    )
                    .toString(),
            )
            database.enqueueSync(
                ownerId = ownerId,
                entityType = "caption",
                entityId = item.id,
                operation = "UPSERT",
                payloadJson = item.toCentralJson().toString(),
            )
        }
        val retainedIds = database.loadCaptions(ownerId).map(CaptionHistoryItem::id).toSet()
        before.filter { it.id !in retainedIds }.forEach { removed ->
            File(removed.thumbnailPath).delete()
        }
        return item
    }

    fun toggleFavorite(
        context: Context,
        ownerId: String,
        id: String,
    ): List<CaptionHistoryItem> {
        val database = VisionDatabase.get(context)
        database.toggleFavorite(ownerId, id)
        val items = database.loadCaptions(ownerId)
        if (ownerId != VisionDatabase.GuestUserId) {
            items.firstOrNull { it.id == id }?.let { item ->
                database.enqueueSync(
                    ownerId,
                    "caption",
                    id,
                    "FAVORITE",
                    JSONObject().put("favorite", item.favorite).toString(),
                )
            }
        }
        return items
    }

    fun delete(
        context: Context,
        ownerId: String,
        id: String,
    ): List<CaptionHistoryItem> {
        val database = VisionDatabase.get(context)
        val current = database.loadCaptions(ownerId)
        current.firstOrNull { it.id == id }?.let { item ->
            File(item.thumbnailPath).delete()
        }
        database.deleteCaption(ownerId, id)
        if (ownerId != VisionDatabase.GuestUserId) {
            database.enqueueSync(ownerId, "caption", id, "DELETE", "{}")
        }
        return database.loadCaptions(ownerId)
    }

    fun clear(context: Context, ownerId: String) {
        val database = VisionDatabase.get(context)
        database.loadCaptions(ownerId).forEach { item ->
            File(item.thumbnailPath).delete()
            if (ownerId != VisionDatabase.GuestUserId) {
                database.enqueueSync(ownerId, "caption", item.id, "DELETE", "{}")
            }
        }
        database.clearCaptions(ownerId)
    }

    private fun CaptionHistoryItem.toCentralJson(): JSONObject = JSONObject()
        .put("client_caption_id", id)
        .put("caption", caption)
        .put("model_key", modelLabel)
        .put("source", source.name)
        .put("favorite", favorite)
        .put("thumbnail_object_key", id)
        .put("created_at", java.time.Instant.ofEpochMilli(createdAtMillis).toString())
        .put(
            "metrics",
            JSONObject()
                .put("source_width", sourceWidth)
                .put("source_height", sourceHeight)
                .put("token_ids", JSONArray(tokenIds))
                .put("word_count", wordCount)
                .put("eos_position", eosPosition)
                .put("raw_score", rawScore)
                .put("normalized_score", normalizedScore)
                .put("model_load_ms", modelLoadMs)
                .put("preprocessing_ms", preprocessingMs)
                .put("encoder_ms", encoderMs)
                .put("beam_ms", beamMs)
                .put("first_total_ms", firstTotalMs)
                .put("repeated_total_ms", repeatedTotalMs)
                .put("repeatability_verified", repeatabilityVerified),
        )

    private fun migrateLegacyHistory(context: Context) {
        val preferences = context.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        )
        if (preferences.getBoolean(MigrationKey, false)) return
        val database = VisionDatabase.get(context)
        loadLegacy(context).forEach { item ->
            runCatching { database.insertCaption(VisionDatabase.GuestUserId, item) }
        }
        preferences.edit().putBoolean(MigrationKey, true).apply()
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
