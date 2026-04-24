package com.cloudinaryfiles.app.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cloudinaryfiles.app.AppLogger
import com.cloudinaryfiles.app.data.cache.AssetCache
import com.cloudinaryfiles.app.data.model.*
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import com.cloudinaryfiles.app.data.preferences.UserPreferences
import com.cloudinaryfiles.app.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class FilesUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val isRefreshing: Boolean = false,
    val allAssets: List<CloudinaryAsset> = emptyList(),
    val filteredAssets: List<CloudinaryAsset> = emptyList(),
    val error: String? = null,
    val filterState: FilterState = FilterState(),
    val currentlyPlayingId: String? = null,
    val isPlaying: Boolean = false,
    val isFilterSheetOpen: Boolean = false,
    val infoAsset: CloudinaryAsset? = null,
    val viewingAsset: CloudinaryAsset? = null,
    val viewingUrl: String? = null,      // Resolved stream URL for the viewer
    val snackbarMessage: String? = null,
    val totalLoaded: Int = 0,
    val accounts: List<NamedAccount> = emptyList(),
    val activeAccountId: String? = null,
    val activeAccount: NamedAccount? = null,
    val selectedAssets: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isGridView: Boolean = true,
    // Inline video playback (video plays in the card before going full-screen)
    val inlineVideoId: String? = null,
    val inlineVideoUrl: String? = null
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val LOG = "FilesViewModel"

    private val prefs        = UserPreferences(application)
    private val cloudRepo    = CloudinaryRepository()
    private val s3Repo       = S3Repository()
    private val gDriveRepo   = GoogleDriveRepository()
    private val dropboxRepo  = DropboxRepository()
    private val oneDriveRepo = OneDriveRepository()
    private val boxRepo      = BoxRepository()
    private val webDavRepo   = WebDavDirectRepository()
    private val cache        = AssetCache(application)

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    init {
        AppLogger.i(LOG, "FilesViewModel created — wiring account observers…")

        viewModelScope.launch {
            prefs.accounts.collect { accounts ->
                AppLogger.d(LOG, "accounts updated: ${accounts.size} total — ${accounts.map { "${it.id}(${it.providerKey})" }}")
                _state.update { s -> s.copy(accounts = accounts) }
            }
        }

        viewModelScope.launch {
            prefs.activeAccountId.collect { id ->
                AppLogger.d(LOG, "activeAccountId updated: $id")
                _state.update { s -> s.copy(activeAccountId = id) }
            }
        }

        viewModelScope.launch {
            var lastId: String? = null
            prefs.activeAccount.collect { account ->
                AppLogger.i(LOG, "activeAccount updated: ${
                    if (account == null) "null"
                    else "id=${account.id}, provider=${account.providerKey}, name='${account.name}'"
                }")
                _state.update { it.copy(activeAccount = account) }
                val newId = account?.id

                if (account != null && newId != lastId) {
                    AppLogger.i(LOG, "Account changed: $lastId → $newId — loading assets…")
                    lastId = newId

                    AppLogger.d(LOG, "Checking cache for account $newId…")
                    val cached = cache.load(account.id)
                    if (!cached.isNullOrEmpty()) {
                        AppLogger.i(LOG, "Cache HIT — ${cached.size} assets restored from disk cache")
                        _state.update { it.copy(allAssets = cached, isLoading = false) }
                        reFilter()
                    } else {
                        AppLogger.i(LOG, "Cache MISS — fetching from provider…")
                        loadAssets(account, isRefresh = false)
                    }
                } else if (account == null) {
                    AppLogger.i(LOG, "Active account set to null — clearing assets")
                    lastId = null
                    _state.update { it.copy(allAssets = emptyList(), filteredAssets = emptyList()) }
                }
            }
        }
    }

    // ─── Account management ──────────────────────────────────────────────────

    fun switchAccount(id: String) {
        AppLogger.i(LOG, "switchAccount($id)")
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(allAssets = emptyList(), filteredAssets = emptyList()) }
            prefs.setActiveAccount(id)
        }
    }

    fun deleteCurrentAccount() {
        val id = _state.value.activeAccountId ?: run {
            AppLogger.w(LOG, "deleteCurrentAccount(): no active account id")
            return
        }
        AppLogger.i(LOG, "deleteCurrentAccount(): id=$id")
        viewModelScope.launch {
            stopPlayback()
            cache.clear(id)
            prefs.deleteAccount(id)
        }
    }

    // ─── Playback ───────────────────────────────────────────────────────────

    private fun getOrCreatePlayer(): ExoPlayer {
        if (player == null) {
            AppLogger.d(LOG, "Creating new ExoPlayer instance")
            player = ExoPlayer.Builder(getApplication()).build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        AppLogger.d(LOG, "ExoPlayer: isPlaying=$playing")
                        _state.update { it.copy(isPlaying = playing) }
                    }
                    override fun onPlaybackStateChanged(s: Int) {
                        val stateName = when (s) {
                            Player.STATE_IDLE     -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY    -> "READY"
                            Player.STATE_ENDED    -> "ENDED"
                            else                  -> "UNKNOWN($s)"
                        }
                        AppLogger.d(LOG, "ExoPlayer: playbackState=$stateName")
                        if (s == Player.STATE_ENDED) _state.update { it.copy(isPlaying = false) }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        AppLogger.e(LOG, "ExoPlayer ERROR: ${error.message} (code=${error.errorCode})", error)
                    }
                })
            }
        }
        return player!!
    }

    fun togglePlay(asset: CloudinaryAsset) {
        AppLogger.i(LOG, "togglePlay(): assetId=${asset.assetId}, currentlyPlaying=${_state.value.currentlyPlayingId}")
        val p = getOrCreatePlayer()
        if (_state.value.currentlyPlayingId == asset.assetId) {
            val nowPlaying = p.isPlaying
            AppLogger.d(LOG, "  Same asset — toggling: isPlaying=$nowPlaying → ${!nowPlaying}")
            if (nowPlaying) p.pause() else p.play()
            return
        }
        viewModelScope.launch {
            val account = _state.value.activeAccount ?: run {
                AppLogger.e(LOG, "togglePlay(): no active account!")
                return@launch
            }
            AppLogger.d(LOG, "  Resolving stream URL for ${asset.assetId}…")
            val streamUrl = withContext(Dispatchers.IO) { resolveStreamUrl(asset, account) }
            AppLogger.i(LOG, "  streamUrl resolved: ${AppLogger.redactUrl(streamUrl)}")
            p.setMediaItem(MediaItem.fromUri(streamUrl))
            p.prepare()
            p.play()
            _state.update { it.copy(currentlyPlayingId = asset.assetId) }
        }
    }

    private fun resolveStreamUrl(asset: CloudinaryAsset, account: NamedAccount): String {
        val provider = Providers.find(account.providerKey)
        AppLogger.d(LOG, "resolveStreamUrl(): provider=${provider.key}, authType=${provider.authType}")

        return when (provider.authType) {
            ProviderAuthType.S3_COMPATIBLE -> {
                val url = s3Repo.presignedGetUrl(account, asset.publicId)
                AppLogger.d(LOG, "  S3 presigned URL generated")
                url
            }
            ProviderAuthType.OAUTH_GOOGLE -> {
                val token = gDriveRepo.freshToken(account) ?: asset.secureUrl
                val url = "https://www.googleapis.com/drive/v3/files/${asset.publicId}?alt=media&access_token=$token"
                AppLogger.d(LOG, "  Google Drive stream URL built (has token=${token.isNotBlank()})")
                url
            }
            ProviderAuthType.OAUTH_DROPBOX -> {
                AppLogger.d(LOG, "  Dropbox: returning stored temp link (may be expired after 4h): ${asset.secureUrl.take(60)}")
                asset.secureUrl
            }
            ProviderAuthType.OAUTH_ONEDRIVE, ProviderAuthType.OAUTH_BOX -> {
                AppLogger.d(LOG, "  ${provider.authType}: returning pre-auth download URL")
                asset.secureUrl
            }
            ProviderAuthType.BASIC_WEBDAV -> {
                AppLogger.d(LOG, "  WebDAV: returning URL with embedded credentials")
                asset.secureUrl
            }
            else -> {
                AppLogger.d(LOG, "  ${provider.authType}: returning secureUrl")
                asset.secureUrl
            }
        }
    }

    fun stopPlayback() {
        AppLogger.d(LOG, "stopPlayback()")
        player?.stop()
        _state.update { it.copy(currentlyPlayingId = null, isPlaying = false) }
    }

    fun seekTo(ms: Long)          { player?.seekTo(ms) }
    fun getPlayerDuration(): Long = player?.duration ?: 0L
    fun getPlayerPosition(): Long = player?.currentPosition ?: 0L

    // ─── Clipboard ──────────────────────────────────────────────────────────

    fun copyLink(asset: CloudinaryAsset) {
        AppLogger.d(LOG, "copyLink(): ${asset.assetId}")
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("URL", asset.secureUrl))
        _state.update { it.copy(snackbarMessage = "🔗 Link copied!") }
    }

    // ─── Info / Filter ──────────────────────────────────────────────────────

    fun showInfo(asset: CloudinaryAsset)  { _state.update { it.copy(infoAsset = asset) } }
    fun dismissInfo()                     { _state.update { it.copy(infoAsset = null) } }
    fun openFile(asset: CloudinaryAsset) {
        val account = _state.value.activeAccount
        if (account == null) {
            _state.update { it.copy(viewingAsset = asset, viewingUrl = asset.secureUrl) }
            return
        }
        // Resolve a fresh authenticated URL (critical for GDrive, S3)
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                try { resolveStreamUrl(asset, account) } catch (_: Exception) { asset.secureUrl }
            }
            _state.update { it.copy(viewingAsset = asset, viewingUrl = resolved) }
        }
    }
    fun dismissViewer()                    { _state.update { it.copy(viewingAsset = null, viewingUrl = null) } }

    /** Start inline video in the card — resolves auth URL first */
    fun setInlineVideo(asset: CloudinaryAsset) {
        val account = _state.value.activeAccount ?: run {
            _state.update { it.copy(inlineVideoId = asset.assetId, inlineVideoUrl = asset.secureUrl) }
            return
        }
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                try { resolveStreamUrl(asset, account) } catch (_: Exception) { asset.secureUrl }
            }
            _state.update { it.copy(inlineVideoId = asset.assetId, inlineVideoUrl = resolved) }
        }
    }

    fun clearInlineVideo() { _state.update { it.copy(inlineVideoId = null, inlineVideoUrl = null) } }
    fun openFilterSheet()                 { _state.update { it.copy(isFilterSheetOpen = true) } }
    fun closeFilterSheet()                { _state.update { it.copy(isFilterSheetOpen = false) } }
    fun applyFilter(f: FilterState) {
        AppLogger.d(LOG, "applyFilter(): query='${f.searchQuery}', type=${f.fileTypeFilter}, sort=${f.sortBy}")
        _state.update { it.copy(filterState = f, isFilterSheetOpen = false) }
        reFilter()
    }
    fun clearFilters() {
        AppLogger.d(LOG, "clearFilters()")
        _state.update { it.copy(filterState = FilterState()) }
        reFilter()
    }
    fun clearSnackbar() { _state.update { it.copy(snackbarMessage = null) } }

    // ─── Loading ─────────────────────────────────────────────────────────────

    fun reload() {
        val a = _state.value.activeAccount ?: run {
            AppLogger.w(LOG, "reload(): no active account")
            return
        }
        AppLogger.i(LOG, "reload(): forcing refresh for account ${a.id}")
        loadAssets(a, true)
    }

    private fun loadAssets(account: NamedAccount, isRefresh: Boolean) {
        AppLogger.i(LOG, "loadAssets(): account=${account.id}, provider=${account.providerKey}, isRefresh=$isRefresh")

        // Log everything about the account (never log actual secrets)
        AppLogger.i(LOG, "  Account details:")
        AppLogger.i(LOG, "    id                : ${account.id}")
        AppLogger.i(LOG, "    name              : '${account.name}'")
        AppLogger.i(LOG, "    providerKey       : ${account.providerKey}")
        AppLogger.i(LOG, "    cloudName         : '${account.cloudName}'")
        AppLogger.i(LOG, "    apiKey            : ${AppLogger.mask(account.apiKey)}")
        AppLogger.i(LOG, "    apiSecret         : ${AppLogger.mask(account.apiSecret)}")
        AppLogger.i(LOG, "    oauthClientId     : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.i(LOG, "    oauthClientSecret : ${AppLogger.mask(account.oauthClientSecret)}")
        AppLogger.i(LOG, "    oauthAccessToken  : ${AppLogger.mask(account.oauthAccessToken)}")
        AppLogger.i(LOG, "    oauthRefreshToken : ${AppLogger.mask(account.oauthRefreshToken)}")
        AppLogger.i(LOG, "    oauthTokenExpiry  : ${account.oauthTokenExpiry}")
        AppLogger.i(LOG, "    s3Endpoint        : '${account.s3Endpoint}'")
        AppLogger.i(LOG, "    s3Bucket          : '${account.s3Bucket}'")
        AppLogger.i(LOG, "    s3AccessKey       : ${AppLogger.mask(account.s3AccessKey)}")
        AppLogger.i(LOG, "    webDavUrl         : '${account.webDavUrl}'")
        AppLogger.i(LOG, "    webDavUser        : '${account.webDavUser}'")

        viewModelScope.launch {
            if (isRefresh) _state.update { it.copy(isRefreshing = true, error = null) }
            else           _state.update { it.copy(isLoading = true, loadingMessage = "", totalLoaded = 0, error = null) }

            val provider = Providers.find(account.providerKey)
            AppLogger.i(LOG, "  Provider found: key=${provider.key}, authType=${provider.authType}")

            val resultFlow = when (provider.authType) {
                ProviderAuthType.CLOUDINARY     -> {
                    AppLogger.i(LOG, "  → Routing to CloudinaryRepository")
                    val creds = account.toCredentials()
                    if (creds == null) {
                        AppLogger.e(LOG, "  ✗ toCredentials() returned null! cloudName='${account.cloudName}' apiKey blank=${account.apiKey.isBlank()}")
                        _state.update { it.copy(isLoading = false, error = "Cloudinary: missing credentials") }
                        return@launch
                    }
                    cloudRepo.fetchAllAssets(creds)
                }
                ProviderAuthType.S3_COMPATIBLE  -> {
                    AppLogger.i(LOG, "  → Routing to S3Repository (endpoint='${account.s3Endpoint}')")
                    s3Repo.fetchAllAssets(account)
                }
                ProviderAuthType.OAUTH_GOOGLE   -> {
                    val hasToken = account.oauthAccessToken.isNotBlank()
                    val hasRefresh = account.oauthRefreshToken.isNotBlank()
                    AppLogger.i(LOG, "  → Routing to GoogleDriveRepository (hasToken=$hasToken, hasRefresh=$hasRefresh)")
                    gDriveRepo.fetchAllAssets(account)
                }
                ProviderAuthType.OAUTH_DROPBOX  -> {
                    val hasToken = account.oauthAccessToken.isNotBlank()
                    AppLogger.i(LOG, "  → Routing to DropboxRepository (hasToken=$hasToken)")
                    dropboxRepo.fetchAllAssets(account)
                }
                ProviderAuthType.OAUTH_ONEDRIVE -> {
                    AppLogger.i(LOG, "  → Routing to OneDriveRepository")
                    oneDriveRepo.fetchAllAssets(account)
                }
                ProviderAuthType.OAUTH_BOX      -> {
                    AppLogger.i(LOG, "  → Routing to BoxRepository")
                    boxRepo.fetchAllAssets(account)
                }
                ProviderAuthType.BASIC_WEBDAV   -> {
                    AppLogger.i(LOG, "  → Routing to WebDavDirectRepository (url='${account.webDavUrl}')")
                    webDavRepo.fetchAllAssets(account)
                }
            }

            resultFlow.collect { result ->
                when (result) {
                    is RepositoryResult.Progress -> {
                        AppLogger.v(LOG, "  Progress: ${result.loaded} — '${result.message}'")
                        if (!isRefresh) _state.update {
                            it.copy(loadingMessage = result.message, totalLoaded = result.loaded)
                        }
                    }
                    is RepositoryResult.Success -> {
                        AppLogger.i(LOG, "  ✓ Success: ${result.assets.size} assets received")
                        AppLogger.d(LOG, "  Asset type breakdown: " +
                                "images=${result.assets.count { it.isImage }}, " +
                                "video=${result.assets.count { it.isVideo }}, " +
                                "audio=${result.assets.count { it.isAudio }}, " +
                                "pdf=${result.assets.count { it.isPdf }}, " +
                                "other=${result.assets.count { it.isOther }}")
                        cache.save(account.id, result.assets)
                        AppLogger.d(LOG, "  Saved ${result.assets.size} assets to cache")
                        _state.update {
                            it.copy(isLoading = false, isRefreshing = false, allAssets = result.assets, error = null)
                        }
                        reFilter()
                    }
                    is RepositoryResult.Error -> {
                        AppLogger.e(LOG, "  ✗ Error from repository: '${result.message}'")
                        _state.update {
                            it.copy(isLoading = false, isRefreshing = false, error = result.message)
                        }
                    }
                }
            }
        }
    }

    // ─── Filtering ──────────────────────────────────────────────────────────

    private fun parseDate(raw: String): LocalDateTime? = try {
        OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        try { LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME) } catch (_: Exception) { null }
    }

    private fun reFilter() {
        val st = _state.value; val f = st.filterState
        var list = st.allAssets
        if (f.searchQuery.isNotBlank()) {
            val q = f.searchQuery.trim()
            list = list.filter { a -> when (f.nameFilterType) {
                NameFilterType.CONTAINS    -> a.publicId.contains(q,true)||a.displayTitle.contains(q,true)
                NameFilterType.EXACT       -> a.publicId.equals(q,true)||a.fileName.equals(q,true)
                NameFilterType.STARTS_WITH -> a.publicId.startsWith(q,true)||a.displayTitle.startsWith(q,true)
                NameFilterType.ENDS_WITH   -> a.publicId.endsWith(q,true)||a.fileName.endsWith(q,true)
            }}
        }
        if (f.dateRangeStart != null || f.dateRangeEnd != null) {
            list = list.filter { a ->
                val d = parseDate(a.createdAt) ?: return@filter true
                (f.dateRangeStart?.let { d >= it } ?: true) && (f.dateRangeEnd?.let { d <= it } ?: true)
            }
        }
        if (f.isPivotFilterEnabled && f.pivotFileName.isNotBlank()) {
            val pivot = st.allAssets.firstOrNull { it.publicId.contains(f.pivotFileName,true) }
            pivot?.let { piv -> parseDate(piv.createdAt)?.let { pd ->
                list = list.filter { a ->
                    if (a.assetId == piv.assetId) return@filter false
                    val d = parseDate(a.createdAt) ?: return@filter false
                    when (f.pivotType) { BeforeAfterType.BEFORE -> d < pd; BeforeAfterType.AFTER -> d > pd }
                }
            }}
        }
        list = when (f.fileTypeFilter) {
            FileTypeFilter.ALL   -> list; FileTypeFilter.AUDIO -> list.filter { it.isAudio }
            FileTypeFilter.VIDEO -> list.filter { it.isVideo }; FileTypeFilter.IMAGE -> list.filter { it.isImage }
            FileTypeFilter.PDF   -> list.filter { it.isPdf }; FileTypeFilter.OTHER -> list.filter { it.isOther }
        }
        if (f.isSizeFilterEnabled) list = list.filter { it.sizeInMB >= f.minSizeMB && it.sizeInMB <= f.maxSizeMB }
        if (f.isDurationFilterEnabled) list = list.filter { a ->
            val dur = a.duration?.toFloat() ?: return@filter false
            dur >= f.minDurationSec && dur <= f.maxDurationSec
        }
        list = when (f.sortBy) {
            SortBy.NEWEST_FIRST  -> list.sortedByDescending { it.createdAt }
            SortBy.OLDEST_FIRST  -> list.sortedBy { it.createdAt }
            SortBy.NAME_AZ       -> list.sortedBy { it.displayTitle.lowercase() }
            SortBy.NAME_ZA       -> list.sortedByDescending { it.displayTitle.lowercase() }
            SortBy.SIZE_LARGEST  -> list.sortedByDescending { it.bytes }
            SortBy.SIZE_SMALLEST -> list.sortedBy { it.bytes }
        }
        AppLogger.d(LOG, "reFilter(): ${st.allAssets.size} total → ${list.size} after filter")
        _state.update { it.copy(filteredAssets = list) }
    }

    // ─── Selection & View ───────────────────────────────────────────────────

    fun toggleSelectionMode() {
        _state.update {
            val newMode = !it.isSelectionMode
            AppLogger.d(LOG, "toggleSelectionMode(): $newMode")
            it.copy(isSelectionMode = newMode, selectedAssets = if (!newMode) emptySet() else it.selectedAssets)
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update {
            val current = it.selectedAssets.toMutableSet()
            if (current.contains(assetId)) current.remove(assetId) else current.add(assetId)
            it.copy(selectedAssets = current)
        }
    }

    fun selectAll() {
        AppLogger.d(LOG, "selectAll()")
        _state.update { it.copy(selectedAssets = it.filteredAssets.map { a -> a.assetId }.toSet()) }
    }

    fun clearSelection() {
        AppLogger.d(LOG, "clearSelection()")
        _state.update { it.copy(selectedAssets = emptySet(), isSelectionMode = false) }
    }

    fun toggleGridView() {
        _state.update { it.copy(isGridView = !it.isGridView) }
    }

    fun deleteSelectedAssets() {
        val st = _state.value
        val account = st.activeAccount ?: return
        val selectedIds = st.selectedAssets
        if (selectedIds.isEmpty()) return
        val assetsToDelete = st.allAssets.filter { it.assetId in selectedIds }
        AppLogger.i(LOG, "deleteSelectedAssets(): ${assetsToDelete.size} assets")

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadingMessage = "Deleting ${assetsToDelete.size} items…") }
            val provider = Providers.find(account.providerKey)
            var successCount = 0

            withContext(Dispatchers.IO) {
                when (provider.authType) {
                    ProviderAuthType.CLOUDINARY -> {
                        val creds = account.toCredentials()
                        if (creds != null && cloudRepo.deleteAssets(creds, assetsToDelete)) {
                            successCount = assetsToDelete.size
                        }
                    }
                    ProviderAuthType.OAUTH_GOOGLE -> {
                        for (a in assetsToDelete) {
                            if (gDriveRepo.deleteFile(account, a.publicId)) successCount++
                        }
                    }
                    ProviderAuthType.OAUTH_DROPBOX -> {
                        for (a in assetsToDelete) {
                            if (dropboxRepo.deleteFile(account, a.publicId)) successCount++
                        }
                    }
                    else -> AppLogger.w(LOG, "deleteSelectedAssets(): not implemented for ${provider.authType}")
                }
            }

            AppLogger.i(LOG, "deleteSelectedAssets(): $successCount/${assetsToDelete.size} deleted")
            if (successCount > 0) {
                _state.update { it.copy(snackbarMessage = "Deleted $successCount items") }
                loadAssets(account, isRefresh = true)
            } else {
                _state.update { it.copy(isLoading = false, error = "Failed to delete items") }
            }
            clearSelection()
        }
    }

    fun downloadSelectedAssets() {
        val st = _state.value
        val account = st.activeAccount ?: return
        val selectedIds = st.selectedAssets
        if (selectedIds.isEmpty()) return
        val assetsToDownload = st.allAssets.filter { it.assetId in selectedIds }
        AppLogger.i(LOG, "downloadSelectedAssets(): ${assetsToDownload.size} assets")

        viewModelScope.launch {
            _state.update { it.copy(snackbarMessage = "Starting download for ${assetsToDownload.size} items…") }
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            withContext(Dispatchers.IO) {
                for (asset in assetsToDownload) {
                    try {
                        val url = resolveStreamUrl(asset, account)
                        AppLogger.d(LOG, "  Enqueuing download: ${asset.fileName} → ${AppLogger.redactUrl(url).take(80)}")
                        val req = DownloadManager.Request(Uri.parse(url))
                            .setTitle(asset.fileName)
                            .setDescription("Downloading from CloudVault")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CloudVault/${asset.fileName}")
                        req.allowScanningByMediaScanner()
                        dm.enqueue(req)
                    } catch (e: Exception) {
                        AppLogger.e(LOG, "  Download enqueue failed for ${asset.assetId}", e)
                    }
                }
            }
            clearSelection()
        }
    }

    override fun onCleared() {
        AppLogger.i(LOG, "onCleared() — releasing ExoPlayer")
        super.onCleared()
        player?.release()
        player = null
    }
}
