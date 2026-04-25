package com.cloudinaryfiles.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudinaryfiles.app.data.preferences.AppOAuthSettings
import com.cloudinaryfiles.app.data.preferences.UserPreferences
import kotlinx.coroutines.launch

// ── Theme Palette Definitions ────────────────────────────────────────────────

data class AppTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val accent: Color
)

val APP_THEMES = listOf(
    AppTheme("midnight_purple", "Midnight Purple", "🟣",
        Color(0xFF7C4DFF), Color(0xFF0D0A1A), Color(0xFF1A1530), Color(0xFF9D6FFF)),
    AppTheme("ocean_blue", "Ocean Blue", "🔵",
        Color(0xFF2196F3), Color(0xFF040E1A), Color(0xFF0A1E30), Color(0xFF64B5F6)),
    AppTheme("emerald", "Emerald Forest", "🟢",
        Color(0xFF00BFA5), Color(0xFF041A14), Color(0xFF0A2820), Color(0xFF4DB6AC)),
    AppTheme("crimson", "Crimson Night", "🔴",
        Color(0xFFE53935), Color(0xFF1A0606), Color(0xFF2D0808), Color(0xFFEF9A9A)),
    AppTheme("amber", "Amber Glow", "🟡",
        Color(0xFFFFA000), Color(0xFF1A0F00), Color(0xFF2A1800), Color(0xFFFFD54F)),
    AppTheme("rose_gold", "Rose Gold", "🌸",
        Color(0xFFE91E8C), Color(0xFF1A040F), Color(0xFF2D0A1B), Color(0xFFF48FB1)),
    AppTheme("arctic", "Arctic White", "❄️",
        Color(0xFF546E7A), Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF90A4AE)),
    AppTheme("neon_cyan", "Neon Cyan", "🩵",
        Color(0xFF00BCD4), Color(0xFF00090C), Color(0xFF001820), Color(0xFF80DEEA)),
    AppTheme("deep_space", "Deep Space", "🌌",
        Color(0xFF673AB7), Color(0xFF050010), Color(0xFF0E0020), Color(0xFFB39DDB)),
    AppTheme("sunset", "Sunset", "🌅",
        Color(0xFFFF6B35), Color(0xFF1A0800), Color(0xFF2D1000), Color(0xFFFFAB91)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val current by prefs.appOAuthSettings.collectAsStateWithLifecycle(AppOAuthSettings())
    val savedThemeId by prefs.selectedThemeId.collectAsStateWithLifecycle("")

    var googleClientId     by remember(current) { mutableStateOf(current.googleClientId) }
    var googleClientSecret by remember(current) { mutableStateOf(current.googleClientSecret) }
    var dropboxKey         by remember(current) { mutableStateOf(current.dropboxAppKey) }
    var dropboxSecret      by remember(current) { mutableStateOf(current.dropboxAppSecret) }
    var onedriveClientId   by remember(current) { mutableStateOf(current.onedriveClientId) }
    var boxClientId        by remember(current) { mutableStateOf(current.boxClientId) }
    var boxClientSecret    by remember(current) { mutableStateOf(current.boxClientSecret) }

    var showGoogleSecret  by remember { mutableStateOf(false) }
    var showDropboxSecret by remember { mutableStateOf(false) }
    var showBoxSecret     by remember { mutableStateOf(false) }
    var saveSuccess       by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Settings", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("OAuth credentials & appearance", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0E0C1C)
                )
            )
        },
        containerColor = Color(0xFF090814)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Theme Section ─────────────────────────────────────────────
            SettingsSection(icon = Icons.Outlined.Palette, title = "Theme") {
                Text("Choose a color palette for the app",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f),
                    modifier = Modifier.padding(bottom = 12.dp))
                // 2-column grid of themes
                val rows = APP_THEMES.chunked(2)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { theme ->
                            ThemeCard(
                                theme = theme,
                                isSelected = savedThemeId == theme.id || (savedThemeId.isEmpty() && theme.id == "midnight_purple"),
                                onClick = {
                                    scope.launch { prefs.saveTheme(theme.id) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ── Google Drive ──────────────────────────────────────────────
            SettingsSection(icon = Icons.Outlined.CloudQueue, title = "Google Drive OAuth") {
                Text("Enter once — used for all Google Drive accounts",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f),
                    modifier = Modifier.padding(bottom = 10.dp))
                SettingsField("Client ID", googleClientId, { googleClientId = it },
                    hint = "xxxx.apps.googleusercontent.com")
                Spacer(Modifier.height(8.dp))
                SettingsField("Client Secret", googleClientSecret, { googleClientSecret = it },
                    isPassword = true, showPass = showGoogleSecret,
                    onTogglePass = { showGoogleSecret = !showGoogleSecret })
            }

            // ── Dropbox ───────────────────────────────────────────────────
            SettingsSection(icon = Icons.Outlined.Cloud, title = "Dropbox OAuth") {
                Text("Enter once — used for all Dropbox accounts",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f),
                    modifier = Modifier.padding(bottom = 10.dp))
                SettingsField("App Key", dropboxKey, { dropboxKey = it })
                Spacer(Modifier.height(8.dp))
                SettingsField("App Secret", dropboxSecret, { dropboxSecret = it },
                    isPassword = true, showPass = showDropboxSecret,
                    onTogglePass = { showDropboxSecret = !showDropboxSecret })
            }

            // ── OneDrive ──────────────────────────────────────────────────
            SettingsSection(icon = Icons.Outlined.Storage, title = "OneDrive / Microsoft") {
                SettingsField("Client ID (Application ID)", onedriveClientId, { onedriveClientId = it })
            }

            // ── Box ───────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Outlined.Inbox, title = "Box OAuth") {
                SettingsField("Client ID", boxClientId, { boxClientId = it })
                Spacer(Modifier.height(8.dp))
                SettingsField("Client Secret", boxClientSecret, { boxClientSecret = it },
                    isPassword = true, showPass = showBoxSecret,
                    onTogglePass = { showBoxSecret = !showBoxSecret })
            }

            // ── Save Button ───────────────────────────────────────────────
            Button(
                onClick = {
                    scope.launch {
                        prefs.saveAppOAuthSettings(AppOAuthSettings(
                            googleClientId     = googleClientId.trim(),
                            googleClientSecret = googleClientSecret.trim(),
                            dropboxAppKey      = dropboxKey.trim(),
                            dropboxAppSecret   = dropboxSecret.trim(),
                            onedriveClientId   = onedriveClientId.trim(),
                            boxClientId        = boxClientId.trim(),
                            boxClientSecret    = boxClientSecret.trim()
                        ))
                        snackbarHostState.showSnackbar("Settings saved")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Theme Card ────────────────────────────────────────────────────────────────

@Composable
private fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val borderMod = if (isSelected)
        Modifier.border(2.dp, theme.primary, RoundedCornerShape(14.dp))
    else
        Modifier.border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp))

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = theme.surface,
        modifier = modifier.then(borderMod)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Color swatches
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.primary))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.accent))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.background))
                }
                if (isSelected) {
                    Icon(Icons.Filled.CheckCircle, null,
                        tint = theme.primary, modifier = Modifier.size(14.dp))
                }
            }
            Text(theme.emoji + " " + theme.name, color = Color.White,
                fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ── Settings Section ──────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color.White.copy(0.04f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = Color(0xFFA29BFE), modifier = Modifier.size(18.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White,
                    style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

// ── Settings Field ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    isPassword: Boolean = false,
    showPass: Boolean = false,
    onTogglePass: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (hint.isNotEmpty()) {
            { Text(hint, fontSize = 11.sp, color = Color.White.copy(0.25f)) }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !showPass) PasswordVisualTransformation()
                               else VisualTransformation.None,
        trailingIcon = if (isPassword && onTogglePass != null) {
            {
                IconButton(onClick = onTogglePass, modifier = Modifier.size(36.dp)) {
                    Icon(if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        null, tint = Color.White.copy(0.4f), modifier = Modifier.size(17.dp))
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White.copy(0.85f),
            focusedLabelColor = Color(0xFFA29BFE),
            unfocusedLabelColor = Color.White.copy(0.45f),
            focusedBorderColor = Color(0xFFA29BFE),
            unfocusedBorderColor = Color.White.copy(0.18f),
            cursorColor = Color(0xFFA29BFE)
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}
