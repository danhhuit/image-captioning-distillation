package com.klcn.mobilecaptioning

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal enum class ImageInputSource(val label: String) {
    Gallery("Thư viện"),
    Camera("Camera"),
}

internal sealed interface CaptionUiState {
    data object LoadingModel : CaptionUiState

    data class Ready(
        val modelLoadMs: Double,
    ) : CaptionUiState

    data class Processing(
        val source: ImageInputSource,
    ) : CaptionUiState

    data class Success(
        val result: CaptionPresentation,
    ) : CaptionUiState

    data class Error(
        val title: String,
        val message: String,
    ) : CaptionUiState
}

internal data class CaptionPresentation(
    val source: ImageInputSource,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val caption: String,
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptionAppScreen(
    uiState: CaptionUiState,
    selectedBitmap: Bitmap?,
    onChooseGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = uiState is CaptionUiState.LoadingModel ||
        uiState is CaptionUiState.Processing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "AI",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Vision Caption",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Mô tả ảnh ngay trên thiết bị",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    ModelStatusBadge(uiState)
                    Spacer(Modifier.width(16.dp))
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard()
            }
            item {
                InputActionCard(
                    enabled = !isBusy,
                    onChooseGallery = onChooseGallery,
                    onOpenCamera = onOpenCamera,
                )
            }
            item {
                ImagePreviewCard(
                    bitmap = selectedBitmap,
                    source = when (uiState) {
                        is CaptionUiState.Processing -> uiState.source
                        is CaptionUiState.Success -> uiState.result.source
                        else -> null
                    },
                    isProcessing = uiState is CaptionUiState.Processing,
                )
            }
            item {
                when (uiState) {
                    CaptionUiState.LoadingModel -> LoadingModelCard()
                    is CaptionUiState.Ready -> ReadyCard(uiState.modelLoadMs)
                    is CaptionUiState.Processing -> ProcessingCard(uiState.source)
                    is CaptionUiState.Success -> ResultCard(uiState.result)
                    is CaptionUiState.Error -> ErrorCard(
                        title = uiState.title,
                        message = uiState.message,
                        onDismiss = onDismissError,
                    )
                }
            }
            item {
                PrivacyNote()
            }
        }
    }
}

@Composable
private fun ModelStatusBadge(state: CaptionUiState) {
    val ready = state !is CaptionUiState.LoadingModel &&
        state !is CaptionUiState.Error
    val color = if (ready) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (ready) "Sẵn sàng" else "Đang nạp",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun HeroCard() {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(gradient)
            .padding(24.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "Biến khoảnh khắc\nthành lời.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 35.sp,
            )
            Text(
                text = "Chọn hoặc chụp một bức ảnh. Mô hình AI sẽ tạo chú thích trong vài giây.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
            )
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
            ) {
                Text(
                    text = "Baseline FP32  •  Beam-3  •  Offline",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun InputActionCard(
    enabled: Boolean,
    onChooseGallery: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Thêm ảnh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Sử dụng ảnh có chủ thể rõ và đủ sáng để nhận kết quả tốt hơn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onChooseGallery,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gallery),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Thư viện", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onOpenCamera,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Camera", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(
    bitmap: Bitmap?,
    source: ImageInputSource?,
    isProcessing: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_gallery),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = "Ảnh xem trước sẽ xuất hiện tại đây",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Ảnh được chọn để tạo chú thích",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (source != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                    ) {
                        Text(
                            text = source.label,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingModelCard() {
    StatusCard(
        title = "Đang chuẩn bị mô hình",
        description = "Encoder, decoder và từ vựng đang được nạp một lần vào bộ nhớ.",
        showProgress = true,
    )
}

@Composable
private fun ReadyCard(modelLoadMs: Double) {
    StatusCard(
        title = "Sẵn sàng tạo chú thích",
        description = "Mô hình đã được nạp trong ${formatMilliseconds(modelLoadMs)}. Hãy chọn một ảnh để bắt đầu.",
        showProgress = false,
    )
}

@Composable
private fun ProcessingCard(source: ImageInputSource) {
    StatusCard(
        title = "AI đang quan sát ảnh",
        description = "Đang xử lý ảnh từ ${source.label}, trích xuất đặc trưng và tạo câu bằng Beam-3.",
        showProgress = true,
    )
}

@Composable
private fun StatusCard(
    title: String,
    description: String,
    showProgress: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: CaptionPresentation) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(result.caption) { mutableStateOf(false) }
    var showDetails by remember(result.caption) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Chú thích được tạo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "AI mô tả ảnh của bạn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Hoàn tất",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = "“${result.caption}”",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 31.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricChip(
                    label = "Từ",
                    value = result.wordCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "Thời gian",
                    value = formatMilliseconds(result.repeatedTotalMs),
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "EOS",
                    value = "Hợp lệ",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(result.caption))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        text = if (copied) "Đã sao chép" else "Sao chép caption",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(if (showDetails) "Ẩn chi tiết" else "Xem chi tiết")
                }
            }

            AnimatedVisibility(showDetails) {
                TechnicalDetails(result)
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TechnicalDetails(result: CaptionPresentation) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "Chi tiết kỹ thuật",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        val details = """
            Nguồn: ${result.source.label}
            Ảnh: ${result.sourceWidth} × ${result.sourceHeight}
            Tensor: [1, 3, 224, 224]
            Visual memory: [1, 1, 256]
            Token IDs: ${result.tokenIds}
            EOS position: ${result.eosPosition}
            Raw score: ${"%.6f".format(result.rawScore)}
            Normalized score: ${"%.6f".format(result.normalizedScore)}

            Model load: ${formatMilliseconds(result.modelLoadMs)}
            Preprocessing: ${formatMilliseconds(result.preprocessingMs)}
            Encoder: ${formatMilliseconds(result.encoderMs)}
            Beam-3: ${formatMilliseconds(result.beamMs)}
            Lượt đầu: ${formatMilliseconds(result.firstTotalMs)}
            Lượt xác minh: ${formatMilliseconds(result.repeatedTotalMs)}
            Repeatability: ${if (result.repeatabilityVerified) "Đạt" else "Không đạt"}
        """.trimIndent()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = details,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Thử lại")
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = "Riêng tư theo thiết kế",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Ảnh được xử lý hoàn toàn trên thiết bị và không tải lên máy chủ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMilliseconds(value: Double): String {
    return if (value >= 1_000.0) {
        "${"%.2f".format(value / 1_000.0)} giây"
    } else {
        "${"%.0f".format(value)} ms"
    }
}
