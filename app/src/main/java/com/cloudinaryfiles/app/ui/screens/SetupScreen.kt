package com.cloudinaryfiles.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.cloudinaryfiles.app.data.repository.OneDriveRepository
import com.cloudinaryfiles.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
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

    // Auto-navigate on first launch when creds already exist
    LaunchedEffect(savedCreds) { if (!addMode && savedCreds != null) onNavigateToFiles() }

    // ── State ────────────────────────────────────────────────────────────────
    var selectedProvider by remember { mutableStateOf(Providers.all.first()) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var accountName  by remember { mutableStateOf("") }

    // Cloudinary
    var cloudName  by remember { mutableStateOf("") }
    var apiKey     by remember { mutableStateOf("") }
    var apiSecret  by remember { mutableStateOf("") }
    var showKey    by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }

    // S3
    var s3Endpoint  by remember { mutableStateOf("") }
    var s3Region    by remember { mutableStateOf("us-east-1") }
    var s3Bucket    by remember { mutableStateOf("") }
    var s3AccessKey by remember { mutableStateOf("") }
    var s3SecretKey by remember { mutableStateOf("") }
    var s3PathStyle by remember { mutableStateOf(false) }
    var showS3Secret by remember { mutableStateOf(false) }

    // OAuth
    var oauthClientId     by remember { mutableStateOf("") }
    var oauthClientSecret by remember { mutableStateOf("") }

    // WebDAV
    var webDavUrl  by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPass by remember { mutableStateOf("") }
    var showWdPass by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf<String?>(null) }

    // OAuth PKCE flow via AppAuth ─────────────────────────────────────────────
    var pendingAccountId by remember { mutableStateOf<String?>(null) }
    val authService = remember { AuthorizationService(context) }
    DisposableEffect(Unit) { onDispose { authService.dispose() } }

    val oauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val authResponse = AuthorizationResponse.fromIntent(data)
        val authException = AuthorizationException.fromIntent(data)
        if (authException != null || authResponse == null) {
            error = authException?.message ?: "OAuth cancelled"; isLoading = false; return@rememberLauncherForActivityResult
        }
        isLoading = true
        scope.launch {
            try {
                val code = authResponse.authorizationCode!!
                val accId = pendingAccountId ?: return@launch
                val account = prefs.accounts.collect { list ->
                    list.firstOrNull { it.id == accId }
                }.let {
                    // Can't use collect like that — re-read from datastore
                    null
                }
                // Re-load account from prefs
                var targetAccount: NamedAccount? = null
                prefs.accounts.collect { list ->
                    targetAccount = list.firstOrNull { it.id == accId }
                    return@collect // break after first emit
                }
                val acct = targetAccount ?: run { error = "Account not found"; return@launch }
                val tokenResult = withContext(Dispatchers.IO) {
                    when (selectedProvider.authType) {
                        ProviderAuthType.OAUTH_GOOGLE   -> { val r = GoogleDriveRepository().exchangeCodeForToken(acct, code); Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_DROPBOX  -> { val r = DropboxRepository().exchangeCodeForToken(acct, code);    Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_ONEDRIVE -> { val r = OneDriveRepository().exchangeCodeForToken(acct, code);   Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        ProviderAuthType.OAUTH_BOX      -> { val r = BoxRepository().exchangeCodeForToken(acct, code);        Triple(r.accessToken, r.refreshToken, r.expiryEpoch) }
                        else -> throw Exception("Unknown OAuth provider")
                    }
                }
                prefs.updateOAuthTokens(accId, tokenResult.first, tokenResult.second, tokenResult.third)
                prefs.setActiveAccount(accId)
                isLoading = false
                onNavigateToFiles()
            } catch (e: Exception) {
                error = "Auth failed: ${e.message}"; isLoading = false
            }
        }
    }

    fun startOAuth() {
        val p = selectedProvider
        val (authEndpoint, tokenEndpoint, scopes) = when (p.authType) {
            ProviderAuthType.OAUTH_GOOGLE   -> Triple(GoogleDriveRepository.AUTH_URL, GoogleDriveRepository.TOKEN_URL, GoogleDriveRepository.SCOPES)
            ProviderAuthType.OAUTH_DROPBOX  -> Triple(DropboxRepository.AUTH_URL,     DropboxRepository.TOKEN_URL,     DropboxRepository.SCOPES)
            ProviderAuthType.OAUTH_ONEDRIVE -> Triple(OneDriveRepository.AUTH_URL,    OneDriveRepository.TOKEN_URL,    OneDriveRepository.SCOPES)
            ProviderAuthType.OAUTH_BOX      -> Triple(BoxRepository.AUTH_URL,         BoxRepository.TOKEN_URL,         "root_readonly")
            else -> return
        }
        if (oauthClientId.isBlank()) { error = "Client ID is required"; return }
        isLoading = true; error = null
        scope.launch {
            val accId = "acct_${UUID.randomUUID()}"
            pendingAccountId = accId
            val name = accountName.ifBlank { p.label }
            val account = NamedAccount(
                id = accId, name = name, providerKey = p.key,
                oauthClientId = oauthClientId.trim(),
                oauthClientSecret = oauthClientSecret.trim()
            )
            prefs.saveAccount(account)
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse(authEndpoint), Uri.parse(tokenEndpoint)
            )
            val redirectUri = Uri.parse("com.cloudinaryfiles.app://oauth")
            val reqBuilder = AuthorizationRequest.Builder(
                serviceConfig, oauthClientId.trim(),
                ResponseTypeValues.CODE, redirectUri
            ).setScope(scopes)
            if (p.authType == ProviderAuthType.OAUTH_DROPBOX) reqBuilder.setAdditionalParameters(mapOf("token_access_type" to "offline"))
            val authReq  = reqBuilder.build()
            val authIntent = authService.getAuthorizationRequestIntent(authReq)
            oauthLauncher.launch(authIntent)
        }
    }

    fun saveNonOAuth() {
        val p = selectedProvider; error = null
        when (p.authType) {
            ProviderAuthType.CLOUDINARY -> {
                if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) { error = "All fields required"; return }
            }
            ProviderAuthType.S3_COMPATIBLE -> {
                if (s3Bucket.isBlank() || s3AccessKey.isBlank() || s3SecretKey.isBlank()) { error = "Bucket, Access Key and Secret are required"; return }
            }
            ProviderAuthType.BASIC_WEBDAV -> {
                if (webDavUrl.isBlank()) { error = "Server URL is required"; return }
            }
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
            val ep = s3Endpoint.trim().ifBlank { p.s3Endpoint }
            val account = NamedAccount(
                id = "acct_${UUID.randomUUID()}", name = name, providerKey = p.key,
                cloudName = cloudName.trim(), apiKey = apiKey.trim(), apiSecret = apiSecret.trim(),
                s3Endpoint = ep, s3Region = s3Region.trim(), s3Bucket = s3Bucket.trim(),
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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(44.dp))

            // Logo
            Box(Modifier.size(76.dp).background(Brush.linearGradient(listOf(AudioAccent2, AudioAccent)), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Cloud, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("CloudVault", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(if (addMode) "Add another account" else "Connect your cloud storage",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.6f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            // ── Provider picker ──────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.88f))) {
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
                            // Group providers by auth type
                            val groups = listOf(
                                null to listOf(Providers.all.first()),
                                "S3-compatible storage" to Providers.all.filter { it.authType == ProviderAuthType.S3_COMPATIBLE },
                                "OAuth2 providers" to Providers.all.filter { it.authType.name.startsWith("OAUTH") },
                                "WebDAV / Direct" to Providers.all.filter { it.authType == ProviderAuthType.BASIC_WEBDAV }
                            )
                            groups.forEach { (label, items) ->
                                if (label != null) {
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                                        onClick = {}, enabled = false,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
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
                                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp))
                                        }) else null,
                                        onClick = {
                                            selectedProvider = provider
                                            providerMenuOpen = false
                                            error = null
                                            // Pre-fill default endpoint
                                            if (provider.s3Endpoint.isNotBlank() && s3Endpoint.isBlank())
                                                s3Endpoint = provider.s3Endpoint
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = accountName, onValueChange = { accountName = it },
                        label = { Text("Account name (optional)") },
                        leadingIcon = { Icon(Icons.Outlined.Label, null) },
                        placeholder = { Text(selectedProvider.label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Credentials card ─────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedProvider.authType,
                transitionSpec = { fadeIn() + slideInVertically { 30 } togetherWith fadeOut() },
                label = "creds"
            ) { authType ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.88f))) {
                    Column(Modifier.padding(16.dp)) {
                        when (authType) {
                            // ── Cloudinary ───────────────────────────────────
                            ProviderAuthType.CLOUDINARY -> {
                                CredSectionHeader("Cloudinary API Credentials",
                                    "Cloudinary Console → Settings → API Keys")
                                CField("Cloud Name", cloudName, { cloudName = it; error = null },
                                    Icons.Outlined.Cloud, focus = focus)
                                Spacer(Modifier.height(10.dp))
                                CField("API Key", apiKey, { apiKey = it; error = null },
                                    Icons.Outlined.Key, focus = focus,
                                    showToggle = true, show = showKey, onShowToggle = { showKey = it })
                                Spacer(Modifier.height(10.dp))
                                CField("API Secret", apiSecret, { apiSecret = it; error = null },
                                    Icons.Outlined.Lock, focus = focus,
                                    showToggle = true, show = showSecret, onShowToggle = { showSecret = it },
                                    imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect", isLoading, onClick = ::saveNonOAuth)
                            }

                            // ── S3-compatible ────────────────────────────────
                            ProviderAuthType.S3_COMPATIBLE -> {
                                CredSectionHeader("S3 Credentials",
                                    if (selectedProvider.s3Endpoint.isNotBlank()) "Endpoint pre-filled — change if needed" else "Enter your endpoint and credentials")
                                CField("Endpoint", s3Endpoint, { s3Endpoint = it },
                                    Icons.Outlined.Language, focus = focus,
                                    placeholder = selectedProvider.s3Endpoint.ifBlank { "s3.amazonaws.com" })
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1.4f)) {
                                        CField("Bucket", s3Bucket, { s3Bucket = it; error = null },
                                            Icons.Outlined.FolderOpen, focus = focus)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        CField("Region", s3Region, { s3Region = it },
                                            Icons.Outlined.Public, focus = focus)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                CField("Access Key ID", s3AccessKey, { s3AccessKey = it; error = null },
                                    Icons.Outlined.Key, focus = focus)
                                Spacer(Modifier.height(10.dp))
                                CField("Secret Access Key", s3SecretKey, { s3SecretKey = it; error = null },
                                    Icons.Outlined.Lock, focus = focus,
                                    showToggle = true, show = showS3Secret, onShowToggle = { showS3Secret = it },
                                    imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = s3PathStyle, onCheckedChange = { s3PathStyle = it },
                                        modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Force path-style URLs (MinIO / custom)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect to ${selectedProvider.label}", isLoading, onClick = ::saveNonOAuth)
                            }

                            // ── OAuth2 providers ─────────────────────────────
                            ProviderAuthType.OAUTH_GOOGLE,
                            ProviderAuthType.OAUTH_DROPBOX,
                            ProviderAuthType.OAUTH_ONEDRIVE,
                            ProviderAuthType.OAUTH_BOX -> {
                                val (consoleUrl, secretNeeded) = when (authType) {
                                    ProviderAuthType.OAUTH_GOOGLE   -> "console.cloud.google.com" to true
                                    ProviderAuthType.OAUTH_DROPBOX  -> "www.dropbox.com/developers" to true
                                    ProviderAuthType.OAUTH_ONEDRIVE -> "portal.azure.com → App registrations" to false
                                    else -> "developer.box.com" to true
                                }
                                CredSectionHeader("OAuth2 App Credentials",
                                    "Register an app at: $consoleUrl")
                                OAuthInstructions(selectedProvider)
                                CField("Client ID", oauthClientId, { oauthClientId = it; error = null },
                                    Icons.Outlined.AppRegistration, focus = focus)
                                if (secretNeeded) {
                                    Spacer(Modifier.height(10.dp))
                                    CField("Client Secret", oauthClientSecret, { oauthClientSecret = it },
                                        Icons.Outlined.Lock, focus = focus,
                                        showToggle = true, show = showSecret, onShowToggle = { showSecret = it })
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Sign in with ${selectedProvider.label} ${selectedProvider.emoji}",
                                    isLoading, onClick = ::startOAuth)
                            }

                            // ── WebDAV / basic auth ──────────────────────────
                            ProviderAuthType.BASIC_WEBDAV -> {
                                CredSectionHeader("WebDAV / Direct Access",
                                    "Connect directly to ${selectedProvider.label}")
                                val placeholder = when (selectedProvider.key) {
                                    "nextcloud" -> "https://cloud.example.com/remote.php/dav/files/username"
                                    "owncloud"  -> "https://cloud.example.com/remote.php/webdav"
                                    "pcloud"    -> "https://webdav.pcloud.com"
                                    "yandex"    -> "https://webdav.yandex.com"
                                    else        -> "https://your-server/webdav"
                                }
                                CField("Server URL", webDavUrl, { webDavUrl = it; error = null },
                                    Icons.Outlined.Language, focus = focus, placeholder = placeholder,
                                    keyboardType = KeyboardType.Uri)
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        CField("Username", webDavUser, { webDavUser = it },
                                            Icons.Outlined.Person, focus = focus)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        CField("Password", webDavPass, { webDavPass = it },
                                            Icons.Outlined.Lock, focus = focus,
                                            showToggle = true, show = showWdPass, onShowToggle = { showWdPass = it },
                                            imeAction = ImeAction.Done, onDone = { focus.clearFocus(); saveNonOAuth() })
                                    }
                                }
                                ErrorBanner(error)
                                Spacer(Modifier.height(16.dp))
                                ConnectButton("Connect to ${selectedProvider.label}", isLoading, onClick = ::saveNonOAuth)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = Color.White.copy(0.35f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Credentials stored locally on device only",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.35f))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Reusable field components ────────────────────────────────────────────────

@Composable
private fun CredSectionHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
}

@Composable
private fun OAuthInstructions(provider: ProviderDef) {
    val (step1, step2, step3) = when (provider.authType) {
        ProviderAuthType.OAUTH_GOOGLE ->
            Triple("Create a project in Google Cloud Console",
                "Enable the Drive API → Create OAuth 2.0 client ID (Android or Web)",
                "Set redirect URI: com.cloudinaryfiles.app://oauth")
        ProviderAuthType.OAUTH_DROPBOX ->
            Triple("Go to dropbox.com/developers → Create app",
                "Choose Scoped access → Full Dropbox → Name it",
                "Add redirect URI: com.cloudinaryfiles.app://oauth")
        ProviderAuthType.OAUTH_ONEDRIVE ->
            Triple("Go to portal.azure.com → App registrations → New registration",
                "Add platform: Mobile/Desktop — set redirect URI: com.cloudinaryfiles.app://oauth",
                "Copy the Application (client) ID")
        else ->
            Triple("Go to developer.box.com → My Apps → Create New App",
                "Choose Custom App → OAuth 2.0 → set redirect URI:",
                "com.cloudinaryfiles.app://oauth")
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Setup (one-time)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            listOf(step1, step2, step3).forEachIndexed { i, s ->
                Row {
                    Text("${i+1}. ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(s, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)) }) else null,
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (showToggle) ({
            IconButton(onClick = { onShowToggle(!show) }, modifier = Modifier.size(32.dp)) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null,
                    modifier = Modifier.size(18.dp))
            }
        }) else null,
        visualTransformation = if (showToggle && !show) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (showToggle && !show) KeyboardType.Password else keyboardType, imeAction = imeAction),
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
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.CloudDone, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}
