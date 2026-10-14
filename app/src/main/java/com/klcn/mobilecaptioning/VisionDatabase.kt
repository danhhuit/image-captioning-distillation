package com.klcn.mobilecaptioning

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import org.json.JSONArray

internal data class SyncOutboxItem(
    val operationId: String,
    val ownerId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String,
    val attemptCount: Int,
)

/**
 * Local cache for offline operation, preferences and the synchronization outbox.
 * PostgreSQL behind the central API is the authoritative data store.
 *
 * SQLite does not expose stored procedures. Transactional methods in this
 * class are the application-level database functions; integrity rules that
 * must always run are implemented as SQL constraints and triggers.
 */
internal class VisionDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DatabaseName, null, DatabaseVersion) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL CHECK(length(trim(display_name)) BETWEEN 2 AND 60),
                username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                email TEXT COLLATE NOCASE UNIQUE,
                password_hash TEXT,
                password_salt TEXT,
                provider TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('SUPER_ADMIN','USER','GUEST')),
                active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createOfflineSyncTables(db)
        db.execSQL(
            """
            CREATE TABLE permission_definitions (
                permission_key TEXT PRIMARY KEY NOT NULL,
                resource TEXT NOT NULL,
                action TEXT NOT NULL,
                UNIQUE(resource, action)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE user_permissions (
                user_id TEXT NOT NULL,
                permission_key TEXT NOT NULL,
                granted_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, permission_key),
                FOREIGN KEY(user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(permission_key) REFERENCES permission_definitions(permission_key)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE activity_events (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                username_snapshot TEXT NOT NULL,
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE caption_history (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                caption TEXT NOT NULL,
                model_label TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                source TEXT NOT NULL,
                thumbnail_path TEXT NOT NULL,
                source_width INTEGER NOT NULL DEFAULT 0,
                source_height INTEGER NOT NULL DEFAULT 0,
                token_ids_json TEXT NOT NULL DEFAULT '[]',
                word_count INTEGER NOT NULL DEFAULT 0,
                eos_position INTEGER NOT NULL DEFAULT -1,
                raw_score REAL NOT NULL DEFAULT 0,
                normalized_score REAL NOT NULL DEFAULT 0,
                model_load_ms REAL NOT NULL DEFAULT 0,
                preprocessing_ms REAL NOT NULL DEFAULT 0,
                encoder_ms REAL NOT NULL DEFAULT 0,
                beam_ms REAL NOT NULL DEFAULT 0,
                first_total_ms REAL NOT NULL DEFAULT 0,
                repeated_total_ms REAL NOT NULL DEFAULT 0,
                repeatability_verified INTEGER NOT NULL DEFAULT 0 CHECK(repeatability_verified IN (0,1)),
                latency_ms REAL NOT NULL DEFAULT 0,
                favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0,1)),
                FOREIGN KEY(user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE user_preferences (
                user_id TEXT PRIMARY KEY NOT NULL,
                selected_model TEXT,
                language TEXT,
                theme TEXT,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE app_state (
                state_key TEXT PRIMARY KEY NOT NULL,
                state_value TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_caption_user_created ON caption_history(user_id, created_at DESC)")
        db.execSQL("CREATE INDEX idx_caption_user_favorite ON caption_history(user_id, favorite, created_at DESC)")
        db.execSQL("CREATE INDEX idx_activity_user_time ON activity_events(user_id, occurred_at DESC)")
        db.execSQL(
            """
            CREATE TRIGGER trg_users_updated_at
            AFTER UPDATE ON users
            FOR EACH ROW WHEN NEW.updated_at = OLD.updated_at
            BEGIN
                UPDATE users SET updated_at = CAST(strftime('%s','now') AS INTEGER) * 1000
                WHERE id = NEW.id;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_protect_super_admin
            BEFORE DELETE ON users
            FOR EACH ROW WHEN OLD.role = 'SUPER_ADMIN'
            BEGIN
                SELECT RAISE(ABORT, 'SUPER_ADMIN cannot be deleted');
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_limit_caption_history
            AFTER INSERT ON caption_history
            FOR EACH ROW
            BEGIN
                DELETE FROM caption_history
                WHERE user_id = NEW.user_id AND id NOT IN (
                    SELECT id FROM caption_history
                    WHERE user_id = NEW.user_id
                    ORDER BY created_at DESC, id DESC
                    LIMIT 100
                );
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE VIEW v_user_activity_summary AS
            SELECT u.id AS user_id, u.username, u.role,
                   COUNT(a.id) AS activity_count,
                   MAX(a.occurred_at) AS last_activity_at
            FROM users u LEFT JOIN activity_events a ON a.user_id = u.id
            GROUP BY u.id, u.username, u.role
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE VIEW v_user_caption_summary AS
            SELECT u.id AS user_id, u.username,
                   COUNT(c.id) AS caption_count,
                   COALESCE(SUM(c.favorite), 0) AS favorite_count,
                   MAX(c.created_at) AS latest_caption_at
            FROM users u LEFT JOIN caption_history c ON c.user_id = u.id
            GROUP BY u.id, u.username
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createOfflineSyncTables(db)
    }

    private fun createOfflineSyncTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_outbox (
                operation_id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                operation TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cache_metadata (
                cache_key TEXT PRIMARY KEY NOT NULL,
                etag TEXT,
                last_synced_at INTEGER,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sync_outbox_owner_created ON sync_outbox(owner_id, created_at)",
        )
    }

    fun enqueueSync(
        ownerId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
    ): String {
        val operationId = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow(
            "sync_outbox",
            null,
            ContentValues().apply {
                put("operation_id", operationId)
                put("owner_id", ownerId)
                put("entity_type", entityType)
                put("entity_id", entityId)
                put("operation", operation)
                put("payload_json", payloadJson)
                put("created_at", System.currentTimeMillis())
            },
        )
        return operationId
    }

    fun pendingSync(ownerId: String, limit: Int = 50): List<SyncOutboxItem> =
        readableDatabase.rawQuery(
            """
            SELECT * FROM sync_outbox WHERE owner_id = ?
            ORDER BY created_at ASC,
                     CASE entity_type WHEN 'media' THEN 0 WHEN 'caption' THEN 1 ELSE 2 END,
                     operation_id ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(ownerId, limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SyncOutboxItem(
                            operationId = cursor.text("operation_id"),
                            ownerId = cursor.text("owner_id"),
                            entityType = cursor.text("entity_type"),
                            entityId = cursor.text("entity_id"),
                            operation = cursor.text("operation"),
                            payloadJson = cursor.text("payload_json"),
                            attemptCount = cursor.int("attempt_count"),
                        ),
                    )
                }
            }
        }

    fun completeSync(operationId: String) {
        writableDatabase.delete("sync_outbox", "operation_id = ?", arrayOf(operationId))
    }

    fun failSync(operationId: String, error: String) {
        writableDatabase.execSQL(
            """
            UPDATE sync_outbox
               SET attempt_count = attempt_count + 1, last_error = ?
             WHERE operation_id = ?
            """.trimIndent(),
            arrayOf(error.take(500), operationId),
        )
    }

    fun seedPermissionDefinitions(keys: Set<String>) {
        writableDatabase.inTransaction {
            keys.forEach { key ->
                val (resource, action) = key.split(':', limit = 2)
                insertWithOnConflict(
                    "permission_definitions",
                    null,
                    ContentValues().apply {
                        put("permission_key", key)
                        put("resource", resource)
                        put("action", action)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
    }

    fun loadUsers(): List<UserAccount> {
        val db = readableDatabase
        return db.rawQuery("SELECT * FROM users ORDER BY lower(display_name)", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.text("id")
                    add(
                        UserAccount(
                            id = id,
                            displayName = cursor.text("display_name"),
                            username = cursor.text("username"),
                            email = cursor.nullableText("email"),
                            passwordHash = cursor.nullableText("password_hash"),
                            passwordSalt = cursor.nullableText("password_salt"),
                            provider = runCatching {
                                AuthProvider.valueOf(cursor.text("provider"))
                            }.getOrDefault(AuthProvider.LEGACY),
                            role = UserRole.valueOf(cursor.text("role")),
                            permissions = loadPermissions(db, id),
                            active = cursor.int("active") == 1,
                        ),
                    )
                }
            }
        }
    }

    fun upsertUser(account: UserAccount) {
        writableDatabase.inTransaction {
            upsertUserRow(this, account)
            replacePermissions(this, account.id, account.permissions)
        }
    }

    /**
     * Removes obsolete provider-scoped cache without touching accounts that use
     * another sign-in method. Related local rows are removed by foreign-key
     * cascades; outbox rows need an explicit cleanup because they deliberately
     * have no foreign key (they must also work while offline).
     */
    fun purgeCachedUsersByProvider(provider: AuthProvider): Int =
        writableDatabase.inTransaction {
            val ids = rawQuery(
                "SELECT id FROM users WHERE provider = ? AND role != ?",
                arrayOf(provider.name, UserRole.SUPER_ADMIN.name),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            if (ids.isEmpty()) return@inTransaction 0

            val currentSession = rawQuery(
                "SELECT state_value FROM app_state WHERE state_key = ?",
                arrayOf(AuthStore.SessionKey),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (currentSession in ids) {
                delete(
                    "app_state",
                    "state_key IN (?, ?)",
                    arrayOf(AuthStore.SessionKey, AuthStore.CentralTokenKey),
                )
            }
            ids.forEach { id ->
                delete("sync_outbox", "owner_id = ?", arrayOf(id))
                delete("users", "id = ?", arrayOf(id))
            }
            ids.size
        }

    fun rekeyCachedUser(oldId: String, newId: String) {
        if (oldId == newId) return
        writableDatabase.inTransaction {
            execSQL("UPDATE users SET id = ? WHERE id = ?", arrayOf(newId, oldId))
            execSQL("UPDATE sync_outbox SET owner_id = ? WHERE owner_id = ?", arrayOf(newId, oldId))
            execSQL(
                "UPDATE app_state SET state_value = ? WHERE state_key = ? AND state_value = ?",
                arrayOf(newId, AuthStore.SessionKey, oldId),
            )
        }
    }

    fun replaceUsers(accounts: List<UserAccount>) {
        writableDatabase.inTransaction {
            accounts.forEach { account ->
                upsertUserRow(this, account)
                replacePermissions(this, account.id, account.permissions)
            }
            val retained = accounts.map(UserAccount::id).toSet()
            rawQuery("SELECT id, role FROM users", null).use { cursor ->
                val removable = buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.text("id")
                        if (id !in retained && cursor.text("role") != UserRole.SUPER_ADMIN.name) add(id)
                    }
                }
                removable.forEach { delete("users", "id = ?", arrayOf(it)) }
            }
        }
    }

    fun insertActivity(event: UserActivityEvent) {
        writableDatabase.inTransaction {
            insertWithOnConflict(
                "activity_events",
                null,
                ContentValues().apply {
                    put("id", event.id)
                    put("user_id", event.userId)
                    put("username_snapshot", event.username)
                    put("action", event.action)
                    put("detail", event.detail)
                    put("occurred_at", event.occurredAtMillis)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            execSQL(
                """
                DELETE FROM activity_events WHERE id NOT IN (
                    SELECT id FROM activity_events ORDER BY occurred_at DESC LIMIT 2000
                )
                """.trimIndent(),
            )
        }
    }

    fun loadActivities(): List<UserActivityEvent> = readableDatabase.rawQuery(
        "SELECT * FROM activity_events ORDER BY occurred_at DESC",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    UserActivityEvent(
                        id = cursor.text("id"),
                        userId = cursor.text("user_id"),
                        username = cursor.text("username_snapshot"),
                        action = cursor.text("action"),
                        detail = cursor.text("detail"),
                        occurredAtMillis = cursor.long("occurred_at"),
                    ),
                )
            }
        }
    }

    fun insertCaption(userId: String, item: CaptionHistoryItem) {
        writableDatabase.insertOrThrow(
            "caption_history",
            null,
            item.toValues(userId),
        )
    }

    fun upsertCaptionCache(userId: String, item: CaptionHistoryItem) {
        writableDatabase.insertWithOnConflict(
            "caption_history",
            null,
            item.toValues(userId),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadCaptions(userId: String): List<CaptionHistoryItem> = readableDatabase.rawQuery(
        "SELECT * FROM caption_history WHERE user_id = ? ORDER BY created_at DESC, id DESC",
        arrayOf(userId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toCaptionHistoryItem())
        }
    }

    fun toggleFavorite(userId: String, id: String) {
        writableDatabase.execSQL(
            "UPDATE caption_history SET favorite = CASE favorite WHEN 1 THEN 0 ELSE 1 END WHERE user_id = ? AND id = ?",
            arrayOf(userId, id),
        )
    }

    fun deleteCaption(userId: String, id: String) {
        writableDatabase.delete("caption_history", "user_id = ? AND id = ?", arrayOf(userId, id))
    }

    fun clearCaptions(userId: String) {
        writableDatabase.delete("caption_history", "user_id = ?", arrayOf(userId))
    }

    fun loadPreference(userId: String, column: String): String? {
        require(column in PreferenceColumns)
        return readableDatabase.rawQuery(
            "SELECT $column FROM user_preferences WHERE user_id = ?",
            arrayOf(userId),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }

    fun savePreference(userId: String, column: String, value: String) {
        require(column in PreferenceColumns)
        val values = ContentValues().apply {
            put(column, value)
            put("updated_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        if (db.update("user_preferences", values, "user_id = ?", arrayOf(userId)) == 0) {
            values.put("user_id", userId)
            db.insertOrThrow("user_preferences", null, values)
        }
    }

    fun loadState(key: String): String? = readableDatabase.rawQuery(
        "SELECT state_value FROM app_state WHERE state_key = ?",
        arrayOf(key),
    ).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }

    fun saveState(key: String, value: String?) {
        if (value == null) {
            writableDatabase.delete("app_state", "state_key = ?", arrayOf(key))
            return
        }
        val values = ContentValues().apply {
            put("state_value", value)
            put("updated_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        if (db.update("app_state", values, "state_key = ?", arrayOf(key)) == 0) {
            values.put("state_key", key)
            db.insertOrThrow("app_state", null, values)
        }
    }

    private fun upsertUserRow(db: SQLiteDatabase, account: UserAccount) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("display_name", account.displayName)
            put("username", account.username)
            if (account.email == null) putNull("email") else put("email", account.email)
            if (account.passwordHash == null) putNull("password_hash") else put("password_hash", account.passwordHash)
            if (account.passwordSalt == null) putNull("password_salt") else put("password_salt", account.passwordSalt)
            put("provider", account.provider.name)
            put("role", account.role.name)
            put("active", if (account.active) 1 else 0)
            put("updated_at", now)
        }
        if (db.update("users", values, "id = ?", arrayOf(account.id)) == 0) {
            values.put("id", account.id)
            values.put("created_at", now)
            db.insertOrThrow("users", null, values)
        }
    }

    private fun replacePermissions(db: SQLiteDatabase, userId: String, permissions: Set<String>) {
        db.delete("user_permissions", "user_id = ?", arrayOf(userId))
        permissions.forEach { permission ->
            db.insertOrThrow(
                "user_permissions",
                null,
                ContentValues().apply {
                    put("user_id", userId)
                    put("permission_key", permission)
                    put("granted_at", System.currentTimeMillis())
                },
            )
        }
    }

    private fun loadPermissions(db: SQLiteDatabase, userId: String): Set<String> = db.rawQuery(
        "SELECT permission_key FROM user_permissions WHERE user_id = ?",
        arrayOf(userId),
    ).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun CaptionHistoryItem.toValues(userId: String) = ContentValues().apply {
        put("id", id)
        put("user_id", userId)
        put("caption", caption)
        put("model_label", modelLabel)
        put("created_at", createdAtMillis)
        put("source", source.name)
        put("thumbnail_path", thumbnailPath)
        put("source_width", sourceWidth)
        put("source_height", sourceHeight)
        put("token_ids_json", JSONArray(tokenIds).toString())
        put("word_count", wordCount)
        put("eos_position", eosPosition)
        put("raw_score", rawScore)
        put("normalized_score", normalizedScore)
        put("model_load_ms", modelLoadMs)
        put("preprocessing_ms", preprocessingMs)
        put("encoder_ms", encoderMs)
        put("beam_ms", beamMs)
        put("first_total_ms", firstTotalMs)
        put("repeated_total_ms", repeatedTotalMs)
        put("repeatability_verified", if (repeatabilityVerified) 1 else 0)
        put("latency_ms", latencyMs)
        put("favorite", if (favorite) 1 else 0)
    }

    private fun Cursor.toCaptionHistoryItem(): CaptionHistoryItem {
        val tokens = JSONArray(text("token_ids_json"))
        return CaptionHistoryItem(
            id = text("id"), caption = text("caption"), modelLabel = text("model_label"),
            createdAtMillis = long("created_at"), source = ImageInputSource.valueOf(text("source")),
            thumbnailPath = text("thumbnail_path"), sourceWidth = int("source_width"),
            sourceHeight = int("source_height"), tokenIds = buildList {
                for (index in 0 until tokens.length()) add(tokens.optLong(index))
            },
            wordCount = int("word_count"), eosPosition = int("eos_position"),
            rawScore = double("raw_score").toFloat(), normalizedScore = double("normalized_score").toFloat(),
            modelLoadMs = double("model_load_ms"), preprocessingMs = double("preprocessing_ms"),
            encoderMs = double("encoder_ms"), beamMs = double("beam_ms"),
            firstTotalMs = double("first_total_ms"), repeatedTotalMs = double("repeated_total_ms"),
            repeatabilityVerified = int("repeatability_verified") == 1,
            latencyMs = double("latency_ms"), favorite = int("favorite") == 1,
        )
    }

    private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.text(name: String) = getString(index(name))
    private fun Cursor.nullableText(name: String) = if (isNull(index(name))) null else getString(index(name))
    private fun Cursor.int(name: String) = getInt(index(name))
    private fun Cursor.long(name: String) = getLong(index(name))
    private fun Cursor.double(name: String) = getDouble(index(name))

    private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            val result = block()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
        }
    }

    companion object {
        const val GuestUserId = "vision-local-guest"
        private const val DatabaseName = "vision_caption.db"
        private const val DatabaseVersion = 2
        private val PreferenceColumns = setOf("selected_model", "language", "theme")
        @Volatile private var instance: VisionDatabase? = null

        fun get(context: Context): VisionDatabase = instance ?: synchronized(this) {
            instance ?: VisionDatabase(context).also { instance = it }
        }
    }
}
