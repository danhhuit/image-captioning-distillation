package com.klcn.mobilecaptioning

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

internal enum class UserRole {
    SUPER_ADMIN,
    USER,
    GUEST,
}

internal enum class PermissionAction {
    READ,
    CREATE,
    UPDATE,
    DELETE,
}

internal enum class AppResource {
    CAPTION,
    HISTORY,
    FAVORITES,
    MODEL_INSIGHTS,
    USERS,
    ROLES,
}

internal enum class AuthProvider {
    EMAIL,
    GOOGLE,
    GUEST,
    LEGACY,
}

internal data class UserAccount(
    val id: String,
    val displayName: String,
    val username: String,
    val email: String?,
    val passwordHash: String?,
    val passwordSalt: String?,
    val provider: AuthProvider,
    val role: UserRole,
    val permissions: Set<String>,
    val active: Boolean = true,
)

internal data class UserActivityEvent(
    val id: String,
    val userId: String,
    val username: String,
    val action: String,
    val detail: String,
    val occurredAtMillis: Long,
)

internal data class AuthSession(
    val account: UserAccount?,
) {
    val ownerId: String
        get() = account?.id ?: VisionDatabase.GuestUserId

    val role: UserRole
        get() = account?.role ?: UserRole.GUEST

    val displayName: String
        get() = account?.displayName ?: "Khách"

    val isAuthenticated: Boolean
        get() = account != null

    fun can(
        resource: AppResource,
        action: PermissionAction = PermissionAction.READ,
    ): Boolean {
        if (role == UserRole.SUPER_ADMIN) return true
        return permissionKey(resource, action) in (
            account?.permissions ?: guestPermissions()
        )
    }
}

internal data class AuthOperation(
    val success: Boolean,
    val message: String,
    val session: AuthSession? = null,
)

internal data class OtpChallenge(
    val destination: String,
    val code: String,
    val expiresAtMillis: Long,
)

internal class AuthStore(private val context: Context) {
    private val database = VisionDatabase.get(context)
    private val centralApi = CentralApiClient(context)
    private val preferences = context.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    init {
        database.seedPermissionDefinitions(allPermissions())
        migrateLegacyAuthData()
        ensureGuestAccount()
    }

    fun loadSession(): AuthSession {
        val userId = database.loadState(SessionKey)
        val account = userId?.let { id ->
            loadAccounts().firstOrNull { it.id == id && it.active }
        }
        if (userId != null && account == null) {
            database.saveState(SessionKey, null)
        }
        return AuthSession(account)
    }

    fun refreshSession(session: AuthSession): AuthSession {
        val id = session.account?.id ?: return AuthSession(null)
        val refreshed = loadAccounts().firstOrNull { it.id == id && it.active }
        if (refreshed == null) database.saveState(SessionKey, null)
        return AuthSession(refreshed)
    }

    suspend fun refreshCentralSession(session: AuthSession): AuthSession {
        val token = database.loadState(CentralTokenKey) ?: return refreshSession(session)
        return try {
            val account = centralApi.currentAccount(token)
            val cached = loadAccounts().firstOrNull { it.id == account.id }
            database.upsertUser(
                account.copy(
                    passwordHash = cached?.passwordHash,
                    passwordSalt = cached?.passwordSalt,
                ),
            )
            refreshSession(session)
        } catch (error: CentralApiException) {
            if (error.statusCode == 401 || error.statusCode == 403) {
                database.saveState(CentralTokenKey, null)
                database.saveState(SessionKey, null)
                AuthSession(null)
            } else {
                refreshSession(session)
            }
        } catch (_: Throwable) {
            refreshSession(session)
        }
    }

    fun continueAsGuest(): AuthSession {
        database.saveState(SessionKey, null)
        database.saveState(CentralTokenKey, null)
        return AuthSession(null)
    }

    suspend fun logout(): AuthSession {
        val current = loadSession()
        recordActivity(current, "LOGOUT", "Đăng xuất khỏi ứng dụng")
        val token = database.loadState(CentralTokenKey)
        if (token != null) {
            runCatching { centralApi.logout(token) }
        }
        database.saveState(CentralTokenKey, null)
        return continueAsGuest()
    }

    suspend fun signInCentral(identifier: String, password: String): AuthOperation =
        try {
            val central = centralApi.login(identifier.trim(), password)
            cacheCentralSession(central, password)
            createSession(
                central.account,
                "Đã đăng nhập thành công!",
                "PASSWORD_LOGIN",
            )
        } catch (error: Throwable) {
            if (error is CentralApiException) {
                AuthOperation(false, centralErrorMessage(error))
            } else {
                val offline = signIn(identifier, password)
                if (offline.success) {
                    offline.copy(message = "Đã đăng nhập ở chế độ ngoại tuyến.")
                } else {
                    AuthOperation(false, centralErrorMessage(error))
                }
            }
        }

    suspend fun registerCentral(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): AuthOperation {
        val normalizedName = displayName.trim()
        val normalizedUsername = username.trim().lowercase()
        val normalizedEmail = email.trim().lowercase()
        if (normalizedName.length !in 2..60) {
            return AuthOperation(false, "Họ và tên phải có từ 2 đến 60 ký tự.")
        }
        if (!UsernamePattern.matches(normalizedUsername)) {
            return AuthOperation(
                false,
                "Username cần 3–24 ký tự, bắt đầu bằng chữ và chỉ gồm chữ, số, dấu chấm hoặc gạch dưới.",
            )
        }
        if (!EmailPattern.matches(normalizedEmail)) {
            return AuthOperation(false, "Email chưa đúng định dạng.")
        }
        if (!isStrongPassword(password)) {
            return AuthOperation(
                false,
                "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số.",
            )
        }
        if (password != confirmPassword) {
            return AuthOperation(false, "Hai mật khẩu chưa trùng nhau.")
        }
        return try {
            val central = centralApi.register(
                normalizedName,
                normalizedUsername,
                normalizedEmail,
                password,
            )
            cacheCentralSession(central, password)
            createSession(
                central.account,
                "Đã đăng ký tài khoản!",
                "REGISTER",
            )
        } catch (error: Throwable) {
            AuthOperation(false, centralErrorMessage(error))
        }
    }

    suspend fun signInWithGoogleCentral(idToken: String, nonce: String): AuthOperation = try {
        val central = centralApi.loginGoogle(idToken, nonce)
        cacheCentralSession(central, null)
        createSession(
            central.account,
            "Đã đăng nhập bằng Google!",
            "GOOGLE_LOGIN",
        )
    } catch (error: Throwable) {
        AuthOperation(false, centralErrorMessage(error))
    }

    fun signIn(identifier: String, password: String): AuthOperation {
        val normalized = identifier.trim().lowercase()
        val account = loadAccounts().firstOrNull {
            it.active && (
                it.email?.lowercase() == normalized ||
                    it.username.lowercase() == normalized
                )
        } ?: return AuthOperation(false, "Không tìm thấy tài khoản.")
        if (account.role == UserRole.SUPER_ADMIN) {
            return AuthOperation(false, "Quản trị viên phải đăng nhập qua máy chủ trung tâm.")
        }
        if (account.passwordHash == null || account.passwordSalt == null) {
            return AuthOperation(
                false,
                "Tài khoản này dùng ${account.provider}; hãy chọn đúng phương thức.",
            )
        }
        if (!verifyPassword(password, account.passwordSalt, account.passwordHash)) {
            return AuthOperation(false, "Mật khẩu không chính xác.")
        }
        return createSession(account, "Đã đăng nhập thành công!", "PASSWORD_LOGIN")
    }

    fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): AuthOperation {
        val normalizedName = displayName.trim()
        val normalizedUsername = username.trim().lowercase()
        val normalizedEmail = email.trim().lowercase()
        if (normalizedName.length !in 2..60) {
            return AuthOperation(false, "Họ và tên phải có từ 2 đến 60 ký tự.")
        }
        if (!UsernamePattern.matches(normalizedUsername)) {
            return AuthOperation(
                false,
                "Username cần 3–24 ký tự, bắt đầu bằng chữ và chỉ gồm chữ, số, dấu chấm hoặc gạch dưới.",
            )
        }
        if (!EmailPattern.matches(normalizedEmail)) {
            return AuthOperation(false, "Email chưa đúng định dạng.")
        }
        if (!isStrongPassword(password)) {
            return AuthOperation(
                false,
                "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số.",
            )
        }
        if (password != confirmPassword) {
            return AuthOperation(false, "Hai mật khẩu chưa trùng nhau.")
        }
        val accounts = loadAccounts()
        if (accounts.any { it.username.lowercase() == normalizedUsername }) {
            return AuthOperation(false, "Username đã tồn tại; vui lòng chọn tên khác.")
        }
        if (accounts.any { it.email?.lowercase() == normalizedEmail }) {
            return AuthOperation(false, "Email đã được sử dụng.")
        }
        val salt = randomBytes(16)
        val account = UserAccount(
            id = UUID.randomUUID().toString(),
            displayName = normalizedName,
            username = normalizedUsername,
            email = normalizedEmail,
            passwordHash = hashPassword(password, salt),
            passwordSalt = Base64.encodeToString(salt, Base64.NO_WRAP),
            provider = AuthProvider.EMAIL,
            role = UserRole.USER,
            permissions = defaultUserPermissions(),
        )
        saveAccounts(accounts + account)
        return createSession(account, "Đã đăng ký tài khoản!", "REGISTER")
    }

    fun requestOtp(destination: String): OtpChallenge {
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        return OtpChallenge(
            destination = destination.trim(),
            code = code,
            expiresAtMillis = System.currentTimeMillis() + 5 * 60 * 1000,
        )
    }

    fun signInWithGoogle(email: String, displayName: String?): AuthOperation {
        val normalized = email.trim().lowercase()
        if (!EmailPattern.matches(normalized)) {
            return AuthOperation(false, "Google không trả về email hợp lệ.")
        }
        val accounts = loadAccounts()
        val existing = accounts.firstOrNull {
            it.email?.lowercase() == normalized
        }
        val account = existing ?: UserAccount(
            id = UUID.randomUUID().toString(),
            displayName = displayName?.trim().orEmpty().ifBlank {
                normalized.substringBefore("@")
            },
            username = uniqueUsername(normalized.substringBefore("@"), accounts),
            email = normalized,
            passwordHash = null,
            passwordSalt = null,
            provider = AuthProvider.GOOGLE,
            role = UserRole.USER,
            permissions = defaultUserPermissions(),
        ).also { saveAccounts(accounts + it) }
        return createSession(account, "Đã đăng nhập bằng Google!", "GOOGLE_LOGIN")
    }

    fun resetPassword(
        identifier: String,
        challenge: OtpChallenge,
        enteredCode: String,
        newPassword: String,
    ): AuthOperation {
        if (!verifyOtp(challenge, enteredCode, identifier)) {
            return AuthOperation(false, "Mã xác minh không đúng hoặc đã hết hạn.")
        }
        if (!isStrongPassword(newPassword)) {
            return AuthOperation(
                false,
                "Mật khẩu mới cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số.",
            )
        }
        val normalized = identifier.trim().lowercase()
        val accounts = loadAccounts()
        val index = accounts.indexOfFirst {
            it.email?.lowercase() == normalized ||
                it.username.lowercase() == normalized
        }
        if (index < 0) return AuthOperation(false, "Không tìm thấy tài khoản.")
        if (accounts[index].role == UserRole.SUPER_ADMIN) {
            return AuthOperation(
                false,
                "Super Admin cố định không cho phép đặt lại mật khẩu tại thiết bị.",
            )
        }
        val salt = randomBytes(16)
        val updated = accounts.toMutableList()
        updated[index] = updated[index].copy(
            passwordHash = hashPassword(newPassword, salt),
            passwordSalt = Base64.encodeToString(salt, Base64.NO_WRAP),
            provider = AuthProvider.EMAIL,
        )
        saveAccounts(updated)
        recordActivity(
            AuthSession(updated[index]),
            "PASSWORD_RESET",
            "Đặt lại mật khẩu thành công",
        )
        return AuthOperation(true, "Đã cập nhật mật khẩu.")
    }

    fun listActivities(): List<UserActivityEvent> = database.loadActivities()
        .sortedByDescending(UserActivityEvent::occurredAtMillis)

    fun recordActivity(
        session: AuthSession,
        action: String,
        detail: String,
    ) {
        val account = session.account ?: loadAccounts().firstOrNull {
            it.id == VisionDatabase.GuestUserId
        } ?: return
        val event = UserActivityEvent(
            id = UUID.randomUUID().toString(),
            userId = account.id,
            username = account.username,
            action = action.trim().uppercase(),
            detail = detail.trim().take(240),
            occurredAtMillis = System.currentTimeMillis(),
        )
        database.insertActivity(event)
        if (account.id != VisionDatabase.GuestUserId) {
            database.enqueueSync(
                ownerId = account.id,
                entityType = "activity",
                entityId = event.id,
                operation = "CREATE",
                payloadJson = JSONObject()
                    .put("action", event.action)
                    .put("detail", event.detail)
                    .put("metadata", JSONObject())
                    .put(
                        "occurred_at",
                        java.time.Instant.ofEpochMilli(event.occurredAtMillis).toString(),
                    )
                    .toString(),
            )
        }
    }

    fun listManagedUsers(): List<UserAccount> =
        loadAccounts().filter { it.role != UserRole.GUEST }.sortedWith(
            compareBy<UserAccount> { it.role != UserRole.SUPER_ADMIN }
                .thenBy { it.displayName.lowercase() },
        )

    suspend fun refreshAdminCache(): AuthOperation {
        val token = database.loadState(CentralTokenKey)
            ?: return AuthOperation(false, "Phiên đăng nhập đã hết hạn.")
        return try {
            database.replaceUsers(centralApi.listAdminUsers(token))
            centralApi.listAdminActivities(token).forEach(database::insertActivity)
            AuthOperation(true, "Đã cập nhật dữ liệu quản trị.")
        } catch (error: Throwable) {
            AuthOperation(false, centralErrorMessage(error))
        }
    }

    fun usernameExists(username: String): Boolean {
        val normalized = username.trim().lowercase()
        return normalized.isNotBlank() && loadAccounts().any {
            it.username.lowercase() == normalized
        }
    }

    fun emailExists(email: String): Boolean {
        val normalized = email.trim().lowercase()
        return normalized.isNotBlank() && loadAccounts().any {
            it.email?.lowercase() == normalized
        }
    }

    fun updatePermissions(
        userId: String,
        permissions: Set<String>,
    ): AuthOperation {
        val accounts = loadAccounts()
        val index = accounts.indexOfFirst { it.id == userId }
        if (index < 0) return AuthOperation(false, "Không tìm thấy người dùng.")
        if (accounts[index].role == UserRole.SUPER_ADMIN) {
            return AuthOperation(false, "Không thể giới hạn Super Admin.")
        }
        val updated = accounts.toMutableList()
        updated[index] = updated[index].copy(permissions = permissions)
        saveAccounts(updated)
        recordActivity(
            AuthSession(loadSession().account),
            "PERMISSION_UPDATE",
            "Cập nhật quyền cho @${accounts[index].username}",
        )
        return AuthOperation(true, "Đã cập nhật nhóm quyền.")
    }

    suspend fun updatePermissionsCentral(
        userId: String,
        permissions: Set<String>,
    ): AuthOperation {
        val token = database.loadState(CentralTokenKey)
            ?: return AuthOperation(false, "Phiên đăng nhập đã hết hạn.")
        return try {
            centralApi.updatePermissions(userId, permissions, token)
            updatePermissions(userId, permissions)
        } catch (error: Throwable) {
            AuthOperation(false, centralErrorMessage(error))
        }
    }

    fun setUserActive(userId: String, active: Boolean): AuthOperation {
        val accounts = loadAccounts()
        val account = accounts.firstOrNull { it.id == userId }
            ?: return AuthOperation(false, "Không tìm thấy người dùng.")
        if (account.role == UserRole.SUPER_ADMIN) {
            return AuthOperation(false, "Không thể khóa Super Admin cố định.")
        }
        val updated = accounts.map {
            if (it.id == userId) it.copy(active = active) else it
        }
        saveAccounts(updated)
        recordActivity(
            loadSession(),
            if (active) "USER_UNLOCK" else "USER_LOCK",
            if (active) {
                "Mở khóa tài khoản @${account.username}"
            } else {
                "Khóa và ẩn tài khoản @${account.username}"
            },
        )
        return AuthOperation(
            true,
            if (active) "Đã mở khóa người dùng." else "Đã khóa người dùng.",
        )
    }

    suspend fun setUserActiveCentral(userId: String, active: Boolean): AuthOperation {
        val token = database.loadState(CentralTokenKey)
            ?: return AuthOperation(false, "Phiên đăng nhập đã hết hạn.")
        return try {
            centralApi.setUserActive(userId, active, token)
            setUserActive(userId, active)
        } catch (error: Throwable) {
            AuthOperation(false, centralErrorMessage(error))
        }
    }

    suspend fun updateUserProfileCentral(
        userId: String,
        displayName: String,
        password: String?,
    ): AuthOperation {
        val normalizedName = displayName.trim()
        if (normalizedName.length !in 2..60) {
            return AuthOperation(false, "Họ và tên phải có từ 2 đến 60 ký tự.")
        }
        if (!password.isNullOrBlank() && !isStrongPassword(password)) {
            return AuthOperation(
                false,
                "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số.",
            )
        }
        val token = database.loadState(CentralTokenKey)
            ?: return AuthOperation(false, "Phiên đăng nhập đã hết hạn.")
        return try {
            val updated = centralApi.updateUserProfile(
                userId = userId,
                displayName = normalizedName,
                password = password,
                accessToken = token,
            )
            database.upsertUser(updated)
            AuthOperation(true, "Đã cập nhật thông tin tài khoản.")
        } catch (error: Throwable) {
            AuthOperation(false, centralErrorMessage(error))
        }
    }

    private fun createSession(
        account: UserAccount,
        message: String,
        action: String,
    ): AuthOperation {
        database.saveState(SessionKey, account.id)
        recordActivity(AuthSession(account), action, message)
        return AuthOperation(true, message, AuthSession(account))
    }

    private fun cacheCentralSession(central: CentralSession, password: String?) {
        val accounts = loadAccounts()
        val salt = if (password != null) randomBytes(16) else null
        val cached = central.account.copy(
            passwordHash = if (salt != null) hashPassword(password.orEmpty(), salt) else null,
            passwordSalt = salt?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
        )
        val previous = accounts.firstOrNull {
            it.id != cached.id && (
                it.username.equals(cached.username, ignoreCase = true) ||
                    it.email?.equals(cached.email, ignoreCase = true) == true
            )
        }
        if (previous != null && accounts.none { it.id == cached.id }) {
            database.rekeyCachedUser(previous.id, cached.id)
        }
        database.upsertUser(cached)
        if (previous != null) {
            CaptionHistoryStore.enqueueExistingForSync(context, cached.id)
            AppPreferences.saveModel(context, cached.id, AppPreferences.loadModel(context, cached.id))
        }
        database.saveState(CentralTokenKey, central.accessToken)
    }

    private fun centralErrorMessage(error: Throwable): String = when (error) {
        is CentralApiException -> when (error.statusCode) {
            401 -> "Thông tin đăng nhập không chính xác."
            403 -> "Tài khoản đã bị khóa hoặc không có quyền truy cập."
            404 -> "Không tìm thấy dữ liệu yêu cầu."
            409 -> when (error.message) {
                "USERNAME_EXISTS" -> "Username đã tồn tại."
                "EMAIL_EXISTS" -> "Email đã được sử dụng."
                "PROTECTED_SUPER_ADMIN" -> "Không thể thay đổi tài khoản Super Admin."
                else -> "Username hoặc email đã tồn tại."
            }
            422 -> "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại."
            else -> "Có lỗi xảy ra. Vui lòng thử lại."
        }
        else -> "Không thể kết nối. Vui lòng thử lại."
    }

    private fun ensureGuestAccount() {
        val accounts = loadAccounts()
        val existing = accounts.firstOrNull { it.id == VisionDatabase.GuestUserId }
        val guest = existing?.copy(permissions = guestPermissions())
            ?: UserAccount(
                id = VisionDatabase.GuestUserId,
                displayName = "Khách cục bộ",
                username = "guest-local",
                email = null,
                passwordHash = null,
                passwordSalt = null,
                provider = AuthProvider.GUEST,
                role = UserRole.GUEST,
                permissions = guestPermissions(),
            )
        database.upsertUser(guest)
    }

    private fun loadAccounts(): List<UserAccount> = database.loadUsers()

    private fun loadLegacyAccounts(): List<UserAccount> {
        val raw = preferences.getString(AccountsKey, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                runCatching {
                    val permissions = value.optJSONArray("permissions")
                    add(
                        UserAccount(
                            id = value.getString("id"),
                            displayName = value.getString("display_name"),
                            username = value.optString("username").ifBlank {
                                value.optString("email")
                                    .substringBefore("@")
                                    .lowercase()
                                    .replace(Regex("[^a-z0-9._]"), "")
                                    .ifBlank { "user${index + 1}" }
                            },
                            email = value.optString("email").ifBlank { null },
                            passwordHash = value.optString("password_hash")
                                .ifBlank { null },
                            passwordSalt = value.optString("password_salt")
                                .ifBlank { null },
                            provider = runCatching {
                                AuthProvider.valueOf(value.getString("provider"))
                            }.getOrDefault(AuthProvider.LEGACY),
                            role = UserRole.valueOf(value.getString("role")),
                            permissions = buildSet {
                                if (permissions != null) {
                                    for (position in 0 until permissions.length()) {
                                        add(permissions.getString(position))
                                    }
                                }
                            },
                            active = value.optBoolean("active", true),
                        ),
                    )
                }
            }
        }
    }

    private fun saveAccounts(accounts: List<UserAccount>) {
        database.replaceUsers(accounts)
    }

    private fun loadLegacyActivities(): List<UserActivityEvent> {
        val raw = preferences.getString(ActivitiesKey, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                runCatching {
                    add(
                        UserActivityEvent(
                            id = value.getString("id"),
                            userId = value.getString("user_id"),
                            username = value.getString("username"),
                            action = value.getString("action"),
                            detail = value.getString("detail"),
                            occurredAtMillis = value.getLong("occurred_at"),
                        ),
                    )
                }
            }
        }
    }

    private fun migrateLegacyAuthData() {
        if (preferences.getBoolean(DatabaseMigrationKey, false)) return
        val legacyAccounts = loadLegacyAccounts()
        if (legacyAccounts.isNotEmpty()) database.replaceUsers(legacyAccounts)
        preferences.getString(SessionKey, null)?.let { legacySession ->
            if (legacyAccounts.any { it.id == legacySession }) {
                database.saveState(SessionKey, legacySession)
            }
        }
        loadLegacyActivities().forEach { event ->
            if (legacyAccounts.any { it.id == event.userId }) {
                database.insertActivity(event)
            }
        }
        preferences.edit().putBoolean(DatabaseMigrationKey, true).apply()
    }

    private fun uniqueUsername(base: String, accounts: List<UserAccount>): String {
        val cleaned = base.lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .trim('.', '_')
            .let { if (it.firstOrNull()?.isLetter() == true) it else "user$it" }
            .take(18)
            .padEnd(3, '0')
        var candidate = cleaned
        var suffix = 2
        val used = accounts.map { it.username.lowercase() }.toSet()
        while (candidate in used) {
            candidate = "${cleaned.take(20)}$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun verifyOtp(
        challenge: OtpChallenge,
        enteredCode: String,
        destination: String,
    ): Boolean =
        System.currentTimeMillis() <= challenge.expiresAtMillis &&
            challenge.code == enteredCode.trim() &&
            normalizeIdentity(challenge.destination) == normalizeIdentity(destination)

    private fun hashPassword(password: String, saltBytes: ByteArray): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            saltBytes,
            Pbkdf2Iterations,
            256,
        )
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun verifyPassword(
        password: String,
        salt: String,
        expected: String,
    ): Boolean {
        val actual = hashPassword(
            password,
            Base64.decode(salt, Base64.NO_WRAP),
        )
        return constantTimeEquals(actual, expected)
    }

    private fun constantTimeEquals(first: String, second: String): Boolean {
        if (first.length != second.length) return false
        var difference = 0
        first.indices.forEach { index ->
            difference = difference or (
                first[index].code xor second[index].code
                )
        }
        return difference == 0
    }

    companion object {
        internal const val CentralTokenKey = "central_access_token"
        private const val PreferencesName = "vision_auth_v1"
        private const val AccountsKey = "accounts"
        private const val ActivitiesKey = "activity_events"
        internal const val SessionKey = "session_user_id"
        private const val DatabaseMigrationKey = "sqlite_v1_migrated"
        private const val Pbkdf2Iterations = 120_000
        private val EmailPattern = Regex(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        )
        private val UsernamePattern = Regex("^[A-Za-z][A-Za-z0-9._]{2,23}$")

        private fun isStrongPassword(value: String): Boolean =
            value.length >= 8 &&
                value.any(Char::isUpperCase) &&
                value.any(Char::isLowerCase) &&
                value.any(Char::isDigit)

        private fun randomBytes(size: Int): ByteArray =
            ByteArray(size).also(SecureRandom()::nextBytes)
    }
}

internal data class GoogleIdentityResult(
    val idToken: String,
    val nonce: String,
    val email: String,
    val displayName: String?,
)

internal object GoogleIdentityGateway {
    suspend fun signIn(context: Context): Result<GoogleIdentityResult> = try {
            val clientId = context.getString(R.string.google_web_client_id)
            check(!clientId.startsWith("REPLACE_WITH")) {
                "Chưa cấu hình google_web_client_id trong strings.xml."
            }
            check(!clientId.startsWith("GOCSPX-")) {
                "google_web_client_id đang chứa Client secret. Hãy dùng Web Client ID, không dùng Client secret."
            }
            check(WebClientIdPattern.matches(clientId)) {
                "Web Client ID không đúng định dạng. Giá trị phải có dạng <project-number>-<id>.apps.googleusercontent.com."
            }
            val nonce = Base64.encodeToString(
                ByteArray(32).also(SecureRandom()::nextBytes),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val googleOption = GetSignInWithGoogleOption.Builder(clientId)
                .setNonce(nonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()
            val credential = CredentialManager.create(context)
                .getCredential(context, request)
                .credential
            check(
                credential is CustomCredential &&
                    credential.type ==
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            ) {
                "Google trả về loại credential không được hỗ trợ."
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(
                GoogleIdentityResult(
                    idToken = google.idToken,
                    nonce = nonce,
                    email = google.id,
                    displayName = google.displayName,
                ),
            )
        } catch (error: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    "Google không cung cấp credential. Hãy kiểm tra Android OAuth client có package com.klcn.mobilecaptioning, đúng SHA-1 của APK, cùng project với Web Client ID; bật lời nhắc Sign in with Google và thêm tài khoản thử nghiệm nếu consent screen đang ở Testing.",
                    error,
                ),
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private val WebClientIdPattern = Regex(
        "^[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com$",
    )
}

internal fun permissionKey(
    resource: AppResource,
    action: PermissionAction,
): String = "${resource.name}:${action.name}"

internal fun defaultUserPermissions(): Set<String> = buildSet {
    AppResource.entries
        .filterNot { it in setOf(AppResource.USERS, AppResource.ROLES) }
        .forEach { resource ->
            PermissionAction.entries.forEach { action ->
                add(permissionKey(resource, action))
            }
        }
}

internal fun guestPermissions(): Set<String> = setOf(
    permissionKey(AppResource.CAPTION, PermissionAction.READ),
    permissionKey(AppResource.CAPTION, PermissionAction.CREATE),
    permissionKey(AppResource.MODEL_INSIGHTS, PermissionAction.READ),
)

internal fun allPermissions(): Set<String> = buildSet {
    AppResource.entries.forEach { resource ->
        PermissionAction.entries.forEach { action ->
            add(permissionKey(resource, action))
        }
    }
}

private fun normalizeIdentity(value: String): String =
    value.trim().lowercase()
