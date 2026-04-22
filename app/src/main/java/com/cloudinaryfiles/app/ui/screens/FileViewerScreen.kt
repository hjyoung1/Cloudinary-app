package com.cloudinaryfiles.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * @param resolvedUrl For video/audio, the fresh stream URL (with current token).
 *                    For everything else can be null — falls back to asset.secureUrl.
 */
@Composable
fun FileViewerScreen(
    asset: CloudinaryAsset,
    resolvedUrl: String?,
    onDismiss: () -> Unit
) {
    // Intercept system back — always close viewer, never exit app
    BackHandler(enabled = true) { onDismiss() }

    val context = LocalContext.current
    val url = resolvedUrl ?: asset.secureUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            asset.isImage  -> ImageViewer(url = url, asset = asset, onDismiss = onDismiss)
            asset.isVideo  -> VideoViewer(url = url, asset = asset, onDismiss = onDismiss)
            asset.isPdf    -> PdfViewer(url = url, asset = asset, onDismiss = onDismiss, context = context)
            isTextFile(asset) -> TextViewer(url = url, asset = asset, onDismiss = onDismiss)
            else -> {
                LaunchedEffect(Unit) { openWithSystem(context, url); onDismiss() }
                Box(Modifier.fillMaxSize().background(SurfaceDark), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                        Spacer(Modifier.height(16.dp))
                        Text("Opening ${asset.format.uppercase()} file…", color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Image viewer ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewer(url: String, asset: CloudinaryAsset, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 6f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                        showControls = false
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            if (scale > 1.5f) { scale = 1f; offset = Offset.Zero }
                            else { scale = 2.5f }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = url,
                contentDescription = asset.displayTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                onState = { imageState = it }
            )
            if (imageState is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(color = Color(0xFF7C4DFF), modifier = Modifier.size(48.dp))
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit  = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.6f))
                    .padding(top = 32.dp, bottom = 8.dp, start = 4.dp, end = 16.dp)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(asset.displayTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("${asset.formattedSize} · ${asset.format.uppercase()}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                }
            }
        }

        // Bottom hint
        AnimatedVisibility(
            visible = showControls && scale == 1f,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(color = Color.Black.copy(0.5f), shape = RoundedCornerShape(20.dp)) {
                Text(
                    "Pinch to zoom · Double-tap to expand",
                    color = Color.White.copy(0.7f), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ── Video viewer ──────────────────────────────────────────────────────────────

@Composable
private fun VideoViewer(url: String, asset: CloudinaryAsset, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Build player — key on url so it rebuilds if URL changes
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        onDispose { exoPlayer.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    controllerShowTimeoutMs = 3000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Back button — top-left, always visible
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.55f))
        ) {
            IconButton(onClick = { exoPlayer.pause(); onDismiss() }) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }
    }
}

// ── PDF viewer ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewer(url: String, asset: CloudinaryAsset, onDismiss: () -> Unit, context: Context) {
    val viewerUrl = "https://docs.google.com/viewer?url=${Uri.encode(url)}&embedded=true"
    Column(Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                    Text("PDF · ${asset.formattedSize}", fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) } },
            actions = {
                IconButton(onClick = { openUrlInBrowser(context, url) }) {
                    Icon(Icons.Outlined.OpenInBrowser, "Open in browser", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    loadUrl(viewerUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Text viewer ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextViewer(url: String, asset: CloudinaryAsset, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        try {
            text = withContext(Dispatchers.IO) { URL(url).readText(Charsets.UTF_8) }
        } catch (e: Exception) { error = "Could not load: ${e.message}" }
        finally { isLoading = false }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0C1C))) {
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                    Text("${asset.format.uppercase()} · ${asset.formattedSize}", fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF7C4DFF))
                    Spacer(Modifier.height(12.dp))
                    Text("Loading…", color = Color.White.copy(0.6f))
                }
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
            else -> Text(
                text = text ?: "",
                color = Color(0xFFE0E0E0),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isTextFile(asset: CloudinaryAsset): Boolean =
    asset.format.lowercase() in setOf(
        "txt", "md", "json", "xml", "csv", "html", "htm", "js", "ts", "kt",
        "java", "py", "rb", "go", "rs", "css", "sh", "log", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "srt", "vtt", "ass", "sub"
    )

private fun openWithSystem(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) { openUrlInBrowser(context, url) }
}

private fun openUrlInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {}
}
