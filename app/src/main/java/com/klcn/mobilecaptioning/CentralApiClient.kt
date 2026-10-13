package com.klcn.mobilecaptioning

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class CentralSession(
    val accessToken: String,
    val account: UserAccount,
)

internal class CentralApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)

internal class CentralApiClient(context: Context) {
    private val baseUrl = context.getString(R.string.central_api_base_url).trimEnd('/')

    suspend fun login(identifier: String, password: String): CentralSession =
        post(
            "/v1/auth/login",
            JSONObject()
                .put("identifier", identifier)
                .put("password", password),
        ).toCentralSession()

    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): CentralSession = post(
        "/v1/auth/register",
        JSONObject()
            .put("display_name", displayName)
            .put("username", username)
            .put("email", email)
            .put("password", password),
    ).toCentralSession()

    suspend fun loginGoogle(idToken: String, nonce: String): CentralSession = post(
        "/v1/auth/google",
        JSONObject().put("id_token", idToken).put("nonce", nonce),
    ).toCentralSession()

    suspend fun currentAccount(accessToken: String): UserAccount = request(
        "GET", "/v1/me", null, accessToken,
    ).toAccount()

    suspend fun logout(accessToken: String) {
        request("POST", "/v1/auth/logout", null, accessToken)
    }

    suspend fun updatePermissions(
        userId: String,
        permissions: Set<String>,
        accessToken: String,
    ) {
        request(
            "PUT",
            "/v1/admin/users/$userId/permissions",
            JSONObject().put("permissions", org.json.JSONArray(permissions.toList())),
            accessToken,
        )
    }

    suspend fun setUserActive(
        userId: String,
        active: Boolean,
        accessToken: String,
    ) {
        request(
            "PATCH",
            "/v1/admin/users/$userId/status",
            JSONObject().put("active", active),
            accessToken,
        )
    }

    suspend fun updateUserProfile(
        userId: String,
        displayName: String,
        password: String?,
        accessToken: String,
    ): UserAccount {
        val body = JSONObject().put("display_name", displayName)
        if (!password.isNullOrBlank()) body.put("password", password)
        return request(
            "PATCH",
            "/v1/admin/users/$userId/profile",
            body,
            accessToken,
        ).toAccount()
    }

    suspend fun listAdminUsers(accessToken: String): List<UserAccount> {
        return buildList {
            var page = 1
            var total = Int.MAX_VALUE
            do {
                val response = request(
                    "GET", "/v1/admin/users?page=$page&page_size=100", null, accessToken,
                )
                val items = response.getJSONArray("items")
                total = response.getInt("total")
                for (index in 0 until items.length()) add(items.getJSONObject(index).toAccount())
                page++
            } while (size < total)
        }
    }

    suspend fun listCaptions(accessToken: String): List<JSONObject> {
        val response = request("GET", "/v1/captions?page=1&page_size=100", null, accessToken)
        val items = response.getJSONArray("items")
        return buildList {
            for (index in 0 until items.length()) add(items.getJSONObject(index))
        }
    }

    suspend fun getPreferences(accessToken: String): JSONObject = request(
        "GET", "/v1/preferences", null, accessToken,
    )

    suspend fun downloadMedia(mediaId: String, accessToken: String): ByteArray {
        val response = request("GET", "/v1/media/$mediaId", null, accessToken)
        return Base64.decode(response.getString("content_base64"), Base64.DEFAULT)
    }

    suspend fun listAdminActivities(accessToken: String): List<UserActivityEvent> {
        return buildList {
            var page = 1
            var total = Int.MAX_VALUE
            do {
                val response = request(
                    "GET", "/v1/admin/activities?page=$page&page_size=100", null, accessToken,
                )
                val items = response.getJSONArray("items")
                total = response.getInt("total")
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(
                        UserActivityEvent(
                            id = item.get("id").toString(),
                            userId = item.optString("user_id"),
                            username = item.getString("username_snapshot"),
                            action = item.getString("action"),
                            detail = item.optString("detail"),
                            occurredAtMillis = java.time.Instant
                                .parse(item.getString("occurred_at"))
                                .toEpochMilli(),
                        ),
                    )
                }
                page++
            } while (size < total)
        }
    }

    suspend fun pushOutbox(item: SyncOutboxItem, accessToken: String) {
        val payload = JSONObject(item.payloadJson).put("operation_id", item.operationId)
        when {
            item.entityType == "media" ->
                post("/v1/media", payload, accessToken)
            item.entityType == "caption" && item.operation == "UPSERT" ->
                post("/v1/captions", payload, accessToken)
            item.entityType == "caption" && item.operation == "FAVORITE" ->
                request(
                    "PATCH",
                    "/v1/captions/${item.entityId}/favorite",
                    payload,
                    accessToken,
                )
            item.entityType == "caption" && item.operation == "DELETE" ->
                request(
                    "DELETE",
                    "/v1/captions/${item.entityId}",
                    null,
                    accessToken,
                )
            item.entityType == "preferences" ->
                request("PUT", "/v1/preferences", payload, accessToken)
            item.entityType == "activity" ->
                post(
                    "/v1/activities",
                    payload
                        .remove("operation_id")
                        .let { payload.put("client_event_id", item.operationId) },
                    accessToken,
                )
            else -> throw CentralApiException(422, "Unsupported offline operation")
        }
    }

    private suspend fun post(
        path: String,
        body: JSONObject,
        token: String? = null,
    ): JSONObject = request("POST", path, body, token)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    writer -> writer.write(body.toString())
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("detail") }
                    .getOrNull().orEmpty().ifBlank { "Central API error $status" }
                throw CentralApiException(status, detail)
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toCentralSession(): CentralSession {
        val user = getJSONObject("user")
        return CentralSession(
            accessToken = getString("access_token"),
            account = user.toAccount(),
        )
    }

    private fun JSONObject.toAccount(): UserAccount {
        val permissionsJson = optJSONArray("permissions")
        val permissions = buildSet {
            if (permissionsJson != null) {
                for (index in 0 until permissionsJson.length()) {
                    add(permissionsJson.getString(index))
                }
            }
        }
        return UserAccount(
            id = getString("id"),
            displayName = getString("display_name"),
            username = getString("username"),
            email = optString("email").takeIf { it.isNotBlank() },
            passwordHash = null,
            passwordSalt = null,
            provider = AuthProvider.valueOf(getString("provider")),
            role = UserRole.valueOf(getString("role")),
            permissions = permissions,
            active = optBoolean("active", true),
        )
    }
}

internal class CentralSyncRepository(context: Context) {
    private val database = VisionDatabase.get(context)
    private val api = CentralApiClient(context)
    private val appContext = context.applicationContext

    suspend fun refreshCaptionCache(ownerId: String): Int = withContext(Dispatchers.IO) {
        val token = database.loadState(AuthStore.CentralTokenKey) ?: return@withContext 0
        if (api.currentAccount(token).id != ownerId) return@withContext 0
        if (database.pendingSync(ownerId).isNotEmpty()) return@withContext 0
        val remote = api.listCaptions(token)
        val cached = database.loadCaptions(ownerId).associateBy(CaptionHistoryItem::id)
        val thumbnailDirectory = File(appContext.filesDir, "caption_history_thumbnails")
            .apply { mkdirs() }
        for (row in remote) {
            val id = row.getString("client_caption_id")
            val existing = cached[id]
            val mediaId = row.optString("thumbnail_media_id")
            val thumbnail = File(thumbnailDirectory, "$id.jpg")
            if (!thumbnail.isFile && mediaId.isNotBlank() && mediaId != "null") {
                runCatching { thumbnail.writeBytes(api.downloadMedia(mediaId, token)) }
            }
            val metrics = row.optJSONObject("metrics") ?: JSONObject()
            val source = runCatching {
                ImageInputSource.valueOf(row.getString("source"))
            }.getOrDefault(ImageInputSource.Gallery)
            val item = CaptionHistoryItem(
                id = id,
                caption = row.getString("caption"),
                modelLabel = row.getString("model_key"),
                createdAtMillis = java.time.Instant.parse(row.getString("created_at"))
                    .toEpochMilli(),
                source = source,
                thumbnailPath = if (thumbnail.isFile) thumbnail.absolutePath else existing?.thumbnailPath.orEmpty(),
                sourceWidth = metrics.optInt("source_width"),
                sourceHeight = metrics.optInt("source_height"),
                tokenIds = metrics.optJSONArray("token_ids")?.let { ids ->
                    (0 until ids.length()).map(ids::getLong)
                } ?: emptyList(),
                wordCount = metrics.optInt("word_count"),
                eosPosition = metrics.optInt("eos_position", -1),
                rawScore = metrics.optDouble("raw_score").toFloat(),
                normalizedScore = metrics.optDouble("normalized_score").toFloat(),
                modelLoadMs = metrics.optDouble("model_load_ms"),
                preprocessingMs = metrics.optDouble("preprocessing_ms"),
                encoderMs = metrics.optDouble("encoder_ms"),
                beamMs = metrics.optDouble("beam_ms"),
                firstTotalMs = metrics.optDouble("first_total_ms"),
                repeatedTotalMs = metrics.optDouble("repeated_total_ms"),
                repeatabilityVerified = metrics.optBoolean("repeatability_verified"),
                latencyMs = metrics.optDouble("repeated_total_ms"),
                favorite = row.optBoolean("favorite"),
            )
            database.upsertCaptionCache(ownerId, item)
        }
        val remoteIds = remote.map { it.getString("client_caption_id") }.toSet()
        cached.values.filter { it.id !in remoteIds }.forEach { stale ->
            database.deleteCaption(ownerId, stale.id)
            if (stale.thumbnailPath.isNotBlank()) File(stale.thumbnailPath).delete()
        }
        remote.size
    }

    suspend fun refreshPreferences(ownerId: String): Boolean = withContext(Dispatchers.IO) {
        val token = database.loadState(AuthStore.CentralTokenKey) ?: return@withContext false
        if (api.currentAccount(token).id != ownerId) return@withContext false
        if (database.pendingSync(ownerId).isNotEmpty()) return@withContext false
        val remote = api.getPreferences(token)
        for (column in listOf("theme", "language", "selected_model")) {
            if (remote.has(column) && !remote.isNull(column)) {
                database.savePreference(ownerId, column, remote.getString(column))
            }
        }
        true
    }

    suspend fun flush(ownerId: String): Int {
        val token = database.loadState(AuthStore.CentralTokenKey) ?: return 0
        val tokenOwner = runCatching { api.currentAccount(token).id }.getOrNull()
        if (tokenOwner != ownerId) return 0
        var completed = 0
        database.pendingSync(ownerId).forEach { item ->
            runCatching { api.pushOutbox(item, token) }
                .onSuccess {
                    database.completeSync(item.operationId)
                    completed++
                }
                .onFailure { database.failSync(item.operationId, it.message.orEmpty()) }
        }
        return completed
    }
}
