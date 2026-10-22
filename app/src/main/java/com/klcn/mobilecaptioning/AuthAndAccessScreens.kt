package com.klcn.mobilecaptioning

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import org.json.JSONObject

private enum class AuthMode {
    SIGN_IN,
    REGISTER,
    FORGOT,
}

@Composable
internal fun AuthDestination(
    store: AuthStore,
    language: AppLanguage,
    onOpenMenu: () -> Unit,
    onAuthenticated: (AuthSession) -> Unit,
    onContinueGuest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vietnamese = language == AppLanguage.Vietnamese
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun handle(operation: AuthOperation) {
        val localizedMessage = localizedOperationMessage(operation.message, language)
        message = localizedMessage
        Toast.makeText(context, localizedMessage, Toast.LENGTH_SHORT).show()
        if (operation.success && operation.session != null) {
            onAuthenticated(operation.session)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FeatureHeader(
                title = if (language == AppLanguage.Vietnamese) {
                    "Tài khoản"
                } else {
                    "Account"
                },
                subtitle = "Vision Caption",
                onOpenMenu = onOpenMenu,
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(13.dp),
                        )
                    }
                    Column {
                        Text(
                            if (vietnamese) "Chào mừng trở lại" else "Welcome back",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (vietnamese) {
                                "Đăng nhập để đồng bộ lịch sử và mục yêu thích."
                            } else {
                                "Sign in to sync your history and favorites."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            AuthModeSelector(
                selected = mode,
                language = language,
                onSelected = {
                    mode = it
                    challenge = null
                    otp = ""
                    password = ""
                    confirmPassword = ""
                    message = null
                },
            )

            AnimatedContent(
                targetState = mode,
                label = "auth-mode",
            ) { selected ->
                when (selected) {
                    AuthMode.SIGN_IN -> {
                        AuthFormCard {
                            AuthTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                label = if (vietnamese) "Username hoặc email" else "Username or email",
                                keyboardType = KeyboardType.Email,
                            )
                            AuthTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = if (vietnamese) "Mật khẩu" else "Password",
                                password = true,
                            )
                            TextButton(
                                onClick = {
                                    mode = AuthMode.FORGOT
                                    challenge = null
                                    otp = ""
                                    password = ""
                                    confirmPassword = ""
                                    message = null
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(if (vietnamese) "Quên mật khẩu?" else "Forgot password?")
                            }
                            Button(
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        delay(250)
                                        val operation = store.signInCentral(
                                            identifier,
                                            password,
                                        )
                                        handle(operation)
                                        loading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = identifier.isNotBlank() &&
                                    password.isNotBlank(),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(if (vietnamese) "Đăng nhập" else "Sign in")
                            }
                            OutlinedButton(
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        val result = GoogleIdentityGateway
                                            .signIn(context)
                                        val operation = result.fold(
                                            onSuccess = { identity ->
                                                store.signInWithGoogleCentral(
                                                    identity.idToken,
                                                    identity.nonce,
                                                )
                                            },
                                            onFailure = {
                                                AuthOperation(
                                                    false,
                                                    it.message
                                                        ?: if (vietnamese) {
                                                            "Không thể đăng nhập Google."
                                                        } else {
                                                            "Unable to sign in with Google."
                                                        },
                                                )
                                            },
                                        )
                                        handle(operation)
                                        loading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    if (vietnamese) {
                                        "Đăng nhập bằng Google"
                                    } else {
                                        "Sign in with Google"
                                    },
                                )
                            }
                        }
                    }

                    AuthMode.REGISTER -> {
                        val usernameTaken = store.usernameExists(username)
                        val emailTaken = store.emailExists(identifier)
                        AuthFormCard {
                            AuthTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = if (vietnamese) "Họ và tên" else "Full name",
                            )
                            AuthTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = "Username",
                            )
                            if (usernameTaken) {
                                Text(
                                    if (vietnamese) {
                                        "Username đã tồn tại; không thể tạo tài khoản."
                                    } else {
                                        "This username is already in use."
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AuthTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                label = "Email",
                                keyboardType = KeyboardType.Email,
                            )
                            if (emailTaken) {
                                Text(
                                    if (vietnamese) {
                                        "Email đã được sử dụng; không thể tạo tài khoản."
                                    } else {
                                        "This email address is already in use."
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AuthTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = if (vietnamese) "Mật khẩu" else "Password",
                                password = true,
                            )
                            AuthTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = if (vietnamese) "Nhập lại mật khẩu" else "Confirm password",
                                password = true,
                            )
                            Button(
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        delay(250)
                                        val operation = store.registerCentral(
                                            displayName = displayName,
                                            username = username,
                                            email = identifier,
                                            password = password,
                                            confirmPassword = confirmPassword,
                                        )
                                        handle(operation)
                                        loading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = displayName.isNotBlank() &&
                                    username.isNotBlank() &&
                                    identifier.isNotBlank() &&
                                    password.isNotBlank() &&
                                    confirmPassword.isNotBlank() &&
                                    !usernameTaken &&
                                    !emailTaken,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(if (vietnamese) "Tạo tài khoản" else "Create account")
                            }
                        }
                    }

                    AuthMode.FORGOT -> {
                        AuthFormCard {
                            AuthTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                label = "Email",
                                keyboardType = KeyboardType.Email,
                                enabled = challenge == null,
                            )
                            if (challenge == null) {
                                Button(
                                    onClick = {
                                        loading = true
                                        scope.launch {
                                            val result = store.requestPasswordResetCentral(identifier)
                                            handle(result.operation)
                                            challenge = result.challenge
                                            loading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = identifier.isNotBlank() && !loading,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(if (vietnamese) "Gửi mã xác minh" else "Send verification code")
                                }
                            } else {
                                RecoveryCodeNotice(challenge!!, language)
                                AuthTextField(
                                    value = otp,
                                    onValueChange = {
                                        otp = it.filter(Char::isDigit).take(6)
                                    },
                                    label = if (vietnamese) "Mã xác minh" else "Verification code",
                                    keyboardType = KeyboardType.Number,
                                )
                                AuthTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = if (vietnamese) "Mật khẩu mới" else "New password",
                                    password = true,
                                )
                                AuthTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = if (vietnamese) {
                                        "Xác nhận mật khẩu mới"
                                    } else {
                                        "Confirm new password"
                                    },
                                    password = true,
                                )
                                Button(
                                    onClick = {
                                        loading = true
                                        scope.launch {
                                            val operation = store.resetPasswordCentral(
                                                challenge = challenge!!,
                                                enteredCode = otp,
                                                newPassword = password,
                                                confirmPassword = confirmPassword,
                                            )
                                            handle(operation)
                                            if (operation.success) {
                                                mode = AuthMode.SIGN_IN
                                                challenge = null
                                                otp = ""
                                                password = ""
                                                confirmPassword = ""
                                            }
                                            loading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = otp.length == 6 &&
                                        password.isNotBlank() &&
                                        confirmPassword.isNotBlank() &&
                                        !loading,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(if (vietnamese) "Đặt lại mật khẩu" else "Reset password")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = {
                                            loading = true
                                            scope.launch {
                                                val result = store.requestPasswordResetCentral(identifier)
                                                handle(result.operation)
                                                challenge = result.challenge ?: challenge
                                                otp = ""
                                                loading = false
                                            }
                                        },
                                        enabled = !loading,
                                    ) {
                                        Text(if (vietnamese) "Gửi lại mã" else "Resend code")
                                    }
                                    TextButton(
                                        onClick = {
                                            challenge = null
                                            otp = ""
                                            password = ""
                                            confirmPassword = ""
                                            message = null
                                        },
                                        enabled = !loading,
                                    ) {
                                        Text(
                                            if (vietnamese) {
                                                "Đổi tài khoản/email"
                                            } else {
                                                "Change account/email"
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = message.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            OutlinedButton(
                onClick = onContinueGuest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (vietnamese) "Tiếp tục với tư cách Khách" else "Continue as guest")
            }
            Text(
                text = if (vietnamese) {
                    "Khách chỉ tạo chú thích và xem mô hình; lịch sử, yêu thích và quản trị yêu cầu đăng nhập."
                } else {
                    "Guests can create captions and preview models; history, favorites and administration require sign-in."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(if (vietnamese) "Đang xử lý…" else "Processing…")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModeSelector(
    selected: AuthMode,
    language: AppLanguage,
    onSelected: (AuthMode) -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton(
                if (vietnamese) "Đăng nhập" else "Sign in",
                selected == AuthMode.SIGN_IN,
            ) {
                onSelected(AuthMode.SIGN_IN)
            }
            ModeButton(
                if (vietnamese) "Đăng ký" else "Register",
                selected == AuthMode.REGISTER,
            ) {
                onSelected(AuthMode.REGISTER)
            }
        }
        if (selected == AuthMode.FORGOT) {
            OutlinedButton(
                onClick = { onSelected(AuthMode.SIGN_IN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vietnamese) "Quay lại đăng nhập" else "Back to sign in")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(label, maxLines = 1)
        }
    }
}

@Composable
private fun AuthFormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (password && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(
                            if (passwordVisible) R.drawable.ic_visibility_off
                            else R.drawable.ic_visibility,
                        ),
                        contentDescription = if (passwordVisible) {
                            "Ẩn mật khẩu"
                        } else {
                            "Hiện mật khẩu"
                        },
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun RecoveryCodeNotice(
    challenge: OtpChallenge,
    language: AppLanguage,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                if (vietnamese) {
                    challenge.emailHint?.let {
                        "Mã xác minh đã được gửi đến $it"
                    } ?: "Mã xác minh đã được gửi đến email đăng ký"
                } else {
                    challenge.emailHint?.let {
                        "A verification code was sent to $it"
                    } ?: "A verification code was sent to the registered email"
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (vietnamese) {
                    "Mã gồm 6 chữ số, có hiệu lực trong 10 phút và chỉ được dùng một lần."
                } else {
                    "The 6-digit code is valid for 10 minutes and can only be used once."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun AccessRequiredDestination(
    title: String,
    language: AppLanguage,
    onOpenMenu: () -> Unit,
    onLogin: () -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureHeader(
            title,
            if (vietnamese) "Yêu cầu quyền truy cập" else "Sign-in required",
            onOpenMenu,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                modifier = Modifier
                    .padding(25.dp)
                    .size(42.dp),
            )
        }
        Text(
            text = if (vietnamese) {
                "Đăng nhập để sử dụng $title"
            } else {
                "Sign in to use $title"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (vietnamese) {
                "Chế độ Khách chỉ cho phép tạo chú thích và xem trước thông tin mô hình."
            } else {
                "Guest mode only provides captioning and model preview."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onLogin) {
            Text(if (vietnamese) "Đi tới đăng nhập" else "Go to sign in")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun AuthenticatedAccountDestination(
    session: AuthSession,
    language: AppLanguage,
    onOpenMenu: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FeatureHeader(
            title = if (vietnamese) "Cá nhân" else "Profile",
            subtitle = session.role.name.replace("_", " "),
            onOpenMenu = onOpenMenu,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        session.displayName.take(1).uppercase(),
                        modifier = Modifier.padding(22.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    session.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "@${session.account?.username.orEmpty()} · " +
                        session.account?.email.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (vietnamese) "Nhà cung cấp: " else "Provider: ") +
                        session.account?.provider?.name,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (session.role == UserRole.SUPER_ADMIN) {
            Button(
                onClick = onOpenAdmin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (vietnamese) {
                        "Mở quản trị & phân quyền"
                    } else {
                        "Open user administration"
                    },
                )
            }
        }
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(if (vietnamese) "Đăng xuất" else "Sign out")
        }
    }
}

@Composable
internal fun ModelInsightsDestination(
    language: AppLanguage,
    selectedModel: CaptionModelMode,
    modelSelectionEnabled: Boolean,
    onModelSelected: (CaptionModelMode) -> Unit,
    latestLatencyMs: Double?,
    onOpenMenu: () -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    val context = LocalContext.current
    val activityManager = remember {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    val memoryInfo = remember {
        ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    }
    val v1StudentAssetBytes = remember {
        val contract = JSONObject(
            context.assets.open("phase7b_mobile_handoff_contract.json")
                .bufferedReader()
                .use { it.readText() },
        )
        contract.getJSONObject("encoder").getLong("size_bytes") +
            contract.getJSONObject("decoder").getLong("size_bytes")
    }
    val appPssMb = remember { Debug.getPss().toDouble() / 1024.0 }
    val totalRamGb = memoryInfo.totalMem.toDouble() / 1024 / 1024 / 1024
    val availableRamGb = memoryInfo.availMem.toDouble() / 1024 / 1024 / 1024
    val studentAssetBytes = selectedModel.packageBytes ?: v1StudentAssetBytes
    val studentSizeMb = studentAssetBytes.toDouble() / 1_000_000.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FeatureHeader(
                title = if (vietnamese) "Mô hình" else "Models",
                subtitle = if (vietnamese) {
                    "Đo trên thiết bị và số liệu tham chiếu"
                } else {
                    "On-device measurements and reference metrics"
                },
                onOpenMenu = onOpenMenu,
            )
        }
        item {
            ModelSelectionCard(
                selectedModel = selectedModel,
                enabled = modelSelectionEnabled,
                language = language,
                onModelSelected = onModelSelected,
            )
        }
        item {
            ModelConfigurationCard(selectedModel, language)
        }
        item {
            DeviceMetricCard(
                totalRamGb = totalRamGb,
                availableRamGb = availableRamGb,
                appPssMb = appPssMb,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                language = language,
            )
        }
        item {
            ComparisonCard(
                title = if (!selectedModel.isV2 && !vietnamese) {
                    "V1 reference model"
                } else {
                    selectedModel.teacherName
                },
                badge = "GPU / Server",
                weight = selectedModel.teacherPackageMb?.let { sizeMb ->
                    "${"%,.2f".format(sizeMb)} MB · " +
                        if (vietnamese) "checkpoint Teacher" else "Teacher checkpoint"
                } ?: if (vietnamese) {
                    "Mô hình tham chiếu chất lượng cao"
                } else {
                    "High-quality reference model"
                },
                memory = if (vietnamese) "Không đóng gói trong APK" else "Not bundled in the APK",
                performance = if (vietnamese) "Nguồn truyền tri thức" else "Knowledge source",
                progress = 1f,
                emphasized = false,
            )
        }
        item {
            ComparisonCard(
                title = if (!selectedModel.isV2 && !vietnamese) {
                    "V1 inference engine"
                } else {
                    selectedModel.studentName
                },
                badge = if (selectedModel.isV2) "On-device INT8" else "On-device FP32",
                weight = selectedModel.studentFp32PackageMb?.let { fp32Mb ->
                    selectedModel.studentInt8PackageMb?.let { int8Mb ->
                        "FP32 ${"%.2f".format(fp32Mb)} MB → " +
                            "INT8 ${"%.2f".format(int8Mb)} MB"
                    }
                } ?: "${"%.2f".format(studentSizeMb)} MB model assets",
                memory = (
                    if (vietnamese) "App PSS hiện tại: " else "Current app PSS: "
                    ) + "${"%.1f".format(appPssMb)} MiB",
                performance = selectedModel.validationCiderD?.let { cider ->
                    "Validation CIDEr-D: ${"%.2f".format(cider * 100.0)} · " +
                        (
                            latestLatencyMs?.let {
                                if (vietnamese) {
                                    "lượt gần nhất ${"%.0f".format(it)} ms"
                                } else {
                                    "latest run ${"%.0f".format(it)} ms"
                                }
                            } ?: if (vietnamese) {
                                "chưa có inference trên thiết bị"
                            } else {
                                "no on-device inference yet"
                            }
                            )
                } ?: (
                    latestLatencyMs?.let {
                        if (vietnamese) {
                            "Inference gần nhất: ${"%.0f".format(it)} ms"
                        } else {
                            "Latest inference: ${"%.0f".format(it)} ms"
                        }
                    } ?: if (vietnamese) {
                        "Chưa có lượt inference thực tế"
                    } else {
                        "No measured inference yet"
                    }
                    ),
                progress = (
                    1.0 - (selectedModel.packageReductionFraction ?: 0.0)
                    ).toFloat().coerceIn(0.04f, 1f),
                emphasized = true,
            )
        }
    }
}

@Composable
private fun ModelConfigurationCard(
    model: CaptionModelMode,
    language: AppLanguage,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    var expanded by remember(model) { mutableStateOf(false) }
    val parameters = when (model) {
        CaptionModelMode.V1_FALLBACK -> "8,480,816"
        CaptionModelMode.T1_S1 -> "8,990,352"
        CaptionModelMode.T2_S2 -> "10,874,112"
        CaptionModelMode.T3_S3 -> "10,925,684"
    }
    val precision = if (model.isV2) "Dynamic INT8" else "FP32"
    val vocabulary = if (model.isV2) "10,080 tokens" else "12,293 tokens"
    val memory = if (model.isV2) "float32 [1,49,256]" else "float32 [1,1,256]"
    val logits = if (model.isV2) "float32 [1,31,10080]" else "float32 [3,31,12293]"
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (vietnamese) "Thông số cấu hình" else "Configuration",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${model.displayName} · $precision",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (expanded) {
                        if (vietnamese) "Thu gọn" else "Collapse"
                    } else {
                        if (vietnamese) "Xem chi tiết" else "View details"
                    },
                )
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HorizontalDivider()
                    ConfigurationRow(if (vietnamese) "Mã mô hình" else "Model ID", model.pairId)
                    ConfigurationRow(
                        if (vietnamese) "Kiến trúc" else "Architecture",
                        if (!model.isV2 && !vietnamese) "V1 inference engine" else model.studentName,
                    )
                    ConfigurationRow(
                        if (vietnamese) "Phương pháp KD" else "KD method",
                        model.kdMethod(vietnamese),
                    )
                    ConfigurationRow(if (vietnamese) "Số tham số" else "Parameters", parameters)
                    ConfigurationRow(if (vietnamese) "Độ chính xác" else "Precision", precision)
                    model.teacherPackageMb?.let {
                        ConfigurationRow(
                            if (vietnamese) {
                                "Dung lượng Teacher"
                            } else {
                                "Teacher size"
                            },
                            "${"%,.2f".format(it)} MB",
                        )
                    }
                    model.studentFp32PackageMb?.let {
                        ConfigurationRow("Student FP32", "${"%.2f".format(it)} MB")
                    }
                    model.studentInt8PackageMb?.let {
                        ConfigurationRow("Student INT8", "${"%.2f".format(it)} MB")
                    }
                    model.teacherToStudentInt8Ratio?.let {
                        ConfigurationRow(
                            if (vietnamese) {
                                "Tỷ lệ Teacher/Student INT8"
                            } else {
                                "Teacher/Student INT8 ratio"
                            },
                            "${"%.2f".format(it)}×",
                        )
                    }
                    ConfigurationRow("Encoder", model.encoderAsset ?: "baseline_encoder_fp32.onnx")
                    ConfigurationRow("Decoder", model.decoderAsset ?: "baseline_decoder_fp32.onnx")
                    ConfigurationRow(if (vietnamese) "Từ vựng" else "Vocabulary", vocabulary)
                    ConfigurationRow(if (vietnamese) "Đầu vào" else "Input", "float32 [1,3,224,224]")
                    ConfigurationRow("Visual memory", memory)
                    ConfigurationRow("Decoder logits", logits)
                    ConfigurationRow(
                        "Beam search",
                        if (vietnamese) "beam 3 · tối đa 31 tokens" else "beam 3 · max 31 tokens",
                    )
                    model.validationCiderD?.let {
                        ConfigurationRow("Validation CIDEr-D", "%.2f".format(it * 100.0))
                    }
                    model.validationBleu4?.let {
                        ConfigurationRow("Validation BLEU-4", "%.2f".format(it * 100.0))
                    }
                    model.validationCe?.let {
                        ConfigurationRow("Validation CE", "%.4f".format(it))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ModelSelectionCard(
    selectedModel: CaptionModelMode,
    enabled: Boolean,
    language: AppLanguage,
    onModelSelected: (CaptionModelMode) -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (vietnamese) {
                    "Chọn mô hình chạy trên thiết bị"
                } else {
                    "Select an on-device model"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (vietnamese) {
                    "V1 là phương án dự phòng. Ba mô hình V2 INT8 đã vượt cổng chất lượng."
                } else {
                    "V1 is the fallback. All three V2 INT8 artifacts passed the quality gate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = CaptionModelMode.selectableModes,
                    key = CaptionModelMode::pairId,
                ) { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { onModelSelected(model) },
                        enabled = enabled,
                        label = { Text(model.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceMetricCard(
    totalRamGb: Double,
    availableRamGb: Double,
    appPssMb: Double,
    cpuCores: Int,
    language: AppLanguage,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (vietnamese) "Thiết bị hiện tại" else "Current device",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "RAM: ${"%.1f".format(availableRamGb)} / " +
                    "${"%.1f".format(totalRamGb)} GiB " +
                    if (vietnamese) "khả dụng" else "available",
            )
            Text("CPU: $cpuCores logical cores")
            Text(
                (if (vietnamese) "Ứng dụng: " else "Application: ") +
                    "${"%.1f".format(appPssMb)} MiB PSS",
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    title: String,
    badge: String,
    weight: String,
    memory: String,
    performance: String,
    progress: Float,
    emphasized: Boolean,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(weight)
            Text(
                memory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                performance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
internal fun AdminDestination(
    store: AuthStore,
    session: AuthSession,
    language: AppLanguage,
    onOpenMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vietnamese = language == AppLanguage.Vietnamese
    var users by remember { mutableStateOf(store.listManagedUsers()) }
    var activities by remember { mutableStateOf(store.listActivities()) }
    var selected by remember { mutableStateOf<UserAccount?>(null) }
    var editing by remember { mutableStateOf<UserAccount?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activityFilter by remember { mutableStateOf(AdminActivityFilter.ALL) }
    var userPage by remember { mutableStateOf(1) }
    var activityPage by remember { mutableStateOf(1) }
    if (session.role != UserRole.SUPER_ADMIN) {
        AccessRequiredDestination(
            title = if (language == AppLanguage.Vietnamese) "Quản trị" else "Administration",
            language = language,
            onOpenMenu = onOpenMenu,
            onLogin = {},
        )
        return
    }
    LaunchedEffect(session.account?.id) {
        val result = store.refreshAdminCache()
        users = store.listManagedUsers()
        activities = store.listActivities()
        if (!result.success) {
            message = localizedOperationMessage(result.message, language)
        }
    }
    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredActivities = activities.filter { event ->
        val matchesQuery = normalizedQuery.isBlank() ||
            event.username.lowercase().contains(normalizedQuery) ||
            event.action.lowercase().contains(normalizedQuery) ||
            event.detail.lowercase().contains(normalizedQuery)
        matchesQuery && activityFilter.matches(event.action)
    }
    val filteredUsers = users.filter { user ->
        normalizedQuery.isBlank() ||
            user.displayName.lowercase().contains(normalizedQuery) ||
            user.username.lowercase().contains(normalizedQuery) ||
            user.email.orEmpty().lowercase().contains(normalizedQuery)
    }
    val userPages = max(1, (filteredUsers.size + AdminUserPageSize - 1) / AdminUserPageSize)
    val activityPages = max(
        1,
        (filteredActivities.size + AdminActivityPageSize - 1) / AdminActivityPageSize,
    )
    userPage = userPage.coerceIn(1, userPages)
    activityPage = activityPage.coerceIn(1, activityPages)
    val visibleUsers = filteredUsers
        .drop((userPage - 1) * AdminUserPageSize)
        .take(AdminUserPageSize)
    val visibleActivities = filteredActivities
        .drop((activityPage - 1) * AdminActivityPageSize)
        .take(AdminActivityPageSize)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FeatureHeader(
                title = if (vietnamese) "Quản trị người dùng" else "User administration",
                subtitle = "${users.size} " +
                    if (vietnamese) "tài khoản · RBAC" else "accounts · RBAC",
                onOpenMenu = onOpenMenu,
            )
        }
        item {
            AdminStatisticsCard(
                users = users,
                activities = activities,
                language = language,
            )
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    userPage = 1
                    activityPage = 1
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        if (vietnamese) {
                            "Tìm người dùng hoặc hoạt động"
                        } else {
                            "Search users or activity"
                        },
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = if (vietnamese) "Xóa tìm kiếm" else "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AdminActivityFilter.entries) { filter ->
                    FilterChip(
                        selected = activityFilter == filter,
                        onClick = {
                            activityFilter = filter
                            activityPage = 1
                        },
                        label = { Text(filter.label(language)) },
                    )
                }
            }
        }
        message?.let { current ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(current, modifier = Modifier.padding(12.dp))
                }
            }
        }
        items(visibleUsers, key = UserAccount::id) { user ->
            Card(
                onClick = { selected = user },
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            user.displayName.take(1).uppercase(),
                            modifier = Modifier.padding(13.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, fontWeight = FontWeight.Bold)
                        Text(
                            "@${user.username} · ${user.email ?: user.provider.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (user.active) {
                                "${activities.count { it.userId == user.id }} " +
                                    if (vietnamese) "hoạt động" else "activities"
                            } else {
                                if (vietnamese) {
                                    "Đã khóa · không thể đăng nhập"
                                } else {
                                    "Locked · sign-in disabled"
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (user.active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    Text(
                        user.role.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (filteredUsers.isEmpty()) {
            item {
                Text(
                    if (vietnamese) {
                        "Không tìm thấy người dùng phù hợp."
                    } else {
                        "No matching users."
                    },
                )
            }
        } else {
            item {
                AdminPager(
                    page = userPage,
                    pages = userPages,
                    language = language,
                    onPrevious = { userPage-- },
                    onNext = { userPage++ },
                )
            }
        }
        item {
            Text(
                if (vietnamese) "Hoạt động gần đây" else "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (filteredActivities.isEmpty()) {
            item {
                Text(
                    if (vietnamese) "Không có hoạt động phù hợp." else "No matching activity.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(visibleActivities, key = UserActivityEvent::id) { event ->
                ActivityEventCard(event, language)
            }
            item {
                AdminPager(
                    page = activityPage,
                    pages = activityPages,
                    language = language,
                    onPrevious = { activityPage-- },
                    onNext = { activityPage++ },
                )
            }
        }
    }
    selected?.let { user ->
        PermissionDialog(
            user = user,
            language = language,
            onDismiss = { selected = null },
            onEdit = {
                editing = user
                selected = null
            },
            onSave = { permissions ->
                scope.launch {
                    val result = store.updatePermissionsCentral(user.id, permissions)
                    val localizedMessage = localizedOperationMessage(result.message, language)
                    message = localizedMessage
                    Toast.makeText(context, localizedMessage, Toast.LENGTH_SHORT).show()
                    users = store.listManagedUsers()
                    activities = store.listActivities()
                    if (result.success) selected = null
                }
            },
            onSetActive = { active ->
                scope.launch {
                    val result = store.setUserActiveCentral(user.id, active)
                    val localizedMessage = localizedOperationMessage(result.message, language)
                    message = localizedMessage
                    Toast.makeText(context, localizedMessage, Toast.LENGTH_SHORT).show()
                    users = store.listManagedUsers()
                    activities = store.listActivities()
                    if (result.success) selected = null
                }
            },
        )
    }
    editing?.let { user ->
        UserEditDialog(
            user = user,
            language = language,
            onDismiss = { editing = null },
            onSave = { displayName, password ->
                scope.launch {
                    val result = store.updateUserProfileCentral(
                        userId = user.id,
                        displayName = displayName,
                        password = password,
                    )
                    val localizedMessage = localizedOperationMessage(result.message, language)
                    message = localizedMessage
                    Toast.makeText(context, localizedMessage, Toast.LENGTH_SHORT).show()
                    users = store.listManagedUsers()
                    activities = store.listActivities()
                    if (result.success) editing = null
                }
            },
        )
    }
}

private fun localizedOperationMessage(
    message: String,
    language: AppLanguage,
): String {
    if (language == AppLanguage.Vietnamese || message.none { it.code > 127 }) {
        return message
    }
    return when {
        message.startsWith("Đã đăng nhập bằng Google") -> "Signed in with Google!"
        message.startsWith("Đã đăng nhập") -> "Signed in successfully!"
        message.startsWith("Đã đăng ký") -> "Account created!"
        message.startsWith("Đã đăng xuất") -> "Signed out."
        message.startsWith("Đã cập nhật nhóm quyền") -> "Permissions updated."
        message.startsWith("Đã cập nhật thông tin") -> "Account updated."
        message.startsWith("Đã mở khóa") -> "User unlocked."
        message.startsWith("Đã khóa") -> "User locked."
        message.startsWith("Không thể kết nối") -> "Unable to connect. Check the server address and network."
        message.startsWith("Thông tin đăng nhập") -> "Incorrect username, email or password."
        message.startsWith("Tài khoản đã bị khóa") -> "This account is locked or lacks access."
        message.startsWith("Không tìm thấy") -> "The requested account or data was not found."
        message.startsWith("Username đã tồn tại") -> "This username is already in use."
        message.startsWith("Email đã được sử dụng") -> "This email address is already in use."
        message.startsWith("Họ và tên") -> "Full name must contain 2 to 60 characters."
        message.startsWith("Username cần") -> "Username must contain 3–24 valid characters."
        message.startsWith("Email chưa đúng") -> "Enter a valid email address."
        message.startsWith("Vui lòng nhập đúng định dạng email") ->
            "Enter a valid email address."
        message.startsWith("Email chưa tồn tại") ->
            "This email is not registered in the system."
        message.startsWith("Tài khoản này không sử dụng mật khẩu email") ->
            "This account does not use an email password."
        message.startsWith("Mật khẩu cần") || message.startsWith("Mật khẩu mới cần") ->
            "Password must have at least 8 characters, uppercase, lowercase and a number."
        message.startsWith("Hai mật khẩu") -> "Passwords do not match."
        message.startsWith("Mã xác minh đã được gửi") ->
            message.replace("Mã xác minh đã được gửi đến", "Verification code sent to")
        message.startsWith("Nếu tài khoản hợp lệ") ->
            "If the account is valid, a verification code was sent to its registered email."
        message.startsWith("Mã xác minh phải") -> "The verification code must contain 6 digits."
        message.startsWith("Mã xác minh không đúng") ->
            "The verification code is incorrect or expired."
        message.startsWith("Mã xác minh đã hết hạn") ->
            "The verification code has expired. Request a new code."
        message.startsWith("Đã cập nhật mật khẩu") ->
            "Password updated. Please sign in again."
        message.startsWith("Bạn đã yêu cầu quá nhiều") ->
            "Too many codes requested. Try again in 15 minutes."
        message.startsWith("Máy chủ chưa cấu hình") ->
            "The server has not configured password recovery email."
        message.startsWith("Không thể gửi email") ->
            "The verification email could not be sent. Please try again."
        message.startsWith("Dịch vụ email") ->
            "The email service is temporarily unavailable."
        message.startsWith("Thông tin chưa hợp lệ") -> "Some information is invalid. Please review it."
        message.startsWith("Không thể thay đổi") -> "This protected account cannot be changed."
        else -> "The request could not be completed. Please try again."
    }
}

private const val AdminUserPageSize = 5
private const val AdminActivityPageSize = 10

private enum class AdminActivityFilter {
    ALL,
    AUTH,
    CAPTION,
    ACCESS,
    ACCOUNT;

    fun label(language: AppLanguage): String {
        val vietnamese = language == AppLanguage.Vietnamese
        return when (this) {
            ALL -> if (vietnamese) "Tất cả" else "All"
            AUTH -> if (vietnamese) "Đăng nhập" else "Authentication"
            CAPTION -> if (vietnamese) "Chú thích" else "Captions"
            ACCESS -> if (vietnamese) "Quyền" else "Access"
            ACCOUNT -> if (vietnamese) "Tài khoản" else "Accounts"
        }
    }

    fun matches(action: String): Boolean = when (this) {
        ALL -> true
        AUTH -> action.contains("LOGIN") || action == "LOGOUT" || action == "REGISTER"
        CAPTION -> action.startsWith("CAPTION") || action.startsWith("FAVORITE")
        ACCESS -> action.contains("PERMISSION") || action.contains("ROLE")
        ACCOUNT -> action.startsWith("USER_") || action.contains("PASSWORD")
    }
}

@Composable
private fun AdminPager(
    page: Int,
    pages: Int,
    language: AppLanguage,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, enabled = page > 1) {
            Icon(
                painter = painterResource(R.drawable.ic_previous),
                contentDescription = if (vietnamese) "Trang trước" else "Previous page",
            )
        }
        Text(
            (if (vietnamese) "Trang" else "Page") + " $page / $pages",
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = onNext, enabled = page < pages) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = if (vietnamese) "Trang sau" else "Next page",
            )
        }
    }
}

@Composable
private fun AdminStatisticsCard(
    users: List<UserAccount>,
    activities: List<UserActivityEvent>,
    language: AppLanguage,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    val authenticatedUsers = users.count { it.role != UserRole.SUPER_ADMIN }
    val signIns = activities.count { it.action.endsWith("LOGIN") }
    val captions = activities.count { it.action == "CAPTION_CREATE" }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (vietnamese) "Tổng quan hoạt động" else "Activity overview",
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AdminMetric(if (vietnamese) "Người dùng" else "Users", authenticatedUsers.toString())
                AdminMetric(if (vietnamese) "Đăng nhập" else "Sign-ins", signIns.toString())
                AdminMetric(if (vietnamese) "Tạo mô tả" else "Captions", captions.toString())
                AdminMetric(if (vietnamese) "Tổng sự kiện" else "Events", activities.size.toString())
            }
        }
    }
}

@Composable
private fun AdminMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActivityEventCard(
    event: UserActivityEvent,
    language: AppLanguage,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    val formattedTime = remember(event.occurredAtMillis) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(event.occurredAtMillis))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("@${event.username}", fontWeight = FontWeight.Bold)
                Text(formattedTime, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                localizedActivityAction(event.action, vietnamese),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (vietnamese || event.detail.none { it.code > 127 }) {
                    event.detail
                } else {
                    localizedActivityDetail(event.action)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun localizedActivityAction(
    action: String,
    vietnamese: Boolean,
): String {
    if (!vietnamese) return action.replace('_', ' ')
    return when (action) {
        "PASSWORD_LOGIN" -> "ĐĂNG NHẬP"
        "GOOGLE_LOGIN" -> "ĐĂNG NHẬP GOOGLE"
        "REGISTER" -> "ĐĂNG KÝ"
        "LOGOUT" -> "ĐĂNG XUẤT"
        "CAPTION_CREATE" -> "TẠO CHÚ THÍCH"
        "FAVORITE_UPDATE" -> "CẬP NHẬT YÊU THÍCH"
        "HISTORY_DELETE" -> "XÓA LỊCH SỬ"
        "HISTORY_CLEAR" -> "XÓA TOÀN BỘ LỊCH SỬ"
        "MODEL_SELECT" -> "CHỌN MÔ HÌNH"
        else -> action.replace('_', ' ')
    }
}

private fun localizedActivityDetail(action: String): String = when (action) {
    "PASSWORD_LOGIN", "GOOGLE_LOGIN" -> "Signed in successfully."
    "REGISTER" -> "Account created."
    "LOGOUT" -> "Signed out."
    "CAPTION_CREATE" -> "Created an image caption."
    "FAVORITE_UPDATE" -> "Updated a favorite."
    "HISTORY_DELETE" -> "Deleted a history item."
    "HISTORY_CLEAR" -> "Cleared caption history."
    "MODEL_SELECT" -> "Selected an on-device model."
    else -> "Activity completed."
}

@Composable
private fun PermissionDialog(
    user: UserAccount,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onSetActive: (Boolean) -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    val hiddenUserResources = setOf(AppResource.USERS, AppResource.ROLES)
    var permissions by remember(user.id) {
        mutableStateOf(
            if (user.role == UserRole.USER) {
                user.permissions.filterNot { permission ->
                    hiddenUserResources.any { resource ->
                        permission.startsWith("${resource.name}:")
                    }
                }.toSet()
            } else {
                user.permissions
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName)
                    Text(
                        if (user.active) {
                            if (vietnamese) "Đang hoạt động" else "Active"
                        } else {
                            if (vietnamese) "Đã khóa" else "Locked"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (user.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (user.role == UserRole.USER) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onEdit) {
                                Text(if (vietnamese) "Chỉnh sửa" else "Edit")
                            }
                            IconButton(
                                onClick = { onSetActive(!user.active) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (user.active) R.drawable.ic_lock
                                        else R.drawable.ic_lock_open,
                                    ),
                                    contentDescription = if (user.active) {
                                        if (vietnamese) "Khóa người dùng" else "Lock user"
                                    } else {
                                        if (vietnamese) "Mở khóa người dùng" else "Unlock user"
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = if (vietnamese) "Đóng" else "Close",
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .height(430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppResource.entries
                    .filterNot { user.role == UserRole.USER && it in hiddenUserResources }
                    .forEach { resource ->
                    Column {
                        val resourceKeys = PermissionAction.entries.map {
                            action -> permissionKey(resource, action)
                        }.toSet()
                        val allSelected = resourceKeys.all {
                            key -> key in permissions
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (vietnamese) "TÀI NGUYÊN: " else "RESOURCE: ") + resource.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            FilledTonalButton(
                                onClick = {
                                    permissions = if (allSelected) {
                                        permissions - resourceKeys
                                    } else {
                                        permissions + resourceKeys
                                    }
                                },
                                enabled = user.role != UserRole.SUPER_ADMIN,
                                contentPadding = PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 2.dp,
                                ),
                            ) {
                                Text(
                                    if (allSelected) {
                                        if (vietnamese) "Bỏ chọn" else "Clear"
                                    } else {
                                        if (vietnamese) "Chọn tất cả" else "Select all"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        PermissionAction.entries.forEach { action ->
                            val key = permissionKey(resource, action)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = key in permissions,
                                    onCheckedChange = { checked ->
                                        permissions = if (checked) {
                                            permissions + key
                                        } else {
                                            permissions - key
                                        }
                                    },
                                    enabled = user.role != UserRole.SUPER_ADMIN,
                                )
                                Text(action.name)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(permissions) },
                enabled = user.role != UserRole.SUPER_ADMIN,
            ) {
                Text(if (vietnamese) "Lưu quyền" else "Save permissions")
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun UserEditDialog(
    user: UserAccount,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (displayName: String, password: String?) -> Unit,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    var displayName by remember(user.id) { mutableStateOf(user.displayName) }
    var password by remember(user.id) { mutableStateOf("") }
    var confirmPassword by remember(user.id) { mutableStateOf("") }
    var showPassword by remember(user.id) { mutableStateOf(false) }
    var showConfirmPassword by remember(user.id) { mutableStateOf(false) }
    val normalizedName = displayName.trim()
    val strongPassword = password.isBlank() || (
        password.length >= 8 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() }
        )
    val canSave = normalizedName.length in 2..60 &&
        strongPassword &&
        password == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (vietnamese) "Chỉnh sửa tài khoản" else "Edit account",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = if (vietnamese) "Đóng" else "Close",
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (vietnamese) "Họ và tên" else "Full name") },
                )
                OutlinedTextField(
                    value = user.username,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    label = {
                        Text(
                            if (vietnamese) {
                                "Username · Không thể thay đổi"
                            } else {
                                "Username · Cannot be changed"
                            },
                        )
                    },
                )
                OutlinedTextField(
                    value = user.email.orEmpty(),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    label = {
                        Text(
                            if (vietnamese) "Email · Không thể thay đổi"
                            else "Email · Cannot be changed",
                        )
                    },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            if (vietnamese) {
                                "Mật khẩu mới · Không bắt buộc"
                            } else {
                                "New password · Optional"
                            },
                        )
                    },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(
                                    if (showPassword) R.drawable.ic_visibility_off
                                    else R.drawable.ic_visibility,
                                ),
                                contentDescription = if (showPassword) {
                                    if (vietnamese) "Ẩn mật khẩu" else "Hide password"
                                } else {
                                    if (vietnamese) "Hiện mật khẩu" else "Show password"
                                },
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            if (vietnamese) "Nhập lại mật khẩu mới"
                            else "Confirm new password",
                        )
                    },
                    visualTransformation = if (showConfirmPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { showConfirmPassword = !showConfirmPassword },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (showConfirmPassword) R.drawable.ic_visibility_off
                                    else R.drawable.ic_visibility,
                                ),
                                contentDescription = if (showConfirmPassword) {
                                    if (vietnamese) "Ẩn mật khẩu" else "Hide password"
                                } else {
                                    if (vietnamese) "Hiện mật khẩu" else "Show password"
                                },
                            )
                        }
                    },
                )
                if (!strongPassword) {
                    Text(
                        text = if (vietnamese) {
                            "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số."
                        } else {
                            "Password must have at least 8 characters, uppercase, lowercase and a number."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (password != confirmPassword) {
                    Text(
                        text = if (vietnamese) {
                            "Hai mật khẩu chưa trùng nhau."
                        } else {
                            "Passwords do not match."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(normalizedName, password.takeIf { it.isNotBlank() })
                },
                enabled = canSave,
            ) {
                Text(if (vietnamese) "Lưu thay đổi" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (vietnamese) "Hủy" else "Cancel")
            }
        },
    )
}

@Composable
internal fun FeatureHeader(
    title: String,
    subtitle: String,
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onOpenMenu) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Mở menu",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(48.dp))
    }
}
