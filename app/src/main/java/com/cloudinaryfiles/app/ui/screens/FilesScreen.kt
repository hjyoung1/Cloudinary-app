package com.cloudinaryfiles.app.ui.screens

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import com.cloudinaryfiles.app.ui.components.AudioPlayerBar
import com.cloudinaryfiles.app.ui.components.FileCard
import com.cloudinaryfiles.app.ui.components.FilterBottomSheet
import com.cloudinaryfiles.app.ui.theme.*
import com.cloudinaryfiles.app.ui.viewmodel.FilesViewModel
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
    var showAccountMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    val activeAccount = remember(state.accounts, state.activeAccountId) {
        state.accounts.firstOrNull { it.id == state.activeAccountId }
            ?: state.accounts.firstOrNull()
    }

    val isAudioPlaying = state.currentlyPlayingId != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E0C1C))
                    .drawBehind {
                        // Bottom shadow/gradient for scroll separation
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                startY = size.height - 4.dp.toPx(),
                                endY = size.height + 20.dp.toPx()
                            )
                        )
                    }
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "CloudVault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (state.allAssets.isNotEmpty()) {
                                Text(
                                    "${state.filteredAssets.size} of ${state.allAssets.size} files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showAccountMenu = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                // Active account header
                                if (activeAccount != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    "Active Account",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    activeAccount.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        },
                                        onClick = {},
                                        leadingIcon = {
                                            Icon(Icons.Outlined.AccountCircle, null,
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                    HorizontalDivider()
                                }

                                // Other accounts to switch
                                state.accounts.filter { it.id != state.activeAccountId }.forEach { acct ->
                                    DropdownMenuItem(
                                        text = { Text("Switch to: ${acct.name}") },
                                        onClick = {
                                            showAccountMenu = false
                                            vm.switchAccount(acct.id)
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.SwitchAccount, null) }
                                    )
                                }

                                // Add Account
                                DropdownMenuItem(
                                    text = { Text("Add Account") },
                                    onClick = {
                                        showAccountMenu = false
                                        onAddAccount()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.AddCircleOutline, null) }
                                )

                                HorizontalDivider()

                                // Disconnect / logout
                                DropdownMenuItem(
                                    text = { Text("Disconnect", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showAccountMenu = false
                                        vm.stopPlayback()
                                        vm.deleteCurrentAccount()
                                        onNavigateToSetup()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Logout, null,
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
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
                    message = state.error!!,
                    onRetry = { vm.reload() }
                )

                state.allAssets.isEmpty() -> EmptyView(onNavigateToSetup)

                state.filteredAssets.isEmpty() -> FilteredEmptyView(
                    onClearFilters = { vm.clearFilters() }
                )

                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { vm.reload() },
                        state = pullState,
                        modifier = Modifier
                            .fillMaxSize()
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
                                    onInfo = { vm.showInfo(asset) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            // ── Mini player bar ──────────────────────────────────────────
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

            // ── FAB positioned above audio player ──────────────────────
            val fabBottomPad by remember(isAudioPlaying) {
                derivedStateOf { if (isAudioPlaying) 104.dp else 16.dp }
            }
            val filterCount = state.filterState.activeFilterCount
            ExtendedFloatingActionButton(
                onClick = { vm.openFilterSheet() },
                icon = {
                    BadgedBox(badge = {
                        if (filterCount > 0) Badge { Text("$filterCount") }
                    }) {
                        Icon(Icons.Filled.FilterAlt, "Filter")
                    }
                },
                text = { Text(if (filterCount > 0) "Filters ($filterCount)" else "Filter") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomPad)
                    .navigationBarsPadding()
            )
        }
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────
    if (state.isFilterSheetOpen) {
        FilterBottomSheet(
            current = state.filterState,
            onApply = { vm.applyFilter(it) },
            onDismiss = { vm.closeFilterSheet() }
        )
    }

    // ── Info dialog ───────────────────────────────────────────────────────
    state.infoAsset?.let { asset ->
        AssetInfoDialog(asset = asset, onDismiss = { vm.dismissInfo() })
    }
}

@Composable
private fun LoadingView(message: String, count: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
                strokeWidth = 4.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                message.ifEmpty { "Loading assets…" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (count > 0) {
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(
                        "$count loaded",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
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
            Text("Your Cloudinary account appears empty, or credentials may be wrong.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onSetup, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Update Credentials")
            }
        }
    }
}

@Composable
private fun FilteredEmptyView(onClearFilters: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Outlined.FilterAltOff, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("No matches", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("No files match your current filters. Try adjusting or clearing them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onClearFilters, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.FilterAltOff, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear Filters")
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("File Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        asset.fileTypeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                InfoRow("Name", asset.displayTitle, Icons.Outlined.Label)
                InfoRow("Format", asset.format.uppercase(), Icons.Outlined.FileOpen)
                InfoRow("Size", asset.formattedSize, Icons.Outlined.DataUsage)
                InfoRow("Type", asset.fileTypeLabel, Icons.Outlined.Category)
                InfoRow("Uploaded", formatFullDate(asset.createdAt), Icons.Outlined.CalendarToday)
                if (asset.duration != null) {
                    InfoRow("Duration", formatDuration(asset.duration), Icons.Outlined.Timer)
                }
                InfoRow("Public ID", asset.publicId, Icons.Outlined.Key)
                if (asset.folder != null) InfoRow("Folder", asset.folder, Icons.Outlined.Folder)
                if (asset.width != null && asset.height != null)
                    InfoRow("Dimensions", "${asset.width} × ${asset.height}", Icons.Outlined.AspectRatio)
                if (asset.tags.isNotEmpty()) InfoRow("Tags", asset.tags.joinToString(", "), Icons.Outlined.Tag)
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        modifier = Modifier.padding(start = 26.dp)
    )
}


private fun formatFullDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm"))
} catch (_: Exception) { raw }

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
