package com.cloudinaryfiles.app.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val snackbarMessage: String? = null,
    val totalLoaded: Int = 0,
    val accounts: List<NamedAccount> = emptyList(),
    val activeAccountId: String? = null,
    val activeAccount: NamedAccount? = null
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs      = UserPreferences(application)
    private val cloudRepo  = CloudinaryRepository()
    private val s3Repo     = S3Repository()
    private val gDriveRepo = GoogleDriveRepository()
    private val dropboxRepo = DropboxRepository()
    private val oneDriveRepo = OneDriveRepository()
    private val boxRepo    = BoxRepository()
    private val webDavRepo = WebDavDirectRepository()
    private val cache      = AssetCache(application)

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    init {
        viewModelScope.launch { prefs.accounts.collect { _state.update { s -> s.copy(accounts = it) } } }
        viewModelScope.launch { prefs.activeAccountId.collect { _state.update { s -> s.copy(activeAccountId = it) } } }
        viewModelScope.launch {
            var lastId: String? = null
            prefs.activeAccount.collect { account ->
                _state.update { it.copy(activeAccount = account) }
                val newId = account?.id
                if (account != null && newId != lastId) {
                    lastId = newId
                    val cached = cache.load(account.id)
                    if (!cached.isNullOrEmpty()) {
                        _state.update { it.copy(allAssets = cached, isLoading = false) }
                        reFilter()
                    } else {
                        loadAssets(account, isRefresh = false)
                    }
                } else if (account == null) {
                    lastId = null
                    _state.update { it.copy(allAssets = emptyList(), filteredAssets = emptyList()) }
                }
            }
        }
    }

    // ─── Account management ──────────────────────────────────────────────────

    fun switchAccount(id: String) {
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(allAssets = emptyList(), filteredAssets = emptyList()) }
            prefs.setActiveAccount(id)
        }
    }

    fun deleteCurrentAccount() {
        val id = _state.value.activeAccountId ?: return
        viewModelScope.launch { stopPlayback(); cache.clear(id); prefs.deleteAccount(id) }
    }

    // ─── Playback ───────────────────────────────────────────────────────────

    private fun getOrCreatePlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(getApplication()).build().also { p ->
                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { _state.update { it.copy(isPlaying = playing) } }
                    override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED) _state.update { it.copy(isPlaying = false) } }
                })
            }
        }
        return player!!
    }

    fun togglePlay(asset: CloudinaryAsset) {
        val p = getOrCreatePlayer()
        if (_state.value.currentlyPlayingId == asset.assetId) {
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        viewModelScope.launch {
            val account = _state.value.activeAccount ?: return@launch
            val streamUrl = withContext(Dispatchers.IO) { resolveStreamUrl(asset, account) }
            p.setMediaItem(MediaItem.fromUri(streamUrl))
            p.prepare()
            p.play()
            _state.update { it.copy(currentlyPlayingId = asset.assetId) }
        }
    }

    private fun resolveStreamUrl(asset: CloudinaryAsset, account: NamedAccount): String {
        val provider = Providers.find(account.providerKey)
        return when (provider.authType) {
            ProviderAuthType.S3_COMPATIBLE -> {
                // Generate presigned URL (valid 1 hour) — no auth header needed
                val objectKey = asset.publicId
                s3Repo.presignedGetUrl(account, objectKey)
            }
            ProviderAuthType.OAUTH_GOOGLE -> {
                // Refresh token if needed and embed in URL
                val token = gDriveRepo.freshToken(account) ?: asset.secureUrl
                val fileId = asset.publicId
                "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&access_token=$token"
            }
            ProviderAuthType.OAUTH_DROPBOX -> {
                // Already stored a temporary link from listing; just return it
                // If it's expired (4h), the URL stored is the API URL, which needs a fresh temp link
                asset.secureUrl
            }
            ProviderAuthType.OAUTH_ONEDRIVE, ProviderAuthType.OAUTH_BOX -> {
                // @microsoft.graph.downloadUrl is pre-authenticated; same for Box
                asset.secureUrl
            }
            ProviderAuthType.BASIC_WEBDAV -> {
                // Credentials already embedded in URL during listing
                asset.secureUrl
            }
            else -> asset.secureUrl
        }
    }

    fun stopPlayback() {
        player?.stop()
        _state.update { it.copy(currentlyPlayingId = null, isPlaying = false) }
    }

    fun seekTo(ms: Long)          { player?.seekTo(ms) }
    fun getPlayerDuration(): Long = player?.duration ?: 0L
    fun getPlayerPosition(): Long = player?.currentPosition ?: 0L

    // ─── Clipboard ──────────────────────────────────────────────────────────

    fun copyLink(asset: CloudinaryAsset) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("URL", asset.secureUrl))
        _state.update { it.copy(snackbarMessage = "🔗 Link copied!") }
    }

    // ─── Info / Filter ──────────────────────────────────────────────────────

    fun showInfo(asset: CloudinaryAsset)  { _state.update { it.copy(infoAsset = asset) } }
    fun dismissInfo()                     { _state.update { it.copy(infoAsset = null) } }
    fun openFilterSheet()                 { _state.update { it.copy(isFilterSheetOpen = true) } }
    fun closeFilterSheet()                { _state.update { it.copy(isFilterSheetOpen = false) } }
    fun applyFilter(f: FilterState)       { _state.update { it.copy(filterState = f, isFilterSheetOpen = false) }; reFilter() }
    fun clearFilters()                    { _state.update { it.copy(filterState = FilterState()) }; reFilter() }
    fun clearSnackbar()                   { _state.update { it.copy(snackbarMessage = null) } }

    // ─── Loading ─────────────────────────────────────────────────────────────

    fun reload() { val a = _state.value.activeAccount ?: return; loadAssets(a, true) }

    private fun loadAssets(account: NamedAccount, isRefresh: Boolean) {
        viewModelScope.launch {
            if (isRefresh) _state.update { it.copy(isRefreshing = true, error = null) }
            else           _state.update { it.copy(isLoading = true, loadingMessage = "", totalLoaded = 0, error = null) }

            val provider = Providers.find(account.providerKey)
            val resultFlow = when (provider.authType) {
                ProviderAuthType.CLOUDINARY     -> cloudRepo.fetchAllAssets(account.toCredentials()!!)
                ProviderAuthType.S3_COMPATIBLE  -> s3Repo.fetchAllAssets(account)
                ProviderAuthType.OAUTH_GOOGLE   -> gDriveRepo.fetchAllAssets(account)
                ProviderAuthType.OAUTH_DROPBOX  -> dropboxRepo.fetchAllAssets(account)
                ProviderAuthType.OAUTH_ONEDRIVE -> oneDriveRepo.fetchAllAssets(account)
                ProviderAuthType.OAUTH_BOX      -> boxRepo.fetchAllAssets(account)
                ProviderAuthType.BASIC_WEBDAV   -> webDavRepo.fetchAllAssets(account)
            }

            resultFlow.collect { result ->
                when (result) {
                    is RepositoryResult.Progress -> if (!isRefresh) _state.update {
                        it.copy(loadingMessage = result.message, totalLoaded = result.loaded)
                    }
                    is RepositoryResult.Success  -> {
                        cache.save(account.id, result.assets)
                        _state.update { it.copy(isLoading = false, isRefreshing = false, allAssets = result.assets, error = null) }
                        reFilter()
                    }
                    is RepositoryResult.Error -> _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = result.message)
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
            FileTypeFilter.ALL -> list; FileTypeFilter.AUDIO -> list.filter { it.isAudio }
            FileTypeFilter.VIDEO -> list.filter { it.isVideo }; FileTypeFilter.IMAGE -> list.filter { it.isImage }
            FileTypeFilter.PDF -> list.filter { it.isPdf }; FileTypeFilter.OTHER -> list.filter { it.isOther }
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
        _state.update { it.copy(filteredAssets = list) }
    }

    override fun onCleared() { super.onCleared(); player?.release(); player = null }
}
