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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        message = operation.message
        Toast.makeText(context, operation.message, Toast.LENGTH_SHORT).show()
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
                            "Chào mừng trở lại",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Đăng nhập để đồng bộ lịch sử và mục yêu thích.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            AuthModeSelector(
                selected = mode,
                onSelected = {
                    mode = it
                    challenge = null
                    otp = ""
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
                                label = "Username hoặc email",
                                keyboardType = KeyboardType.Email,
                            )
                            AuthTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Mật khẩu",
                                password = true,
                            )
                            TextButton(
                                onClick = {
                                    mode = AuthMode.FORGOT
                                    challenge = null
                                    otp = ""
                                    message = null
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Quên mật khẩu?")
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
                                Text("Đăng nhập")
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
                                                        ?: "Không thể đăng nhập Google.",
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
                                Text("Đăng nhập bằng Google")
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
                                label = "Họ và tên",
                            )
                            AuthTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = "Username",
                            )
                            if (usernameTaken) {
                                Text(
                                    "Username đã tồn tại; không thể tạo tài khoản.",
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
                                    "Email đã được sử dụng; không thể tạo tài khoản.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AuthTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Mật khẩu",
                                password = true,
                            )
                            AuthTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = "Nhập lại mật khẩu",
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
                                Text("Tạo tài khoản")
                            }
                        }
                    }

                    AuthMode.FORGOT -> {
                        AuthFormCard {
                            AuthTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                label = "Username hoặc email",
                            )
                            if (challenge == null) {
                                Button(
                                    onClick = {
                                        challenge = store.requestOtp(identifier)
                                        message = "Đã tạo mã khôi phục tạm thời."
                                        Toast.makeText(
                                            context,
                                            message,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = identifier.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text("Nhận mã khôi phục")
                                }
                            } else {
                                RecoveryCodeNotice(challenge!!)
                                AuthTextField(
                                    value = otp,
                                    onValueChange = { otp = it },
                                    label = "Mã xác minh",
                                    keyboardType = KeyboardType.Number,
                                )
                                AuthTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "Mật khẩu mới",
                                    password = true,
                                )
                                Button(
                                    onClick = {
                                        loading = true
                                        scope.launch {
                                            val operation = withContext(
                                                Dispatchers.Default,
                                            ) {
                                                store.resetPassword(
                                                    identifier,
                                                    challenge!!,
                                                    otp,
                                                    password,
                                                )
                                            }
                                            handle(operation)
                                            loading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text("Đặt lại mật khẩu")
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
                Text("Tiếp tục với tư cách Khách")
            }
            Text(
                text = "Guest chỉ dùng Caption và xem thông tin mô hình ở " +
                    "chế độ giới hạn; lịch sử, yêu thích và quản trị yêu cầu đăng nhập.",
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
                        Text("Đang xử lý…")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModeSelector(
    selected: AuthMode,
    onSelected: (AuthMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton("Đăng nhập", selected == AuthMode.SIGN_IN) {
                onSelected(AuthMode.SIGN_IN)
            }
            ModeButton("Đăng ký", selected == AuthMode.REGISTER) {
                onSelected(AuthMode.REGISTER)
            }
        }
        if (selected == AuthMode.FORGOT) {
            OutlinedButton(
                onClick = { onSelected(AuthMode.SIGN_IN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Quay lại đăng nhập")
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
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
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
private fun RecoveryCodeNotice(challenge: OtpChallenge) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                "Mã khôi phục tạm thời: ${challenge.code}",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Khi triển khai chính thức, mã này phải được gửi qua email " +
                    "bằng dịch vụ xác thực phía máy chủ và không hiển thị tại đây.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun AccessRequiredDestination(
    title: String,
    onOpenMenu: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureHeader(title, "Yêu cầu quyền truy cập", onOpenMenu)
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
            text = "Đăng nhập để sử dụng $title",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Chế độ Khách chỉ cung cấp trải nghiệm Caption và " +
                "thông tin mô hình ở mức preview.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onLogin) {
            Text("Đi tới đăng nhập")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun AuthenticatedAccountDestination(
    session: AuthSession,
    onOpenMenu: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FeatureHeader(
            title = "Tài khoản",
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
                    "Nhà cung cấp: ${session.account?.provider?.name}",
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
                Text("Mở quản trị & phân quyền")
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
            Text("Đăng xuất")
        }
    }
}

@Composable
internal fun ModelInsightsDestination(
    selectedModel: CaptionModelMode,
    modelSelectionEnabled: Boolean,
    onModelSelected: (CaptionModelMode) -> Unit,
    latestLatencyMs: Double?,
    onOpenMenu: () -> Unit,
) {
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
    val studentSizeMb = studentAssetBytes.toDouble() / 1024 / 1024

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FeatureHeader(
                title = "Mô hình",
                subtitle = "Đo trên thiết bị và số liệu tham chiếu",
                onOpenMenu = onOpenMenu,
            )
        }
        item {
            ModelSelectionCard(
                selectedModel = selectedModel,
                enabled = modelSelectionEnabled,
                onModelSelected = onModelSelected,
            )
        }
        item {
            ModelConfigurationCard(selectedModel)
        }
        item {
            DeviceMetricCard(
                totalRamGb = totalRamGb,
                availableRamGb = availableRamGb,
                appPssMb = appPssMb,
                cpuCores = Runtime.getRuntime().availableProcessors(),
            )
        }
        item {
            ComparisonCard(
                title = selectedModel.teacherName,
                badge = "GPU / Server",
                weight = "Mô hình tham chiếu chất lượng cao",
                memory = "Không đóng gói trong APK",
                performance = "Nguồn truyền tri thức",
                progress = 1f,
                emphasized = false,
            )
        }
        item {
            ComparisonCard(
                title = selectedModel.studentName,
                badge = if (selectedModel.isV2) "On-device INT8" else "On-device FP32",
                weight = "${"%.1f".format(studentSizeMb)} MiB model assets",
                memory = "App PSS hiện tại: ${"%.1f".format(appPssMb)} MiB",
                performance = selectedModel.validationCiderD?.let { cider ->
                    "Validation CIDEr-D: ${"%.4f".format(cider)} · " +
                        (
                            latestLatencyMs?.let {
                                "lượt gần nhất ${"%.0f".format(it)} ms"
                            } ?: "chưa có inference trên thiết bị"
                            )
                } ?: (
                    latestLatencyMs?.let {
                        "Inference gần nhất: ${"%.0f".format(it)} ms"
                    } ?: "Chưa có lượt inference thực tế"
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
private fun ModelConfigurationCard(model: CaptionModelMode) {
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
                    Text("Thông số cấu hình", fontWeight = FontWeight.Bold)
                    Text(
                        "${model.displayName} · $precision",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(if (expanded) "Thu gọn" else "Xem chi tiết")
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HorizontalDivider()
                    ConfigurationRow("Mã mô hình", model.pairId)
                    ConfigurationRow("Kiến trúc", model.studentName)
                    ConfigurationRow("Số tham số", parameters)
                    ConfigurationRow("Độ chính xác", precision)
                    ConfigurationRow("Encoder", model.encoderAsset ?: "baseline_encoder_fp32.onnx")
                    ConfigurationRow("Decoder", model.decoderAsset ?: "baseline_decoder_fp32.onnx")
                    ConfigurationRow("Từ vựng", vocabulary)
                    ConfigurationRow("Đầu vào", "float32 [1,3,224,224]")
                    ConfigurationRow("Visual memory", memory)
                    ConfigurationRow("Decoder logits", logits)
                    ConfigurationRow("Beam search", "beam 3 · tối đa 31 tokens")
                    model.validationCiderD?.let {
                        ConfigurationRow("Validation CIDEr-D", "%.4f".format(it))
                    }
                    model.validationBleu4?.let {
                        ConfigurationRow("Validation BLEU-4", "%.4f".format(it))
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
    onModelSelected: (CaptionModelMode) -> Unit,
) {
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
                text = "Chọn mô hình chạy trên thiết bị",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "V1 được giữ làm fallback. Ba lựa chọn V2 là artifact " +
                    "INT8 đã vượt quality gate trên Karpathy Validation.",
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
) {
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
            Text("Thiết bị hiện tại", fontWeight = FontWeight.Bold)
            Text(
                "RAM: ${"%.1f".format(availableRamGb)} / " +
                    "${"%.1f".format(totalRamGb)} GiB khả dụng",
            )
            Text("CPU: $cpuCores logical cores")
            Text("Ứng dụng: ${"%.1f".format(appPssMb)} MiB PSS")
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
    onOpenMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            title = "Quản trị",
            onOpenMenu = onOpenMenu,
            onLogin = {},
        )
        return
    }
    LaunchedEffect(session.account?.id) {
        val result = store.refreshAdminCache()
        users = store.listManagedUsers()
        activities = store.listActivities()
        if (!result.success) message = result.message
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
                title = "Quản trị người dùng",
                subtitle = "${users.size} tài khoản · RBAC",
                onOpenMenu = onOpenMenu,
            )
        }
        item {
            AdminStatisticsCard(users = users, activities = activities)
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
                label = { Text("Tìm người dùng hoặc hoạt động") },
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
                                contentDescription = "Xóa tìm kiếm",
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
                        label = { Text(filter.label) },
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
                                "${activities.count { it.userId == user.id }} hoạt động"
                            } else {
                                "Đã khóa · không thể đăng nhập"
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
            item { Text("Không tìm thấy người dùng phù hợp.") }
        } else {
            item {
                AdminPager(
                    page = userPage,
                    pages = userPages,
                    onPrevious = { userPage-- },
                    onNext = { userPage++ },
                )
            }
        }
        item {
            Text(
                "Hoạt động gần đây",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (filteredActivities.isEmpty()) {
            item {
                Text(
                    "Không có hoạt động phù hợp.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(visibleActivities, key = UserActivityEvent::id) { event ->
                ActivityEventCard(event)
            }
            item {
                AdminPager(
                    page = activityPage,
                    pages = activityPages,
                    onPrevious = { activityPage-- },
                    onNext = { activityPage++ },
                )
            }
        }
    }
    selected?.let { user ->
        PermissionDialog(
            user = user,
            onDismiss = { selected = null },
            onEdit = {
                editing = user
                selected = null
            },
            onSave = { permissions ->
                scope.launch {
                    val result = store.updatePermissionsCentral(user.id, permissions)
                    message = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    users = store.listManagedUsers()
                    activities = store.listActivities()
                    if (result.success) selected = null
                }
            },
            onSetActive = { active ->
                scope.launch {
                    val result = store.setUserActiveCentral(user.id, active)
                    message = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
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
            onDismiss = { editing = null },
            onSave = { displayName, password ->
                scope.launch {
                    val result = store.updateUserProfileCentral(
                        userId = user.id,
                        displayName = displayName,
                        password = password,
                    )
                    message = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    users = store.listManagedUsers()
                    activities = store.listActivities()
                    if (result.success) editing = null
                }
            },
        )
    }
}

private const val AdminUserPageSize = 5
private const val AdminActivityPageSize = 10

private enum class AdminActivityFilter(val label: String) {
    ALL("Tất cả"),
    AUTH("Đăng nhập"),
    CAPTION("Chú thích"),
    ACCESS("Quyền"),
    ACCOUNT("Tài khoản");

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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, enabled = page > 1) {
            Icon(
                painter = painterResource(R.drawable.ic_previous),
                contentDescription = "Trang trước",
            )
        }
        Text("Trang $page / $pages", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onNext, enabled = page < pages) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = "Trang sau",
            )
        }
    }
}

@Composable
private fun AdminStatisticsCard(
    users: List<UserAccount>,
    activities: List<UserActivityEvent>,
) {
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
            Text("Tổng quan hoạt động", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AdminMetric("Người dùng", authenticatedUsers.toString())
                AdminMetric("Đăng nhập", signIns.toString())
                AdminMetric("Tạo mô tả", captions.toString())
                AdminMetric("Tổng sự kiện", activities.size.toString())
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
private fun ActivityEventCard(event: UserActivityEvent) {
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
            Text(event.action.replace('_', ' '), color = MaterialTheme.colorScheme.primary)
            Text(event.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionDialog(
    user: UserAccount,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onSetActive: (Boolean) -> Unit,
) {
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
                        if (user.active) "Đang hoạt động" else "Đã khóa",
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
                                Text("Chỉnh sửa")
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
                                        "Khóa người dùng"
                                    } else {
                                        "Mở khóa người dùng"
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Đóng",
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
                                "TÀI NGUYÊN: ${resource.name}",
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
                                        "Bỏ chọn"
                                    } else {
                                        "Chọn tất cả"
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
                Text("Lưu quyền")
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun UserEditDialog(
    user: UserAccount,
    onDismiss: () -> Unit,
    onSave: (displayName: String, password: String?) -> Unit,
) {
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
                    text = "Chỉnh sửa tài khoản",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Đóng",
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
                    label = { Text("Họ và tên") },
                )
                OutlinedTextField(
                    value = user.username,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    label = { Text("Username · Không thể thay đổi") },
                )
                OutlinedTextField(
                    value = user.email.orEmpty(),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    label = { Text("Email · Không thể thay đổi") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Mật khẩu mới · Không bắt buộc") },
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
                                    "Ẩn mật khẩu"
                                } else {
                                    "Hiện mật khẩu"
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
                    label = { Text("Nhập lại mật khẩu mới") },
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
                                    "Ẩn mật khẩu"
                                } else {
                                    "Hiện mật khẩu"
                                },
                            )
                        }
                    },
                )
                if (!strongPassword) {
                    Text(
                        text = "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và số.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (password != confirmPassword) {
                    Text(
                        text = "Hai mật khẩu chưa trùng nhau.",
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
                Text("Lưu thay đổi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
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
        // Reserve one fixed row for the language and theme controls.
        Spacer(Modifier.size(104.dp))
    }
}
