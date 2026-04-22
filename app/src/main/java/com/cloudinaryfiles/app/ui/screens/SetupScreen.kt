package com.cloudinaryfiles.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudinaryfiles.app.data.model.ProviderAuthType
import com.cloudinaryfiles.app.data.model.ProviderDef
import com.cloudinaryfiles.app.data.model.Providers
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import com.cloudinaryfiles.app.data.preferences.UserPreferences
import com.cloudinaryfiles.app.data.repository.BoxRepository
import com.cloudinaryfiles.app.data.repository.DropboxRepository
import com.cloudinaryfiles.app.data.repository.GoogleDriveRepository
import com.cloudinaryfiles.app.data.repository.LoopbackOAuthServer
import com.cloudinaryfiles.app.data.repository.OneDriveRepository
import com.cloudinaryfiles.app.data.repository.PkceUtil
import com.cloudinaryfiles.app.ui.theme.*
import com.cloudinaryfiles.app.ui.components.ExcludedFoldersSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onNavigateToFiles: () -> Unit,
    addMode: Boolean = false,
    editAccountId: String? = null   // non-null = edit existing account
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs   = remember { UserPreferences(context) }
    val focus   = LocalFocusManager.current
    val savedAccounts by prefs.accounts.collectAsStateWithLifecycle(initialValue = emptyList())

    // Navigate to files if already connected (only on initial setup screen, not add/edit mode)
    LaunchedEffect(savedAccounts) {
        if (!addMode && editAccountId == null && savedAccounts.isNotEmpty()) onNavigateToFiles()
    }

    var selectedProvider by remember { mutableStateOf(Providers.all.first()) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var accountName  by remember { mutableStateOf("") }

    // Cloudinary
    var cloudName  by remember { mutableStateOf("") }
    var apiKey     by remember { mutableStateOf("") }
    var apiSecret  by remember { mutableStateOf("") }

    // S3
    var s3Endpoint  by remember { mutableStateOf("") }
    var s3Region    by remember { mutableStateOf("us-east-1") }
    var s3Bucket    by remember { mutableStateOf("") }
    var s3AccessKey by remember { mutableStateOf("") }
    var s3SecretKey by remember { mutableStateOf("") }
    var s3PathStyle by remember { mutableStateOf(false) }

    // OAuth
    var oauthClientId     by remember { mutableStateOf("") }
    var oauthClientSecret by remember { mutableStateOf("") }
    var showOAuthHint by remember { mutableStateOf(true) }

    // WebDAV
    var webDavUrl  by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPass by remember { mutableStateOf("") }

    // Visibility toggles
    var showApiSecret  by remember { mutableStateOf(false) }
    var showS3Secret   by remember { mutableStateOf(false) }
    var showOAuthSec   by remember { mutableStateOf(false) }
    var showWdPass     by remember { mutableStateOf(false) }
    var showApiKey     by remember { mutableStateOf(false) }

    // Folder exclusions
    var excludedFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingMsg by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf<String?>(null) }

    // Manual code paste fallback (if loopback browser redirect fails)
    var manualPasteVisible by remember { mutableStateOf(false) }
    var manualPasteText    by remember { mutableStateOf("") }
    var manualPasteAccount by remember { mutableStateOf<NamedAccount?>(null) }
    var manualPastePort    by remember { mutableStateOf(0) }

    // Pre-fill all fields when editing an existing account
    LaunchedEffect(editAccountId) {
        if (editAccountId != null) {
            val account = prefs.accounts.first().firstOrNull { it.id == editAccountId } ?: return@LaunchedEffect
            selectedProvider  = Providers.find(account.providerKey)
            accountName       = account.name
            cloudName         = account.cloudName
            excludedFolders   = account.excludedFolders
            apiKey            = account.apiKey
            apiSecret         = account.apiSecret
            s3Endpoint        = account.s3Endpoint
            s3Region          = account.s3Region.ifBlank { "us-east-1" }
            s3Bucket          = account.s3Bucket
            s3AccessKey       = account.s3AccessKey
            s3SecretKey       = account.s3SecretKey
            s3PathStyle       = account.s3ForcePathStyle
            oauthClientId     = account.oauthClientId
            oauthClientSecret = account.oauthClientSecret
            webDavUrl         = account.webDavUrl
            webDavUser        = account.webDavUser
            webDavPass        = account.webDavPass
        }
    }

    // AppAuth for Dropbox/OneDrive/Box
    var pendingAccountId by remember { mutableStateOf<String?>(null) }
    var pendingPort by remember { mutableStateOf(0) }

    // Google Drive loopback flow
    fun startGoogleOAuth() {
        if (oauthClientId.isBlank()) { error = "Client ID is required"; return }
        if (oauthClientSecret.isBlank()) { error = "Client Secret is required. Copy it from Google Cloud Console."; return }
        isLoading = true; loadingMsg = "Starting authorization…"; error = null
        scope.launch {
            val accId  = editAccountId ?: "acct_${UUID.randomUUID()}"
            val server = LoopbackOAuthServer()
            val port   = server.start()
            pendingPort = port

            val stashedAccount = NamedAccount(
                id = accId, name = accountName.ifBlank { "Google Drive" },
                providerKey = selectedProvider.key,
                oauthClientId = oauthClientId.trim(), oauthClientSecret = oauthClientSecret.trim()
            )
            // Store for manual-paste fallback; DO NOT save to prefs yet
            manualPasteAccount = stashedAccount
            manualPastePort    = port
            manualPasteVisible = false

            val authUrl = GoogleDriveRepository.buildAuthUrl(oauthClientId.trim(), port)

            // Start listening BEFORE browser opens to avoid race condition
            val codeDeferred = async(Dispatchers.IO) { server.waitForCode() }

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            loadingMsg = "Waiting for Google sign-in…\n(Complete in browser, then return here)"

            try {
                val code = codeDeferred.await()
                server.stop()
                loadingMsg = "Exchanging tokens…"
                val result = withContext(Dispatchers.IO) {
                    GoogleDriveRepository().exchangeCodeForToken(stashedAccount, code, port)
                }
                // Save ONLY after successful token exchange — avoids loading with empty tokens
                val finalAccount = stashedAccount.copy(
                    oauthAccessToken  = result.accessToken,
                    oauthRefreshToken = result.refreshToken,
                    oauthTokenExpiry  = result.expiryEpoch
                ,
                    excludedFolders = excludedFolders)
                prefs.saveAccount(finalAccount)
                prefs.setActiveAccount(accId)
                isLoading = false
                onNavigateToFiles()
            } catch (e: Exception) {
                codeDeferred.cancel()
                server.stop()
                error = "Sign-in failed: ${e.message}"; isLoading = false; loadingMsg = ""
            }
        }
    }

    // Manual code paste: user pastes the full redirect URL from browser address bar
    // e.g. "http://127.0.0.1:38821/?code=4/0AbCD..."
    fun submitManualCode() {
        val acct = manualPasteAccount ?: run { error = "No pending sign-in"; return }
        val port = manualPastePort
        val raw  = manualPasteText.trim()
        // Accept either the full URL or just the bare code
        val code = Regex("[?&]code=([^&\\s]+)").find(raw)?.groupValues?.get(1)
            ?: raw.takeIf { it.isNotBlank() }
            ?: run { error = "Paste the full redirect URL or just the code"; return }
        manualPasteVisible = false
        isLoading = true; loadingMsg = "Exchanging tokens…"; error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    GoogleDriveRepository().exchangeCodeForToken(acct, code, port)
                }
                prefs.updateOAuthTokens(acct.id, result.accessToken, result.refreshToken, result.expiryEpoch)
                prefs.setActiveAccount(acct.id)
                isLoading = false
                onNavigateToFiles()
            } catch (e: Exception) {
                prefs.deleteAccount(acct.id)
                error = "Token exchange failed: ${e.message}"; isLoading = false; loadingMsg = ""
            }
        }
    }

    // Loopback OAuth for Dropbox / OneDrive / Box (avoids custom URI scheme restrictions)
    fun startAppAuth() {
        val p = selectedProvider
        if (oauthClientId.isBlank()) { error = "Client ID is required"; return }
        isLoading = true; loadingMsg = "Starting authorization…"; error = null
        scope.launch {
            val accId  = editAccountId ?: "acct_${UUID.randomUUID()}"
            val server = LoopbackOAuthServer()
            val port   = server.start()

            val stashedAccount = NamedAccount(
                id = accId, name = accountName.ifBlank { p.label }, providerKey = p.key,
                oauthClientId = oauthClientId.trim(), oauthClientSecret = oauthClientSecret.trim()
            )

            var pkceVerifier: String? = null
            val authUrl = when (p.authType) {
                ProviderAuthType.OAUTH_DROPBOX -> buildString {
                    pkceVerifier = PkceUtil.generateCodeVerifier()
                    val pkceChallenge = PkceUtil.generateCodeChallenge(pkceVerifier!!)
                    append(DropboxRepository.AUTH_URL)
                    append("?client_id=${encode(oauthClientId.trim())}")
                    append("&redirect_uri=${encode("http://127.0.0.1:$port")}")
                    append("&response_type=code")
                    append("&token_access_type=offline")
                    append("&code_challenge=$pkceChallenge")
                    append("&code_challenge_method=S256")
                }
                ProviderAuthType.OAUTH_ONEDRIVE -> buildString {
                    append(OneDriveRepository.AUTH_URL)
                    append("?client_id=${encode(oauthClientId.trim())}")
                    append("&redirect_uri=${encode("http://127.0.0.1:$port")}")
                    append("&response_type=code")
                    append("&scope=${encode(OneDriveRepository.SCOPES)}")
                }
                ProviderAuthType.OAUTH_BOX -> buildString {
                    append(BoxRepository.AUTH_URL)
                    append("?client_id=${encode(oauthClientId.trim())}")
                    append("&redirect_uri=${encode("http://127.0.0.1:$port")}")
                    append("&response_type=code")
                }
                else -> { server.stop(); isLoading = false; return@launch }
            }

            // Start listening BEFORE browser opens
            val codeDeferred = async(Dispatchers.IO) { server.waitForCode() }

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            loadingMsg = "Waiting for ${p.label} sign-in…\n(Complete in browser, then return here)"

            try {
                val code = codeDeferred.await()
                server.stop()
                loadingMsg = "Exchanging tokens…"
                val result = withContext(Dispatchers.IO) {
                    when (p.authType) {
                        ProviderAuthType.OAUTH_DROPBOX  -> { val r = DropboxRepository().exchangeCodeForToken(stashedAccount, code, port, pkceVerifier); Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_ONEDRIVE -> { val r = OneDriveRepository().exchangeCodeForToken(stashedAccount, code, port); Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_BOX      -> { val r = BoxRepository().exchangeCodeForToken(stashedAccount, code, port);      Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        else -> throw Exception("Unknown provider")
                    }
                }
                // Save ONLY after successful token exchange
                val finalAccount = stashedAccount.copy(
                    oauthAccessToken  = result.first,
                    oauthRefreshToken = result.second,
                    oauthTokenExpiry  = result.third
                ,
                    excludedFolders = excludedFolders)
                prefs.saveAccount(finalAccount)
                prefs.setActiveAccount(accId)
                isLoading = false; loadingMsg = ""
                onNavigateToFiles()
            } catch (e: Exception) {
                codeDeferred.cancel()
                server.stop()
                error = "Sign-in failed: ${e.message}"; isLoading = false; loadingMsg = ""
            }
        }
    }

    fun saveNonOAuth() {
        val p = selectedProvider; error = null
        when (p.authType) {
            ProviderAuthType.CLOUDINARY    -> if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) { error = "All fields required"; return }
            ProviderAuthType.S3_COMPATIBLE -> if (s3Bucket.isBlank() || s3AccessKey.isBlank() || s3SecretKey.isBlank()) { error = "Bucket, Access Key and Secret are required"; return }
            ProviderAuthType.BASIC_WEBDAV  -> if (webDavUrl.isBlank()) { error = "Server URL is required"; return }
            else -> return
        }
        isLoading = true
        scope.launch {
            val name = accountName.ifBlank {
                when (p.authType) {
                    ProviderAuthType.CLOUDINARY -> cloudName.trim()
                    ProviderAuthType.S3_COMPATIBLE -> s3Bucket.trim()
                    else -> p.label
                }
            }
            val account = NamedAccount(
                id = editAccountId ?: "acct_${UUID.randomUUID()}",  // preserve id when editing
                name = name, providerKey = p.key,
                cloudName = cloudName.trim(), apiKey = apiKey.trim(), apiSecret = apiSecret.trim(),
                s3Endpoint = s3Endpoint.trim().ifBlank { p.s3Endpoint },
                s3Region = s3Region.trim(), s3Bucket = s3Bucket.trim(),
                s3AccessKey = s3AccessKey.trim(), s3SecretKey = s3SecretKey,
                s3ForcePathStyle = s3PathStyle,
                webDavUrl = webDavUrl.trim().trimEnd('/'),
                webDavUser = webDavUser.trim(), webDavPass = webDavPass,
                excludedFolders = excludedFolders
            )
            prefs.saveAccount(account)
            prefs.setActiveAccount(account.id)
            isLoading = false
            onNavigateToFiles()
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.radialGradient(listOf(Color(0xFF1A1050), SurfaceDark), Offset(0.5f, 0.2f), 900f)
    )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()                        // pushes content above keyboard
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(44.dp))

            // Logo
            Box(Modifier.size(72.dp).background(
                Brush.linearGradient(listOf(AudioAccent2, AudioAccent)), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Cloud, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("CloudVault", style = MaterialTheme.typography.headlineLarge,
                color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(when {
                editAccountId != null -> "Edit account"
                addMode -> "Add another account"
                else -> "Connect your cloud storage"
            }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.6f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            // Provider picker card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.85f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Provider", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = providerMenuOpen, onExpandedChange = { providerMenuOpen = it }) {
                        OutlinedTextField(
                            value = "${selectedProvider.emoji}  ${selectedProvider.label}",
                            onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        ExposedDropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false },
                            modifier = Modifier.heightIn(max = 360.dp)) {
                            val groups = listOf(
                                null to listOf(Providers.all.first()),
                                "S3-compatible" to Providers.all.filter { it.authType == ProviderAuthType.S3_COMPATIBLE },
                                "OAuth2 (browser sign-in)" to Providers.all.filter { it.authType.name.startsWith("OAUTH") },
                                "WebDAV / Basic auth" to Providers.all.filter { it.authType == ProviderAuthType.BASIC_WEBDAV }
                            )
                            groups.forEach { (label, items) ->
                                if (label != null) {
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                                        onClick = {}, enabled = false)
                                }
                                items.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(provider.emoji, fontSize = 18.sp)
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Text(provider.label, style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (selectedProvider == provider) FontWeight.Bold else FontWeight.Normal)
                                                    Text(provider.hint, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        leadingIcon = if (selectedProvider == provider) ({
                                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }) else null,
                                        onClick = {
                                            selectedProvider = provider; providerMenuOpen = false; error = null; showOAuthHint = true
                                            // Always reset S3 endpoint/region to the new provider's defaults
                                            s3Endpoint = provider.s3Endpoint
                                            s3Region   = provider.s3Region.ifBlank { "us-east-1" }
                                            // Clear OAuth credentials on provider switch to avoid confusion
                                            oauthClientId = ""; oauthClientSecret = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CField("Account name (optional)", accountName, { accountName = it },
                        Icons.Outlined.Label, focus, placeholder = selectedProvider.label)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Credentials card
            AnimatedContent(
                targetState = selectedProvider.authType,
                transitionSpec = { fadeIn() + slideInVertically { 30 } togetherWith fadeOut() },
                label = "creds"
            ) { authType ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.85f))) {
                    Column(Modifier.padding(16.dp)) {
                        when (authType) {
                            // ── Cloudinary ───────────────────────────────────────────
                            ProviderAuthType.CLOUDINARY -> {
                                CredHeader("Cloudinary Credentials", "Console → Settings → API Keys")
                                CField("Cloud Name", cloudName, { cloudName = it; error = null }, Icons.Outlined.Cloud, focus)
                                Spacer(Modifier.height(10.dp))
                                CField("API Key", apiKey, { apiKey = it; error = null }, Icons.Outlined.Key, focus,
                                    showToggle = true, show = showApiKey, onShowToggle = { showApiKey = it })
                                Spacer(Modifier.height(10.dp))
                                CField("API Secret", apiSecret, { apiSecret = it; error = null }, Icons.Outlined.Lock, focus,
                                    showToggle = true, show = showApiSecret, onShowToggle = { showApiSecret = it },
                                    imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect", isLoading, loadingMsg, ::saveNonOAuth)
                            }

                            // ── S3 ───────────────────────────────────────────────────
                            ProviderAuthType.S3_COMPATIBLE -> {
                                CredHeader("S3 Credentials",
                                    if (selectedProvider.s3Endpoint.isNotBlank()) "Endpoint pre-filled for ${selectedProvider.label}" else "Enter your endpoint and credentials")
                                CField("Endpoint", s3Endpoint, { s3Endpoint = it }, Icons.Outlined.Language, focus,
                                    placeholder = selectedProvider.s3Endpoint.ifBlank { "s3.amazonaws.com" })
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1.4f)) {
                                        CField("Bucket", s3Bucket, { s3Bucket = it; error = null }, Icons.Outlined.FolderOpen, focus)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        CField("Region", s3Region, { s3Region = it }, Icons.Outlined.Public, focus)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                CField("Access Key ID", s3AccessKey, { s3AccessKey = it; error = null }, Icons.Outlined.Key, focus)
                                Spacer(Modifier.height(10.dp))
                                CField("Secret Access Key", s3SecretKey, { s3SecretKey = it; error = null }, Icons.Outlined.Lock, focus,
                                    showToggle = true, show = showS3Secret, onShowToggle = { showS3Secret = it },
                                    imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = s3PathStyle, onCheckedChange = { s3PathStyle = it })
                                    Spacer(Modifier.width(10.dp))
                                    Text("Force path-style URLs (MinIO / custom host)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect to ${selectedProvider.label}", isLoading, loadingMsg, ::saveNonOAuth)
                            }

                            // ── Google Drive (loopback) ─────────────────────────────
                            ProviderAuthType.OAUTH_GOOGLE -> {
                                CredHeader("Google Drive OAuth2", "Uses loopback redirect — no Error 400")
                                // Collapsible setup guide
                                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = 14.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Setup guide (one-time)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold)
                                            TextButton(onClick = { showOAuthHint = !showOAuthHint },
                                                contentPadding = PaddingValues(0.dp)) {
                                                Text(if (showOAuthHint) "Hide" else "Show",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        AnimatedVisibility(showOAuthHint) {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                listOf(
                                                    "1. Go to console.cloud.google.com",
                                                    "2. Enable Google Drive API",
                                                    "3. Credentials → Create OAuth client ID",
                                                    "4. Choose type: Desktop app",
                                                    "5. Add redirect URI: http://127.0.0.1",
                                                    "6. In Audience → add yourself as test user",
                                                    "7. Copy Client ID and Client Secret below"
                                                ).forEach { step ->
                                                    Text(step, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                                CField("Client ID", oauthClientId, { oauthClientId = it; error = null },
                                    Icons.Outlined.AppRegistration, focus)
                                Spacer(Modifier.height(10.dp))
                                CField("Client Secret", oauthClientSecret, { oauthClientSecret = it },
                                    Icons.Outlined.Lock, focus,
                                    showToggle = true, show = showOAuthSec, onShowToggle = { showOAuthSec = it })
                                ErrorBanner(error)
                                // Loading state with status message
                                AnimatedVisibility(isLoading && loadingMsg.isNotBlank()) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                            CircularProgressIndicator(Modifier.size(16.dp).padding(top = 2.dp),
                                                color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(loadingMsg, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                                // Manual paste fallback — shown when waiting for redirect
                                AnimatedVisibility(isLoading && loadingMsg.contains("sign-in")) {
                                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        TextButton(
                                            onClick = { manualPasteVisible = !manualPasteVisible },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                if (manualPasteVisible) "▲ Hide manual entry"
                                                else "▼ Browser didn't redirect? Paste code manually",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        AnimatedVisibility(manualPasteVisible) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(
                                                        "After Google approves, copy the full URL from your browser's address bar " +
                                                        "(e.g. http://127.0.0.1:38821/?code=4/0AB…) and paste it below.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = manualPasteText,
                                                        onValueChange = { manualPasteText = it },
                                                        label = { Text("Paste redirect URL or code") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = false,
                                                        maxLines = 4,
                                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Button(
                                                        onClick = ::submitManualCode,
                                                        enabled = manualPasteText.isNotBlank(),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text("Submit Code") }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Sign in with Google 🔐", isLoading, "", ::startGoogleOAuth)
                            }

                            // ── Dropbox / OneDrive / Box ────────────────────────────
                            ProviderAuthType.OAUTH_DROPBOX,
                            ProviderAuthType.OAUTH_ONEDRIVE,
                            ProviderAuthType.OAUTH_BOX -> {
                                val (consoleUrl, steps) = when (authType) {
                                    ProviderAuthType.OAUTH_DROPBOX -> "www.dropbox.com/developers" to listOf(
                                        "1. Create app at dropbox.com/developers",
                                        "2. Choose Scoped access → Full Dropbox",
                                        "3. In OAuth 2 settings, add redirect URI:",
                                        "4. Copy App key (Client ID) and App secret"
                                    )
                                    ProviderAuthType.OAUTH_ONEDRIVE -> "portal.azure.com" to listOf(
                                        "1. Azure Portal → App registrations → New",
                                        "2. Platform: Mobile/Desktop app",
                                        "3. Add redirect URI (see below)",
                                        "4. Copy Application (client) ID"
                                    )
                                    else -> "developer.box.com" to listOf(
                                        "1. developer.box.com → My Apps → New App",
                                        "2. Custom App → Standard OAuth 2.0",
                                        "3. Add redirect URI (see below)",
                                        "4. Copy Client ID and Client Secret"
                                    )
                                }
                                CredHeader("${selectedProvider.label} OAuth2", "Register app at: $consoleUrl")
                                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("Setup guide (one-time)", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            TextButton(onClick = { showOAuthHint = !showOAuthHint },
                                                contentPadding = PaddingValues(0.dp)) {
                                                Text(if (showOAuthHint) "Hide" else "Show",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        AnimatedVisibility(showOAuthHint) {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                steps.forEach { step ->
                                                    Text(step, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text("Redirect URI to register (use http://127.0.0.1 — no port needed):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                                Surface(color = Color.Black.copy(0.3f), shape = RoundedCornerShape(6.dp)) {
                                                    Text("http://127.0.0.1",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                                CField("Client ID", oauthClientId, { oauthClientId = it; error = null },
                                    Icons.Outlined.AppRegistration, focus)
                                if (authType != ProviderAuthType.OAUTH_ONEDRIVE) {
                                    Spacer(Modifier.height(10.dp))
                                    CField("Client Secret", oauthClientSecret, { oauthClientSecret = it },
                                        Icons.Outlined.Lock, focus,
                                        showToggle = true, show = showOAuthSec, onShowToggle = { showOAuthSec = it })
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Sign in with ${selectedProvider.label} ${selectedProvider.emoji}",
                                    isLoading, loadingMsg, ::startAppAuth)
                            }

                            // ── WebDAV ───────────────────────────────────────────────
                            ProviderAuthType.BASIC_WEBDAV -> {
                                CredHeader("Direct Connection", "${selectedProvider.label} — URL + credentials")
                                val urlPlaceholder = when (selectedProvider.key) {
                                    "nextcloud" -> "https://cloud.example.com/remote.php/dav/files/user"
                                    "owncloud"  -> "https://cloud.example.com/remote.php/webdav"
                                    "pcloud"    -> "https://webdav.pcloud.com"
                                    "yandex"    -> "https://webdav.yandex.com"
                                    "koofr"     -> "https://app.koofr.net/dav/Koofr"
                                    else        -> "https://your-server/webdav"
                                }
                                CField("Server URL", webDavUrl, { webDavUrl = it; error = null },
                                    Icons.Outlined.Language, focus, placeholder = urlPlaceholder,
                                    keyboardType = KeyboardType.Uri)
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        CField("Username", webDavUser, { webDavUser = it },
                                            Icons.Outlined.Person, focus)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        CField("Password", webDavPass, { webDavPass = it },
                                            Icons.Outlined.Lock, focus,
                                            showToggle = true, show = showWdPass, onShowToggle = { showWdPass = it },
                                            imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                    }
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect to ${selectedProvider.label}", isLoading, loadingMsg, ::saveNonOAuth)
                            }
                        }
                    }
                }
            }

            // Folder exclusion (only relevant for OAuth + S3 + WebDAV — not Cloudinary which uses search API)
            if (selectedProvider.authType != com.cloudinaryfiles.app.data.model.ProviderAuthType.CLOUDINARY) {
                Spacer(Modifier.height(16.dp))
                ExcludedFoldersSection(
                    excludedFolders = excludedFolders,
                    onFoldersChanged = { excludedFolders = it }
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = Color.White.copy(0.35f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Credentials stored locally on device only",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun CredHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
}

@Composable
private fun CField(
    label: String, value: String, onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focus: androidx.compose.ui.focus.FocusManager,
    placeholder: String = "",
    showToggle: Boolean = false, show: Boolean = false, onShowToggle: (Boolean) -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }) else null,
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (showToggle) ({
            IconButton(onClick = { onShowToggle(!show) }) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null,
                    modifier = Modifier.size(18.dp))
            }
        }) else null,
        visualTransformation = if (showToggle && !show) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (showToggle && !show) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { focus.clearFocus(); onDone() }
        )
    )
}

@Composable
private fun ErrorBanner(error: String?) {
    AnimatedVisibility(visible = error != null) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

@Composable
private fun ConnectButton(text: String, isLoading: Boolean, loadingMsg: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isLoading && loadingMsg.isBlank()) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.CloudDone, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}
