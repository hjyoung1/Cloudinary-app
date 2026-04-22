package com.cloudinaryfiles.app.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.model.Providers
import com.cloudinaryfiles.app.ui.components.AudioPlayerBar
import com.cloudinaryfiles.app.ui.components.FileCard
import com.cloudinaryfiles.app.ui.components.FilterBottomSheet
import com.cloudinaryfiles.app.ui.screens.FileViewerScreen
import com.cloudinaryfiles.app.ui.components.SelectionToolbar
import com.cloudinaryfiles.app.ui.components.formatDurationSec
import com.cloudinaryfiles.app.ui.theme.*
import com.cloudinaryfiles.app.ui.viewmodel.FilesViewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onNavigateToSetup: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    vm: FilesViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    val activeAccount = remember(state.accounts, state.activeAccountId) {
        state.accounts.firstOrNull { it.id == state.activeAccountId } ?: state.accounts.firstOrNull()
    }
    val provider = remember(activeAccount) {
        activeAccount?.providerKey?.let { Providers.find(it) }
    }
    val isAudioPlaying = state.currentlyPlayingId != null

    // Compute stats for drawer
    val totalSize = remember(state.allAssets) {
        val bytes = state.allAssets.sumOf { it.bytes }
        when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
            else                    -> "%.1f KB".format(bytes / 1024.0)
        }
    }
    val audioCount = remember(state.allAssets) { state.allAssets.count { it.isAudio } }
    val imageCount = remember(state.allAssets) { state.allAssets.count { it.isImage } }
    val videoCount = remember(state.allAssets) { state.allAssets.count { it.isVideo } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0E0C1C),
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // Header — app branding
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(
                            Brush.linearGradient(listOf(AudioAccent2, AudioAccent)),
                            RoundedCornerShape(14.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Cloud, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("CloudVault", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Multi-cloud file manager", style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.5f))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.White.copy(0.08f))

                // Active account — with Edit button
                if (activeAccount != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).background(
                                    MaterialTheme.colorScheme.primaryContainer, CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(provider?.emoji ?: "☁️", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(activeAccount.displayLabel, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                Text(provider?.label ?: "", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            // Edit button for active account
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    onEditAccount(activeAccount.id)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, "Edit account",
                                    tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                            }
                            Surface(color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                shape = RoundedCornerShape(6.dp)) {
                                Text("Active", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Stats
                if (state.allAssets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip("${state.allAssets.size}", "files", Modifier.weight(1f))
                        StatChip(totalSize, "total", Modifier.weight(1f))
                    }
                    if (audioCount > 0 || imageCount > 0 || videoCount > 0) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (audioCount > 0) StatChip("$audioCount", "audio", Modifier.weight(1f))
                            if (imageCount > 0) StatChip("$imageCount", "images", Modifier.weight(1f))
                            if (videoCount > 0) StatChip("$videoCount", "video", Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    color = Color.White.copy(0.08f))

                // Other accounts — switch + edit
                if (state.accounts.size > 1) {
                    Text("Switch Account", modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.accounts.filter { it.id != state.activeAccountId }.forEach { acct ->
                        val acctProvider = Providers.find(acct.providerKey)
                        NavigationDrawerItem(
                            icon = { Text(acctProvider.emoji, fontSize = 18.sp) },
                            label = {
                                Column {
                                    Text(acct.displayLabel, style = MaterialTheme.typography.bodyMedium)
                                    Text(acctProvider.label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            badge = {
                                IconButton(
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onEditAccount(acct.id)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp))
                                }
                            },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; vm.switchAccount(acct.id) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        color = Color.White.copy(0.08f))
                }

                // Add account
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary) },
                    label = { Text("Add Account", color = MaterialTheme.colorScheme.primary) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onAddAccount() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                Spacer(Modifier.weight(1f))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    color = Color.White.copy(0.08f))

                // Export Logs
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.BugReport, null, tint = Color(0xFF74B9FF)) },
                    label = { Text("Export Logs", color = Color(0xFF74B9FF)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        exportLogs(context)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                                // Disconnect
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    label = { Text("Disconnect Account", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        vm.stopPlayback(); vm.deleteCurrentAccount(); onNavigateToSetup()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E0C1C))
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                                    startY = size.height - 4.dp.toPx(),
                                    endY = size.height + 18.dp.toPx()
                                )
                            )
                        }
                ) {
                    if (state.isSelectionMode) {
                        SelectionToolbar(
                            selectedCount = state.selectedAssets.size,
                            onClearSelection = { vm.clearSelection() },
                            onSelectAll = { vm.selectAll() },
                            onDelete = { vm.deleteSelectedAssets() },
                            onDownload = { vm.downloadSelectedAssets() },
                            onShare = { /* We'll implement share selected later */ }
                        )
                    } else {
                        TopAppBar(
                            navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                        title = {
                            Column {
                                Text("CloudVault", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold)
                                if (state.allAssets.isNotEmpty()) {
                                    Text("${state.filteredAssets.size} of ${state.allAssets.size} files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        actions = {
                            if (state.filterState.activeFilterCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        "${state.filterState.activeFilterCount} filters",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(SurfaceDark, Color(0xFF0D0D1A)))
            )
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                val pullState = rememberPullToRefreshState()

                when {
                    state.isLoading -> LoadingView(state.loadingMessage, state.totalLoaded)
                    state.error != null && state.allAssets.isEmpty() -> ErrorView(
                        message = state.error!!, onRetry = { vm.reload() })
                    state.allAssets.isEmpty() -> EmptyView(onNavigateToSetup)
                    state.filteredAssets.isEmpty() -> FilteredEmptyView(
                        onClearFilters = { vm.clearFilters() })
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { vm.reload() },
                            state = pullState,
                            modifier = Modifier.fillMaxSize()
                                .padding(top = paddingValues.calculateTopPadding())
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(
                                    start = 12.dp, end = 12.dp,
                                    top = 8.dp,
                                    bottom = if (isAudioPlaying) 192.dp else 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = state.filteredAssets,
                                    key = { it.assetId.ifEmpty { it.publicId } }
                                ) { asset ->
                                    FileCard(
                                        asset = asset,
                                        isPlaying = state.currentlyPlayingId == asset.assetId && state.isPlaying,
                                        isSelected = state.selectedAssets.contains(asset.assetId),
                                        isSelectionMode = state.isSelectionMode,
                                        onPlayClick = { vm.togglePlay(asset) },
                                        onOpen = {
                                            if (asset.isAudio) vm.togglePlay(asset)
                                            else vm.openFile(asset)
                                        },
                                        onCopyLink = { vm.copyLink(asset) },
                                        onShare = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, asset.secureUrl)
                                                putExtra(Intent.EXTRA_SUBJECT, asset.displayTitle)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share file"))
                                        },
                                        onInfo = { vm.showInfo(asset) },
                                        onSelectToggle = {
                                            if (!state.isSelectionMode) vm.toggleSelectionMode()
                                            vm.toggleSelection(asset.assetId)
                                        },
                                        onClick = {
                                            if (state.isSelectionMode) {
                                                vm.toggleSelection(asset.assetId)
                                            } else if (!asset.isAudio) {
                                                vm.openFile(asset)
                                            }
                                        },
                                        onLongPress = {
                                            if (!state.isSelectionMode) vm.toggleSelectionMode()
                                            vm.toggleSelection(asset.assetId)
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }


            // ── File Viewer overlay ─────────────────────────────────────────
            if (state.viewingAsset != null) {
                FileViewerScreen(
                    asset = state.viewingAsset!!,
                    onDismiss = { vm.dismissViewer() }
                )
            }
                // Mini player
                val playingAsset = state.currentlyPlayingId?.let { id ->
                    state.allAssets.firstOrNull { it.assetId == id }
                }
                AnimatedVisibility(
                    visible = playingAsset != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()
                ) {
                    playingAsset?.let { asset ->
                        AudioPlayerBar(
                            asset = asset,
                            isPlaying = state.isPlaying,
                            onPlayPause = { vm.togglePlay(asset) },
                            onStop = { vm.stopPlayback() },
                            getPosition = { vm.getPlayerPosition() },
                            getDuration = { vm.getPlayerDuration() }
                        )
                    }
                }

                // FAB — moves above audio bar when playing
                val fabBottom by remember(isAudioPlaying) {
                    derivedStateOf { if (isAudioPlaying) 104.dp else 16.dp }
                }
                val filterCount = state.filterState.activeFilterCount
                ExtendedFloatingActionButton(
                    onClick = { vm.openFilterSheet() },
                    icon = {
                        BadgedBox(badge = {
                            if (filterCount > 0) Badge { Text("$filterCount") }
                        }) { Icon(Icons.Filled.FilterAlt, "Filter") }
                    },
                    text = { Text(if (filterCount > 0) "Filters ($filterCount)" else "Filter") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottom)
                        .navigationBarsPadding()
                )
            }
        }
    }

    // Filter bottom sheet
    if (state.isFilterSheetOpen) {
        FilterBottomSheet(
            current = state.filterState,
            onApply = { vm.applyFilter(it) },
            onDismiss = { vm.closeFilterSheet() }
        )
    }

    // Info dialog
    state.infoAsset?.let { asset ->
        AssetInfoDialog(asset = asset, onDismiss = { vm.dismissInfo() })
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(0.05f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
        }
    }
}

@Composable
private fun LoadingView(message: String, count: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp), strokeWidth = 4.dp)
            Spacer(Modifier.height(20.dp))
            Text(message.ifEmpty { "Loading assets…" }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            if (count > 0) {
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text("$count loaded", modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Failed to load", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Try Again")
            }
        }
    }
}

@Composable
private fun EmptyView(onSetup: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("No files found", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your account appears empty or credentials may be wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onSetup, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Settings, null); Spacer(Modifier.width(8.dp)); Text("Update Credentials")
            }
        }
    }
}

@Composable
private fun FilteredEmptyView(onClearFilters: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Outlined.FilterAltOff, null,
                tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("No matches", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("No files match your current filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onClearFilters, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.FilterAltOff, null); Spacer(Modifier.width(8.dp)); Text("Clear Filters")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetInfoDialog(asset: CloudinaryAsset, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF12101E),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ── Gradient Header ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.linearGradient(
                            colors = when {
                                asset.isAudio -> listOf(AudioGradientStart, AudioGradientEnd)
                                asset.isVideo -> listOf(VideoGradientStart, VideoGradientEnd)
                                asset.isImage -> listOf(ImageGradientStart, ImageGradientEnd)
                                else -> listOf(Color(0xFF1A1050), Color(0xFF0D0D1A))
                            },
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.padding(14.dp)) {
                            Icon(
                                when {
                                    asset.isAudio -> Icons.Filled.MusicNote
                                    asset.isVideo -> Icons.Filled.Videocam
                                    asset.isImage -> Icons.Filled.Image
                                    asset.isPdf -> Icons.Filled.PictureAsPdf
                                    else -> Icons.Filled.InsertDriveFile
                                },
                                null, tint = Color.White, modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            asset.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White.copy(0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    asset.format.uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                asset.fileTypeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Quick Stats Row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoStatCard(
                    icon = Icons.Outlined.DataUsage,
                    label = "Size",
                    value = asset.formattedSize,
                    modifier = Modifier.weight(1f)
                )
                if (asset.duration != null && asset.duration > 0) {
                    InfoStatCard(
                        icon = Icons.Outlined.Timer,
                        label = "Duration",
                        value = formatDurationSec(asset.duration),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (asset.width != null && asset.height != null) {
                    InfoStatCard(
                        icon = Icons.Outlined.AspectRatio,
                        label = "Dimensions",
                        value = "${asset.width}×${asset.height}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Details Card ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.06f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoDetailRow("File Name", asset.fileName, Icons.Outlined.Label)
                    InfoDetailRow("Uploaded", formatFullDate(asset.createdAt), Icons.Outlined.CalendarToday)
                    InfoDetailRow("Public ID", asset.publicId, Icons.Outlined.Key)
                    if (asset.folder != null) InfoDetailRow("Folder", asset.folder, Icons.Outlined.Folder)
                    if (asset.type.isNotBlank()) InfoDetailRow("Type", asset.type, Icons.Outlined.Category)
                }
            }

            // ── Tags ─────────────────────────────────────────────────────
            if (asset.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.06f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Tags",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            asset.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Action Buttons ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("URL", asset.secureUrl))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy URL", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(asset.secureUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(0.06f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.5f))
        }
    }
}

@Composable
private fun InfoDetailRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(0.7f), modifier = Modifier.size(15.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(0.8f), fontSize = 10.sp)
            Text(value, style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatFullDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm"))
} catch (_: Exception) { raw }



// ─── Export logs helper ───────────────────────────────────────────────────────
/**
 * Gathers all CloudVault log files and opens Android's share sheet.
 * No PC or root needed — share to WhatsApp, email, Telegram, Drive, etc.
 */
private fun exportLogs(context: android.content.Context) {
    val logFiles = com.cloudinaryfiles.app.AppLogger.allLogFiles()

    if (logFiles.isEmpty()) {
        android.widget.Toast.makeText(context, "No log files yet — reproduce your issue first", android.widget.Toast.LENGTH_LONG).show()
        return
    }

    // Prefer sharing the file so the recipient gets the full content.
    // Fall back to plain text if FileProvider is not configured.
    val uris = logFiles.mapNotNull { file ->
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) { null }
    }

    val intent = if (uris.isNotEmpty()) {
        // Share up to the 3 most recent log files as attachments
        val shareUris = ArrayList(uris.take(3))
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
            putExtra(Intent.EXTRA_SUBJECT, "CloudVault Logs")
            putExtra(Intent.EXTRA_TEXT, "CloudVault log files — ${logFiles.size} file(s)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        // FileProvider not set up — share as plain text (capped at 50 KB)
        val text = logFiles.firstOrNull()?.let { file ->
            try { file.readText(Charsets.UTF_8).takeLast(50_000) } catch (_: Exception) { null }
        } ?: "Could not read log file"
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "CloudVault Logs")
        }
    }

    context.startActivity(Intent.createChooser(intent, "Export logs via…"))
}
