package com.cloudinaryfiles.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.model.Providers
import com.cloudinaryfiles.app.ui.components.AudioPlayerBar
import com.cloudinaryfiles.app.ui.components.FileCard
import com.cloudinaryfiles.app.ui.components.FilterBottomSheet
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

                // Active account card
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

                // Other accounts
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
                                        onPlayClick = { vm.togglePlay(asset) },
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
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
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

@Composable
private fun AssetInfoDialog(asset: CloudinaryAsset, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Info, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("File Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(asset.fileTypeLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                InfoRow("Name", asset.displayTitle, Icons.Outlined.Label)
                InfoRow("Format", asset.format.uppercase(), Icons.Outlined.FileOpen)
                InfoRow("Size", asset.formattedSize, Icons.Outlined.DataUsage)
                if (asset.duration != null && asset.duration > 0)
                    InfoRow("Duration", formatDurationSec(asset.duration), Icons.Outlined.Timer)
                InfoRow("Uploaded", formatFullDate(asset.createdAt), Icons.Outlined.CalendarToday)
                InfoRow("Public ID", asset.publicId, Icons.Outlined.Key)
                if (asset.folder != null) InfoRow("Folder", asset.folder, Icons.Outlined.Folder)
                if (asset.width != null && asset.height != null)
                    InfoRow("Dimensions", "${asset.width} × ${asset.height}", Icons.Outlined.AspectRatio)
                if (asset.tags.isNotEmpty()) InfoRow("Tags", asset.tags.joinToString(", "), Icons.Outlined.Tag)
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f), modifier = Modifier.padding(start = 26.dp))
}

private fun formatFullDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm"))
} catch (_: Exception) { raw }
