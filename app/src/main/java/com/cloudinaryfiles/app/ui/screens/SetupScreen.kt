package com.cloudinaryfiles.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.cloudinaryfiles.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import android.net.Uri
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onNavigateToFiles: () -> Unit,
    addMode: Boolean = false
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs   = remember { UserPreferences(context) }
    val focus   = LocalFocusManager.current
    val savedCreds by prefs.credentials.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(savedCreds) { if (!addMode && savedCreds != null) onNavigateToFiles() }

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

    var isLoading by remember { mutableStateOf(false) }
    var loadingMsg by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf<String?>(null) }

    // AppAuth for Dropbox/OneDrive/Box
    val authService = remember { AuthorizationService(context) }
    DisposableEffect(Unit) { onDispose { authService.dispose() } }
    var pendingAccountId by remember { mutableStateOf<String?>(null) }
    var pendingPort by remember { mutableStateOf(0) }

    val oauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val authResponse  = AuthorizationResponse.fromIntent(data)
        val authException = AuthorizationException.fromIntent(data)
        if (authException != null || authResponse == null) {
            error = authException?.message ?: "Authorization cancelled"
            isLoading = false
            return@rememberLauncherForActivityResult
        }
        isLoading = true
        scope.launch {
            try {
                val code  = authResponse.authorizationCode!!
                val accId = pendingAccountId ?: return@launch
                var targetAccount: NamedAccount? = null
                prefs.accounts.collect { list ->
                    targetAccount = list.firstOrNull { it.id == accId }
                    return@collect
                }
                val acct = targetAccount ?: throw Exception("Account not found")
                val (at, rt, exp) = withContext(Dispatchers.IO) {
                    when (selectedProvider.authType) {
                        ProviderAuthType.OAUTH_DROPBOX  -> { val r = DropboxRepository().exchangeCodeForToken(acct, code);    Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_ONEDRIVE -> { val r = OneDriveRepository().exchangeCodeForToken(acct, code);   Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_BOX      -> { val r = BoxRepository().exchangeCodeForToken(acct, code);        Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        else -> throw Exception("Unknown provider")
                    }
                }
                prefs.updateOAuthTokens(accId, at, rt, exp)
                prefs.setActiveAccount(accId)
                isLoading = false
                onNavigateToFiles()
            } catch (e: Exception) {
                error = "Auth failed: ${e.message}"; isLoading = false
            }
        }
    }

    // Google Drive loopback flow
    fun startGoogleOAuth() {
        if (oauthClientId.isBlank()) { error = "Client ID is required"; return }
        isLoading = true; loadingMsg = "Starting authorization…"; error = null
        scope.launch {
            val accId  = "acct_${UUID.randomUUID()}"
            val server = LoopbackOAuthServer()
            val port   = server.start()
            pendingPort = port

            val name = accountName.ifBlank { "Google Drive" }
            val account = NamedAccount(
                id = accId, name = name, providerKey = selectedProvider.key,
                oauthClientId = oauthClientId.trim(), oauthClientSecret = oauthClientSecret.trim()
            )
            prefs.saveAccount(account)
            pendingAccountId = accId

            val authUrl = GoogleDriveRepository.buildAuthUrl(oauthClientId.trim(), port)

            // Open in browser
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            loadingMsg = "Waiting for Google sign-in…\n(Complete in browser, then return)"

            try {
                val code = withContext(Dispatchers.IO) { server.waitForCode() }
                server.stop()
                loadingMsg = "Exchanging tokens…"

                // Re-fetch account
                var targetAccount: NamedAccount? = null
                prefs.accounts.collect { list ->
                    targetAccount = list.firstOrNull { it.id == accId }
                    return@collect
                }
                val acct = targetAccount ?: throw Exception("Account not found")
                val result = withContext(Dispatchers.IO) {
                    GoogleDriveRepository().exchangeCodeForToken(acct, code, port)
                }
                prefs.updateOAuthTokens(accId, result.accessToken, result.refreshToken, result.expiryEpoch)
                prefs.setActiveAccount(accId)
                isLoading = false
                onNavigateToFiles()
            } catch (e: Exception) {
                server.stop()
                prefs.deleteAccount(accId)
                error = "Sign-in failed: ${e.message}"; isLoading = false; loadingMsg = ""
            }
        }
    }

    // AppAuth for Dropbox/OneDrive/Box
    fun startAppAuth() {
        val p = selectedProvider
        val (authEndpoint, tokenEndpoint, scopes) = when (p.authType) {
            ProviderAuthType.OAUTH_DROPBOX  -> Triple(DropboxRepository.AUTH_URL, DropboxRepository.TOKEN_URL, DropboxRepository.SCOPES)
            ProviderAuthType.OAUTH_ONEDRIVE -> Triple(OneDriveRepository.AUTH_URL, OneDriveRepository.TOKEN_URL, OneDriveRepository.SCOPES)
            ProviderAuthType.OAUTH_BOX      -> Triple(BoxRepository.AUTH_URL, BoxRepository.TOKEN_URL, "root_readonly")
            else -> return
        }
        if (oauthClientId.isBlank()) { error = "Client ID is required"; return }
        isLoading = true; error = null
        scope.launch {
            val accId = "acct_${UUID.randomUUID()}"
            pendingAccountId = accId
            val account = NamedAccount(
                id = accId, name = accountName.ifBlank { p.label }, providerKey = p.key,
                oauthClientId = oauthClientId.trim(), oauthClientSecret = oauthClientSecret.trim()
            )
            prefs.saveAccount(account)
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse(authEndpoint), Uri.parse(tokenEndpoint)
            )
            val redirectUri = Uri.parse("com.cloudinaryfiles.app:/oauth2redirect")
            val reqBuilder = AuthorizationRequest.Builder(
                serviceConfig, oauthClientId.trim(), ResponseTypeValues.CODE, redirectUri
            ).setScope(scopes)
            if (p.authType == ProviderAuthType.OAUTH_DROPBOX)
                reqBuilder.setAdditionalParameters(mapOf("token_access_type" to "offline"))
            val authIntent = authService.getAuthorizationRequestIntent(reqBuilder.build())
            oauthLauncher.launch(authIntent)
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
                id = "acct_${UUID.randomUUID()}", name = name, providerKey = p.key,
                cloudName = cloudName.trim(), apiKey = apiKey.trim(), apiSecret = apiSecret.trim(),
                s3Endpoint = s3Endpoint.trim().ifBlank { p.s3Endpoint },
                s3Region = s3Region.trim(), s3Bucket = s3Bucket.trim(),
                s3AccessKey = s3AccessKey.trim(), s3SecretKey = s3SecretKey,
                s3ForcePathStyle = s3PathStyle,
                webDavUrl = webDavUrl.trim().trimEnd('/'),
                webDavUser = webDavUser.trim(), webDavPass = webDavPass
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
            Text(if (addMode) "Add another account" else "Connect your cloud storage",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.6f),
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
                                            if (provider.s3Endpoint.isNotBlank() && s3Endpoint.isBlank()) s3Endpoint = provider.s3Endpoint
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
                                        "3. Redirect URI: com.cloudinaryfiles.app:/oauth2redirect",
                                        "4. Copy App key (Client ID) and App secret"
                                    )
                                    ProviderAuthType.OAUTH_ONEDRIVE -> "portal.azure.com" to listOf(
                                        "1. Azure Portal → App registrations → New",
                                        "2. Platform: Mobile/Desktop",
                                        "3. Redirect URI: com.cloudinaryfiles.app:/oauth2redirect",
                                        "4. Copy Application (client) ID"
                                    )
                                    else -> "developer.box.com" to listOf(
                                        "1. developer.box.com → My Apps → New App",
                                        "2. Custom App → Standard OAuth 2.0",
                                        "3. Redirect URI: com.cloudinaryfiles.app:/oauth2redirect",
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
                                                Text("Redirect URI to register:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                                Surface(color = Color.Black.copy(0.3f), shape = RoundedCornerShape(6.dp)) {
                                                    Text("com.cloudinaryfiles.app:/oauth2redirect",
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
