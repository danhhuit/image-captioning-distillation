package com.klcn.mobilecaptioning

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.launch

internal enum class AppDestination {
    Capture,
    History,
    Favorites,
    Settings,
}

private enum class HistoryDateFilter {
    All,
    Today,
    Yesterday,
    Last7Days,
    Last30Days,
    ThisMonth,
    ThisYear,
}

private val VisionCyan = Color(0xFF2563EB)
private val VisionBlue = Color(0xFF2563EB)
private val VisionPurple = Color(0xFF2563EB)
private val VisionPink = Color(0xFFDC2626)
private const val HistoryPageSize = 10

private data class VisionStrings(
    val capture: String,
    val history: String,
    val favorites: String,
    val settings: String,
    val appTagline: String,
    val modelReady: String,
    val loadingModel: String,
    val gallery: String,
    val camera: String,
    val removePhoto: String,
    val choosePhoto: String,
    val choosePhotoHint: String,
    val processing: String,
    val resultTitle: String,
    val copy: String,
    val copied: String,
    val share: String,
    val details: String,
    val hideDetails: String,
    val historyTitle: String,
    val historySubtitle: String,
    val favoriteTitle: String,
    val favoriteSubtitle: String,
    val emptyHistory: String,
    val emptyHistoryHint: String,
    val emptyFavorites: String,
    val emptyFavoritesHint: String,
    val noFilteredResults: String,
    val noFilteredResultsHint: String,
    val delete: String,
    val clearHistory: String,
    val clearConfirmTitle: String,
    val clearConfirmBody: String,
    val cancel: String,
    val clear: String,
    val appearance: String,
    val appearanceHint: String,
    val system: String,
    val light: String,
    val dark: String,
    val language: String,
    val languageHint: String,
    val vietnamese: String,
    val english: String,
    val modelLanguageNotice: String,
    val privacy: String,
    val privacyBody: String,
    val about: String,
    val aboutBody: String,
    val version: String,
    val dismiss: String,
    val words: String,
    val repeatable: String,
    val all: String,
    val today: String,
    val yesterday: String,
    val last7Days: String,
    val last30Days: String,
    val thisMonth: String,
    val thisYear: String,
    val search: String,
    val searchHint: String,
    val page: String,
    val of: String,
    val previous: String,
    val next: String,
    val guest: String,
    val loginSoon: String,
    val logoutSoon: String,
)

private fun visionStrings(language: AppLanguage): VisionStrings =
    if (language == AppLanguage.Vietnamese) {
        VisionStrings(
            capture = "Chụp",
            history = "Lịch sử",
            favorites = "Yêu thích",
            settings = "Cài đặt",
            appTagline = "Mô tả hình ảnh trực tiếp trên thiết bị",
            modelReady = "Mô hình sẵn sàng",
            loadingModel = "Đang khởi tạo mô hình trên thiết bị…",
            gallery = "Thư viện",
            camera = "Máy ảnh",
            removePhoto = "Xóa ảnh hiện tại",
            choosePhoto = "Chọn một khoảnh khắc",
            choosePhotoHint = "Ảnh được xử lý hoàn toàn trên thiết bị",
            processing = "Mô hình đang phân tích và tạo mô tả…",
            resultTitle = "Kết quả mô tả",
            copy = "Sao chép",
            copied = "Đã chép",
            share = "Chia sẻ",
            details = "Chi tiết kỹ thuật",
            hideDetails = "Ẩn chi tiết",
            historyTitle = "Lịch sử",
            historySubtitle = "Những khoảnh khắc bạn đã khám phá",
            favoriteTitle = "Yêu thích",
            favoriteSubtitle = "Các mô tả bạn muốn lưu lại",
            emptyHistory = "Chưa có lịch sử",
            emptyHistoryHint = "Chọn hoặc chụp một ảnh để tạo mô tả đầu tiên.",
            emptyFavorites = "Chưa có mục yêu thích",
            emptyFavoritesHint = "Chạm biểu tượng trái tim trên một kết quả để lưu.",
            noFilteredResults = "Không có kết quả phù hợp",
            noFilteredResultsHint = "Hãy chọn một khoảng thời gian khác.",
            delete = "Xóa",
            clearHistory = "Xóa toàn bộ lịch sử",
            clearConfirmTitle = "Xóa toàn bộ lịch sử?",
            clearConfirmBody = "Ảnh thu nhỏ và mô tả đã lưu sẽ bị xóa khỏi thiết bị.",
            cancel = "Hủy",
            clear = "Xóa",
            appearance = "Giao diện",
            appearanceHint = "Chọn chế độ hiển thị phù hợp",
            system = "Hệ thống",
            light = "Sáng",
            dark = "Tối",
            language = "Ngôn ngữ",
            languageHint = "Ngôn ngữ của giao diện ứng dụng",
            vietnamese = "Tiếng Việt",
            english = "English",
            modelLanguageNotice = "Mô hình hiện sinh mô tả bằng tiếng Anh; tùy chọn này chỉ đổi ngôn ngữ giao diện.",
            privacy = "Quyền riêng tư",
            privacyBody = "Ảnh và mô tả được xử lý, lưu cục bộ. Ứng dụng không cần gửi ảnh lên máy chủ.",
            about = "Giới thiệu",
            aboutBody = "Image Captioning chạy ngoại tuyến với Visual Encoder, Transformer Decoder và Beam Search.",
            version = "Phiên bản 1.3.0 · Baseline FP32",
            dismiss = "Đóng",
            words = "từ",
            repeatable = "Kết quả tái lập",
            all = "Tất cả",
            today = "Hôm nay",
            yesterday = "Hôm qua",
            last7Days = "7 ngày",
            last30Days = "30 ngày",
            thisMonth = "Tháng này",
            thisYear = "Năm nay",
            search = "Tìm kiếm",
            searchHint = "Tìm theo nội dung mô tả…",
            page = "Trang",
            of = "trên",
            previous = "Trang trước",
            next = "Trang sau",
            guest = "Người dùng khách",
            loginSoon = "Đăng nhập · Sắp có",
            logoutSoon = "Đăng xuất · Sắp có",
        )
    } else {
        VisionStrings(
            capture = "Capture",
            history = "History",
            favorites = "Favorites",
            settings = "Settings",
            appTagline = "On-device image descriptions",
            modelReady = "Model ready",
            loadingModel = "Starting the on-device model…",
            gallery = "Gallery",
            camera = "Camera",
            removePhoto = "Remove current image",
            choosePhoto = "Choose a moment",
            choosePhotoHint = "Your image stays and runs on this device",
            processing = "The model is analyzing and writing a caption…",
            resultTitle = "Caption result",
            copy = "Copy",
            copied = "Copied",
            share = "Share",
            details = "Technical details",
            hideDetails = "Hide details",
            historyTitle = "History",
            historySubtitle = "Moments you have explored",
            favoriteTitle = "Favorites",
            favoriteSubtitle = "Captions worth keeping",
            emptyHistory = "No history yet",
            emptyHistoryHint = "Choose or capture an image to create your first caption.",
            emptyFavorites = "No favorites yet",
            emptyFavoritesHint = "Tap the heart on a result to save it here.",
            noFilteredResults = "No matching results",
            noFilteredResultsHint = "Try another time range.",
            delete = "Delete",
            clearHistory = "Clear all history",
            clearConfirmTitle = "Clear all history?",
            clearConfirmBody = "Saved captions and thumbnails will be removed from this device.",
            cancel = "Cancel",
            clear = "Clear",
            appearance = "Appearance",
            appearanceHint = "Choose how Vision Caption looks",
            system = "System",
            light = "Light",
            dark = "Dark",
            language = "Language",
            languageHint = "Language used by the application interface",
            vietnamese = "Tiếng Việt",
            english = "English",
            modelLanguageNotice = "The current model generates English captions; this setting changes the interface language only.",
            privacy = "Privacy",
            privacyBody = "Images and captions are processed and stored locally. No image upload is required.",
            about = "About",
            aboutBody = "Offline Image Captioning powered by a Visual Encoder, Transformer Decoder and Beam Search.",
            version = "Version 1.3.0 · Baseline FP32",
            dismiss = "Dismiss",
            words = "words",
            repeatable = "Repeatable result",
            all = "All",
            today = "Today",
            yesterday = "Yesterday",
            last7Days = "Last 7 days",
            last30Days = "Last 30 days",
            thisMonth = "This month",
            thisYear = "This year",
            search = "Search",
            searchHint = "Search caption content…",
            page = "Page",
            of = "of",
            previous = "Previous page",
            next = "Next page",
            guest = "Guest user",
            loginSoon = "Sign in · Coming soon",
            logoutSoon = "Sign out · Coming soon",
        )
    }

@Composable
internal fun VisionCaptionApp(
    selectedDestination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    uiState: CaptionUiState,
    selectedBitmap: Bitmap?,
    historyItems: List<CaptionHistoryItem>,
    themeMode: AppThemeMode,
    language: AppLanguage,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onChooseGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onClearCurrentImage: () -> Unit,
    onDismissError: () -> Unit,
    onToggleCurrentFavorite: (String) -> Unit,
    onToggleHistoryFavorite: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = visionStrings(language)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        coroutineScope.launch { drawerState.open() }
        Unit
    }
    val selectDestination: (AppDestination) -> Unit = { destination ->
        onDestinationChange(destination)
        coroutineScope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            VisionDrawer(
                labels = labels,
                selected = selectedDestination,
                onDestinationSelected = selectDestination,
            )
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                VisionNavigationBar(
                    selected = selectedDestination,
                    labels = labels,
                    onSelected = onDestinationChange,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
            ) {
                when (selectedDestination) {
                    AppDestination.Capture -> CaptureDestination(
                        labels = labels,
                        uiState = uiState,
                        selectedBitmap = selectedBitmap,
                        onOpenMenu = openDrawer,
                        onChooseGallery = onChooseGallery,
                        onOpenCamera = onOpenCamera,
                        onClearCurrentImage = onClearCurrentImage,
                        onDismissError = onDismissError,
                        onToggleFavorite = onToggleCurrentFavorite,
                    )

                    AppDestination.History -> HistoryDestination(
                        labels = labels,
                        items = historyItems,
                        favoritesOnly = false,
                        onOpenMenu = openDrawer,
                        onToggleFavorite = onToggleHistoryFavorite,
                        onDelete = onDeleteHistoryItem,
                        onCapture = {
                            onDestinationChange(AppDestination.Capture)
                        },
                    )

                    AppDestination.Favorites -> HistoryDestination(
                        labels = labels,
                        items = historyItems.filter(
                            CaptionHistoryItem::favorite,
                        ),
                        favoritesOnly = true,
                        onOpenMenu = openDrawer,
                        onToggleFavorite = onToggleHistoryFavorite,
                        onDelete = onDeleteHistoryItem,
                        onCapture = {
                            onDestinationChange(AppDestination.Capture)
                        },
                    )

                    AppDestination.Settings -> SettingsDestination(
                        labels = labels,
                        historyCount = historyItems.size,
                        themeMode = themeMode,
                        language = language,
                        onOpenMenu = openDrawer,
                        onThemeModeChange = onThemeModeChange,
                        onLanguageChange = onLanguageChange,
                        onClearHistory = onClearHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionDrawer(
    labels: VisionStrings,
    selected: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val entries = listOf(
        Triple(AppDestination.Capture, labels.capture, R.drawable.ic_capture),
        Triple(AppDestination.History, labels.history, R.drawable.ic_history),
        Triple(
            AppDestination.Favorites,
            labels.favorites,
            R.drawable.ic_favorite_border,
        ),
        Triple(AppDestination.Settings, labels.settings, R.drawable.ic_settings),
    )
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.vision_logo),
                    contentDescription = "Vision Caption",
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Vision Caption",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = labels.guest,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            NavigationDrawerItem(
                label = { Text(labels.loginSoon) },
                selected = false,
                onClick = {},
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                    )
                },
            )
            entries.forEach { (destination, label, icon) ->
                NavigationDrawerItem(
                    label = { Text(label) },
                    selected = destination == selected,
                    onClick = {
                        onDestinationSelected(destination)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                        )
                    },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            NavigationDrawerItem(
                label = { Text(labels.logoutSoon) },
                selected = false,
                onClick = {},
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_logout),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun VisionNavigationBar(
    selected: AppDestination,
    labels: VisionStrings,
    onSelected: (AppDestination) -> Unit,
) {
    val entries = listOf(
        Triple(AppDestination.Capture, labels.capture, R.drawable.ic_capture),
        Triple(AppDestination.History, labels.history, R.drawable.ic_history),
        Triple(AppDestination.Favorites, labels.favorites, R.drawable.ic_favorite),
        Triple(AppDestination.Settings, labels.settings, R.drawable.ic_settings),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 10.dp,
    ) {
        entries.forEach { (destination, label, icon) ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        fontSize = 11.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VisionCyan,
                    selectedTextColor = VisionCyan,
                    indicatorColor = VisionBlue.copy(alpha = 0.18f),
                ),
            )
        }
    }
}

@Composable
private fun CaptureDestination(
    labels: VisionStrings,
    uiState: CaptionUiState,
    selectedBitmap: Bitmap?,
    onOpenMenu: () -> Unit,
    onChooseGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onClearCurrentImage: () -> Unit,
    onDismissError: () -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VisionHeader(onOpenMenu)
        }
        item {
            CaptureCanvas(
                labels = labels,
                bitmap = selectedBitmap,
                isProcessing = uiState is CaptionUiState.Processing,
                onClearCurrentImage = onClearCurrentImage,
            )
        }
        item {
            CaptureActions(
                labels = labels,
                enabled = uiState !is CaptionUiState.LoadingModel &&
                    uiState !is CaptionUiState.Processing,
                onChooseGallery = onChooseGallery,
                onOpenCamera = onOpenCamera,
            )
        }
        when (uiState) {
            is CaptionUiState.Success -> item {
                CaptionResultCard(
                    labels = labels,
                    result = uiState.result,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            is CaptionUiState.Error -> item {
                ErrorCard(
                    state = uiState,
                    dismissLabel = labels.dismiss,
                    onDismiss = onDismissError,
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun VisionHeader(
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenMenu) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Menu",
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier
                .size(56.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(18.dp),
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 2.dp,
        ) {
            Image(
                painter = painterResource(R.drawable.vision_logo),
                contentDescription = "Vision Caption",
                modifier = Modifier.padding(7.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun CaptureCanvas(
    labels: VisionStrings,
    bitmap: Bitmap?,
    isProcessing: Boolean,
    onClearCurrentImage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(30.dp),
            ),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = labels.choosePhoto,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.12f),
                                ),
                            ),
                        ),
                )
                if (!isProcessing) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable(onClick = onClearCurrentImage),
                        color = Color.Black.copy(alpha = 0.66f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = labels.removePhoto,
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(84.dp),
                        color = VisionBlue.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_capture),
                            contentDescription = null,
                            tint = VisionCyan,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    Text(
                        text = labels.choosePhoto,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = labels.choosePhotoHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = VisionCyan,
                            trackColor = Color.White.copy(alpha = 0.18f),
                        )
                        Text(
                            text = labels.processing,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureActions(
    labels: VisionStrings,
    enabled: Boolean,
    onChooseGallery: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onChooseGallery,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gallery),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(labels.gallery, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onOpenCamera,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VisionBlue,
                contentColor = Color.White,
            ),
            modifier = Modifier
                .weight(1f)
                .height(58.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(labels.camera, fontWeight = FontWeight.Bold)
        }
    }
    if (!enabled) {
        Spacer(Modifier.height(9.dp))
        Text(
            text = labels.loadingModel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CaptionResultCard(
    labels: VisionStrings,
    result: CaptionPresentation,
    onToggleFavorite: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember(result.caption) { mutableStateOf(false) }
    var detailsExpanded by remember(result.caption) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(26.dp),
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = "✦",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = labels.resultTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${result.wordCount} ${labels.words} · Beam-3",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.historyId != null) {
                    IconButton(
                        onClick = {
                            onToggleFavorite(result.historyId)
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (result.favorite) {
                                    R.drawable.ic_favorite
                                } else {
                                    R.drawable.ic_favorite_border
                                },
                            ),
                            contentDescription = labels.favorites,
                            tint = if (result.favorite) {
                                VisionPink
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = result.caption,
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(result.caption))
                        copied = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(if (copied) labels.copied else labels.copy)
                }
                Button(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result.caption)
                        }
                        context.startActivity(
                            Intent.createChooser(share, labels.share),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(labels.share)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { detailsExpanded = !detailsExpanded }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (detailsExpanded) {
                        labels.hideDetails
                    } else {
                        labels.details
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = VisionCyan,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (detailsExpanded) "⌃" else "⌄",
                    color = VisionCyan,
                    fontSize = 20.sp,
                )
            }
            AnimatedVisibility(detailsExpanded) {
                TechnicalGrid(labels, result)
            }
        }
    }
}

@Composable
private fun TechnicalGrid(
    labels: VisionStrings,
    result: CaptionPresentation,
) {
    val speedup = if (result.repeatedTotalMs > 0.0) {
        result.firstTotalMs / result.repeatedTotalMs
    } else {
        0.0
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        MetricSectionTitle("MODEL & RUNTIME")
        TechnicalRow("Model", "Baseline FP32 fallback")
        TechnicalRow("Runtime", "ONNX Runtime Android 1.29.0")
        TechnicalRow("Execution provider", "CPUExecutionProvider")
        TechnicalRow("Deployment parameters", "8,480,816")
        TechnicalRow("Encoder ONNX", "baseline_encoder_fp32.onnx")
        TechnicalRow("Decoder ONNX", "baseline_decoder_fp32.onnx")
        TechnicalRow("Vocabulary", "12,293 tokens")
        TechnicalRow("Processing", "Offline · on-device")

        MetricSectionTitle("INPUT & PREPROCESSING")
        TechnicalRow(
            "Source image",
            "${result.sourceWidth} × ${result.sourceHeight} · ${result.source.label}",
        )
        TechnicalRow("Resize policy", "shortest side 224 · bicubic")
        TechnicalRow("Center crop", "224 × 224")
        TechnicalRow("Normalization", "ImageNet mean / std")
        TechnicalRow("Tensor layout", "NCHW")
        TechnicalRow("Encoder input", "float32 [1,3,224,224]")
        TechnicalRow("Visual memory", "float32 [1,1,256]")

        MetricSectionTitle("DECODER & BEAM SEARCH")
        TechnicalRow("Decoder input IDs", "int64 [1,31]")
        TechnicalRow("Attention mask", "int64 [1,31]")
        TechnicalRow("Decoder logits", "float32 [1,31,12293]")
        TechnicalRow("Beam size", "3")
        TechnicalRow("Maximum new tokens", "31")
        TechnicalRow("Minimum words", "2")
        TechnicalRow("Length penalty", "0.7")
        TechnicalRow("Forbidden token IDs", "[0, 1, 3]")
        TechnicalRow("BOS / EOS / PAD", "1 / 2 / 0")

        MetricSectionTitle("CAPTION OUTPUT")
        TechnicalRow("Word count", result.wordCount.toString())
        TechnicalRow("Generated token count", result.tokenIds.size.toString())
        TechnicalRow("EOS position", result.eosPosition.toString())
        TechnicalRow("Has EOS", if (result.eosPosition >= 0) "true" else "false")
        TechnicalRow(
            "Raw score",
            String.format(Locale.US, "%.6f", result.rawScore),
        )
        TechnicalRow(
            "Normalized score",
            String.format(Locale.US, "%.6f", result.normalizedScore),
        )
        Text(
            text = "Token IDs:\n${result.tokenIds.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )

        MetricSectionTitle("LATENCY")
        TechnicalRow("Model load", formatMs(result.modelLoadMs))
        TechnicalRow("Preprocessing", formatMs(result.preprocessingMs))
        TechnicalRow("Encoder", formatMs(result.encoderMs))
        TechnicalRow("Beam-3 decoder", formatMs(result.beamMs))
        TechnicalRow(
            "Inference run 1",
            formatMs(result.firstTotalMs),
        )
        TechnicalRow(
            "Inference run 2",
            formatMs(result.repeatedTotalMs),
        )
        TechnicalRow(
            "Warm-session speedup",
            String.format(Locale.US, "%.2f×", speedup),
        )
        TechnicalRow(
            labels.repeatable,
            if (result.repeatabilityVerified) "✓ true" else "false",
        )
    }
}

@Composable
private fun MetricSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = VisionCyan,
        modifier = Modifier.padding(top = 7.dp),
    )
}

@Composable
private fun TechnicalRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ErrorCard(
    state: CaptionUiState.Error,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FilledTonalButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDestination(
    labels: VisionStrings,
    items: List<CaptionHistoryItem>,
    favoritesOnly: Boolean,
    onOpenMenu: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCapture: () -> Unit,
) {
    var selectedFilter by remember(favoritesOnly) {
        mutableStateOf(HistoryDateFilter.All)
    }
    var searchVisible by remember(favoritesOnly) { mutableStateOf(false) }
    var searchQuery by remember(favoritesOnly) { mutableStateOf("") }
    var currentPage by remember(favoritesOnly) { mutableStateOf(1) }
    val filteredItems = remember(items, selectedFilter, searchQuery) {
        val normalizedQuery = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchesDate = historyItemMatchesFilter(
                item.createdAtMillis,
                selectedFilter,
            )
            val matchesSearch = normalizedQuery.isEmpty() ||
                item.caption.lowercase().contains(normalizedQuery) ||
                item.tokenIds.joinToString(" ").contains(normalizedQuery)
            matchesDate && matchesSearch
        }
    }
    val totalPages = max(
        1,
        ceil(filteredItems.size / HistoryPageSize.toDouble()).toInt(),
    )
    LaunchedEffect(selectedFilter, searchQuery, items.size) {
        currentPage = 1
    }
    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) {
            currentPage = totalPages
        }
    }
    val pageItems = filteredItems
        .drop((currentPage - 1) * HistoryPageSize)
        .take(HistoryPageSize)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 22.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle(
                title = if (favoritesOnly) {
                    labels.favoriteTitle
                } else {
                    labels.historyTitle
                },
                subtitle = if (favoritesOnly) {
                    labels.favoriteSubtitle
                } else {
                    labels.historySubtitle
                },
                count = filteredItems.size,
                onOpenMenu = onOpenMenu,
                onSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) {
                        searchQuery = ""
                    }
                },
            )
        }
        item {
            AnimatedVisibility(searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(labels.search) },
                    placeholder = { Text(labels.searchHint) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.ic_close,
                                    ),
                                    contentDescription = labels.clear,
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
        item {
            HistoryFilterRow(
                selected = selectedFilter,
                labels = labels,
                onSelected = { selectedFilter = it },
            )
        }
        if (filteredItems.isEmpty()) {
            item {
                EmptyCollection(
                    icon = if (favoritesOnly) {
                        R.drawable.ic_favorite_border
                    } else {
                        R.drawable.ic_history
                    },
                    title = if (
                        items.isNotEmpty() &&
                        selectedFilter != HistoryDateFilter.All
                    ) {
                        labels.noFilteredResults
                    } else if (favoritesOnly) {
                        labels.emptyFavorites
                    } else {
                        labels.emptyHistory
                    },
                    hint = if (
                        items.isNotEmpty() &&
                        selectedFilter != HistoryDateFilter.All
                    ) {
                        labels.noFilteredResultsHint
                    } else if (favoritesOnly) {
                        labels.emptyFavoritesHint
                    } else {
                        labels.emptyHistoryHint
                    },
                    buttonLabel = labels.capture,
                    onCapture = onCapture,
                )
            }
        } else {
            items(
                items = pageItems,
                key = CaptionHistoryItem::id,
            ) { item ->
                HistoryCard(
                    item = item,
                    labels = labels,
                    onToggleFavorite = onToggleFavorite,
                    onDelete = onDelete,
                )
            }
            item {
                PaginationBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    labels = labels,
                    onPrevious = {
                        currentPage = max(1, currentPage - 1)
                    },
                    onNext = {
                        currentPage = minOf(
                            totalPages,
                            currentPage + 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    labels: VisionStrings,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentPage > 1,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_previous),
                    contentDescription = labels.previous,
                )
            }
            Text(
                text = "${labels.page} $currentPage ${labels.of} $totalPages",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = onNext,
                enabled = currentPage < totalPages,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_next),
                    contentDescription = labels.next,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterRow(
    selected: HistoryDateFilter,
    labels: VisionStrings,
    onSelected: (HistoryDateFilter) -> Unit,
) {
    val filters = listOf(
        HistoryDateFilter.All to labels.all,
        HistoryDateFilter.Today to labels.today,
        HistoryDateFilter.Yesterday to labels.yesterday,
        HistoryDateFilter.Last7Days to labels.last7Days,
        HistoryDateFilter.Last30Days to labels.last30Days,
        HistoryDateFilter.ThisMonth to labels.thisMonth,
        HistoryDateFilter.ThisYear to labels.thisYear,
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        items(filters) { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    subtitle: String,
    count: Int,
    onOpenMenu: () -> Unit,
    onSearch: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenMenu) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Menu",
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = VisionBlue.copy(alpha = 0.14f),
            shape = CircleShape,
        ) {
            Text(
                text = count.toString(),
                color = VisionCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: CaptionHistoryItem,
    labels: VisionStrings,
    onToggleFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val thumbnail = remember(item.thumbnailPath) {
        BitmapFactory.decodeFile(item.thumbnailPath)
    }
    var detailsExpanded by remember(item.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = item.caption,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_gallery),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.caption,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = historyMetadata(item, labels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { onToggleFavorite(item.id) }) {
                        Icon(
                            painter = painterResource(
                                if (item.favorite) {
                                    R.drawable.ic_favorite
                                } else {
                                    R.drawable.ic_favorite_border
                                },
                            ),
                            contentDescription = labels.favorites,
                            tint = if (item.favorite) {
                                VisionPink
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = labels.delete,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        detailsExpanded = !detailsExpanded
                    }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (detailsExpanded) {
                        labels.hideDetails
                    } else {
                        labels.details
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = VisionCyan,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (detailsExpanded) "⌃" else "⌄",
                    color = VisionCyan,
                )
            }
            AnimatedVisibility(detailsExpanded) {
                HistoryTechnicalGrid(
                    item = item,
                    labels = labels,
                    modifier = Modifier.padding(
                        start = 15.dp,
                        end = 15.dp,
                        bottom = 16.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HistoryTechnicalGrid(
    item: CaptionHistoryItem,
    labels: VisionStrings,
    modifier: Modifier = Modifier,
) {
    val hasDetailedAudit = item.tokenIds.isNotEmpty()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MetricSectionTitle("CAPTION")
        TechnicalRow("Created", historyTimestamp(item, labels))
        TechnicalRow("Source", historySourceLabel(item, labels))
        TechnicalRow(
            "Source image",
            if (item.sourceWidth > 0 && item.sourceHeight > 0) {
                "${item.sourceWidth} × ${item.sourceHeight}"
            } else {
                "Legacy record"
            },
        )
        TechnicalRow("Word count", item.wordCount.toString())
        TechnicalRow(
            "Token count",
            if (hasDetailedAudit) {
                item.tokenIds.size.toString()
            } else {
                legacyUnavailable(labels)
            },
        )
        TechnicalRow(
            "EOS position",
            if (hasDetailedAudit) {
                item.eosPosition.toString()
            } else {
                legacyUnavailable(labels)
            },
        )
        TechnicalRow(
            "Raw score",
            if (hasDetailedAudit) {
                String.format(Locale.US, "%.6f", item.rawScore)
            } else {
                legacyUnavailable(labels)
            },
        )
        TechnicalRow(
            "Normalized score",
            if (hasDetailedAudit) {
                String.format(Locale.US, "%.6f", item.normalizedScore)
            } else {
                legacyUnavailable(labels)
            },
        )
        if (item.tokenIds.isNotEmpty()) {
            Text(
                text = "Token IDs:\n${item.tokenIds.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        MetricSectionTitle("MODEL & TENSORS")
        TechnicalRow("Model", "Baseline FP32 · ORT Android")
        TechnicalRow("Encoder input", "float32 [1,3,224,224]")
        TechnicalRow("Visual memory", "float32 [1,1,256]")
        TechnicalRow("Decoder logits", "float32 [1,31,12293]")
        TechnicalRow("Beam configuration", "3 · LP 0.7 · min 2")
        MetricSectionTitle("LATENCY")
        TechnicalRow(
            "Model load",
            formatStoredMs(item.modelLoadMs, labels),
        )
        TechnicalRow(
            "Preprocessing",
            formatStoredMs(item.preprocessingMs, labels),
        )
        TechnicalRow(
            "Encoder",
            formatStoredMs(item.encoderMs, labels),
        )
        TechnicalRow(
            "Beam-3 decoder",
            formatStoredMs(item.beamMs, labels),
        )
        TechnicalRow(
            "Inference run 1",
            formatStoredMs(item.firstTotalMs, labels),
        )
        TechnicalRow(
            "Inference run 2",
            formatStoredMs(item.repeatedTotalMs, labels),
        )
        TechnicalRow(
            labels.repeatable,
            if (item.repeatabilityVerified) "✓ true" else "false",
        )
    }
}

@Composable
private fun EmptyCollection(
    icon: Int,
    title: String,
    hint: String,
    buttonLabel: String,
    onCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 88.dp, start = 28.dp, end = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(86.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(25.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onCapture,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
        ) {
            Text(buttonLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDestination(
    labels: VisionStrings,
    historyCount: Int,
    themeMode: AppThemeMode,
    language: AppLanguage,
    onOpenMenu: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onClearHistory: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 22.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenTitle(
                title = labels.settings,
                subtitle = "Vision Caption",
                count = historyCount,
                onOpenMenu = onOpenMenu,
            )
        }
        item {
            SettingsCard(
                icon = R.drawable.ic_palette,
                title = labels.appearance,
                hint = labels.appearanceHint,
            ) {
                ChoiceRow {
                    ThemeChoice(
                        label = labels.system,
                        selected = themeMode == AppThemeMode.System,
                        onClick = {
                            onThemeModeChange(AppThemeMode.System)
                        },
                    )
                    ThemeChoice(
                        label = labels.light,
                        selected = themeMode == AppThemeMode.Light,
                        onClick = {
                            onThemeModeChange(AppThemeMode.Light)
                        },
                    )
                    ThemeChoice(
                        label = labels.dark,
                        selected = themeMode == AppThemeMode.Dark,
                        onClick = {
                            onThemeModeChange(AppThemeMode.Dark)
                        },
                    )
                }
            }
        }
        item {
            SettingsCard(
                icon = R.drawable.ic_language,
                title = labels.language,
                hint = labels.languageHint,
            ) {
                ChoiceRow {
                    ThemeChoice(
                        label = labels.vietnamese,
                        selected = language == AppLanguage.Vietnamese,
                        onClick = {
                            onLanguageChange(AppLanguage.Vietnamese)
                        },
                    )
                    ThemeChoice(
                        label = labels.english,
                        selected = language == AppLanguage.English,
                        onClick = {
                            onLanguageChange(AppLanguage.English)
                        },
                    )
                }
                Text(
                    text = labels.modelLanguageNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsCard(
                icon = R.drawable.ic_info,
                title = labels.privacy,
                hint = labels.privacyBody,
            )
        }
        item {
            SettingsCard(
                icon = R.drawable.ic_history,
                title = labels.historyTitle,
                hint = "$historyCount",
            ) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    enabled = historyCount > 0,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(labels.clearHistory)
                }
            }
        }
        item {
            AboutVisionCard(labels)
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(labels.clearConfirmTitle) },
            text = { Text(labels.clearConfirmBody) },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text(labels.cancel)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(labels.clear)
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    icon: Int,
    title: String,
    hint: String,
    content: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = VisionBlue.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = VisionCyan,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content?.invoke()
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.ThemeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = VisionBlue,
            contentColor = Color.White,
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    if (selected) {
        Button(
            onClick = onClick,
            colors = colors,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(label, maxLines = 1, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = colors,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(label, maxLines = 1, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AboutVisionCard(labels: VisionStrings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(26.dp),
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.vision_brand_full),
                contentDescription = "Vision Caption",
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(112.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = labels.aboutBody,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = labels.version,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = VisionCyan,
            )
        }
    }
}

private fun historyMetadata(
    item: CaptionHistoryItem,
    labels: VisionStrings,
): String =
    "${historySourceLabel(item, labels)} · ${historyTimestamp(item, labels)}"

private fun historyTimestamp(
    item: CaptionHistoryItem,
    labels: VisionStrings,
): String {
    val locale = if (labels.capture == "Chụp") {
        Locale.Builder()
            .setLanguage("vi")
            .setRegion("VN")
            .build()
    } else {
        Locale.US
    }
    return SimpleDateFormat("dd/MM/yyyy · HH:mm", locale)
        .format(Date(item.createdAtMillis))
}

private fun historySourceLabel(
    item: CaptionHistoryItem,
    labels: VisionStrings,
): String =
    if (labels.capture == "Chụp") {
        if (item.source == ImageInputSource.Camera) "Máy ảnh" else "Thư viện"
    } else {
        if (item.source == ImageInputSource.Camera) "Camera" else "Gallery"
    }

private fun historyItemMatchesFilter(
    createdAtMillis: Long,
    filter: HistoryDateFilter,
): Boolean {
    if (filter == HistoryDateFilter.All) {
        return true
    }
    val todayStart = calendarStartOfDay().timeInMillis
    val tomorrowStart = calendarStartOfDay().apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    return when (filter) {
        HistoryDateFilter.All -> true
        HistoryDateFilter.Today ->
            createdAtMillis in todayStart until tomorrowStart
        HistoryDateFilter.Yesterday -> {
            val yesterdayStart = calendarStartOfDay().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            createdAtMillis in yesterdayStart until todayStart
        }
        HistoryDateFilter.Last7Days -> {
            val start = calendarStartOfDay().apply {
                add(Calendar.DAY_OF_YEAR, -6)
            }.timeInMillis
            createdAtMillis in start until tomorrowStart
        }
        HistoryDateFilter.Last30Days -> {
            val start = calendarStartOfDay().apply {
                add(Calendar.DAY_OF_YEAR, -29)
            }.timeInMillis
            createdAtMillis in start until tomorrowStart
        }
        HistoryDateFilter.ThisMonth -> {
            val start = calendarStartOfDay().apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            createdAtMillis in start until tomorrowStart
        }
        HistoryDateFilter.ThisYear -> {
            val start = calendarStartOfDay().apply {
                set(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            createdAtMillis in start until tomorrowStart
        }
    }
}

private fun calendarStartOfDay(): Calendar =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun formatMs(value: Double): String =
    if (value >= 1000.0) {
        String.format(Locale.US, "%.2f s", value / 1000.0)
    } else {
        String.format(Locale.US, "%.1f ms", value)
    }

private fun formatStoredMs(
    value: Double,
    labels: VisionStrings,
): String =
    if (value > 0.0) formatMs(value) else legacyUnavailable(labels)

private fun legacyUnavailable(labels: VisionStrings): String =
    if (labels.capture == "Chụp") {
        "Không lưu ở bản cũ"
    } else {
        "Not stored in legacy record"
    }
