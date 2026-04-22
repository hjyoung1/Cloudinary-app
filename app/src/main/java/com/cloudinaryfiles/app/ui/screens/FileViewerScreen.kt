package com.cloudinaryfiles.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.ui.components.formatDurationSec
import com.cloudinaryfiles.app.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    asset: CloudinaryAsset,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Choose viewer based on file type
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            asset.isImage -> ImageViewer(asset = asset, onDismiss = onDismiss)
            asset.isVideo -> VideoViewer(asset = asset, onDismiss = onDismiss, context = context)
            asset.isPdf   -> PdfViewer(asset = asset, onDismiss = onDismiss, context = context)
            isTextFile(asset) -> TextViewer(asset = asset, onDismiss = onDismiss)
            else -> {
                // For all other types, open with system intent
                LaunchedEffect(Unit) {
                    openWithSystem(context, asset)
                    onDismiss()
                }
                // Show brief loading state while the system handles it
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

// ── Image Viewer ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewer(asset: CloudinaryAsset, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Zoomable image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                        showControls = false
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            scale = if (scale > 1.5f) 1f else 2.5f
                            if (scale == 1f) offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = asset.secureUrl,
                contentDescription = asset.displayTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                onState = { imageState = it }
            )

            // Loading indicator
            if (imageState is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Top controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 32.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        asset.displayTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        "${asset.formattedSize} · ${asset.format.uppercase()}",
                        color = Color.White.copy(0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Bottom hint
        AnimatedVisibility(
            visible = showControls && scale == 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(
                color = Color.Black.copy(0.5f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "Pinch to zoom · Double-tap to expand",
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ── Video Viewer ──────────────────────────────────────────────────────────────

@Composable
private fun VideoViewer(asset: CloudinaryAsset, onDismiss: () -> Unit, context: Context) {
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(asset.secureUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    controllerShowTimeoutMs = 3000
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Back button overlay (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.5f))
        ) {
            IconButton(onClick = {
                exoPlayer.pause()
                onDismiss()
            }) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }

        // Title overlay at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        ) {
            Text(
                asset.displayTitle,
                color = Color.White.copy(0.85f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

// ── PDF Viewer ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewer(asset: CloudinaryAsset, onDismiss: () -> Unit, context: Context) {
    var useWebView by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text("PDF · ${asset.formattedSize}", fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            actions = {
                // Open in external browser
                IconButton(onClick = { openUrlInBrowser(context, asset.secureUrl) }) {
                    Icon(Icons.Outlined.OpenInBrowser, "Open in browser", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )

        // WebView with Google Docs viewer for PDFs
        val viewerUrl = "https://docs.google.com/viewer?url=${Uri.encode(asset.secureUrl)}&embedded=true"
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
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

// ── Text Viewer ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextViewer(asset: CloudinaryAsset, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(asset.secureUrl) {
        try {
            val content = withContext(Dispatchers.IO) {
                URL(asset.secureUrl).readText(Charsets.UTF_8)
            }
            text = content
        } catch (e: Exception) {
            error = "Could not load file: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0C1C))) {
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text("${asset.format.uppercase()} · ${asset.formattedSize}", fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading…", color = Color.White.copy(0.6f))
                    }
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            text != null -> {
                SelectionContainer {
                    Text(
                        text = text!!,
                        color = Color(0xFFE0E0E0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isTextFile(asset: CloudinaryAsset): Boolean =
    asset.format.lowercase() in setOf("txt", "md", "json", "xml", "csv", "html", "htm",
        "js", "ts", "kt", "java", "py", "rb", "go", "rs", "css", "sh", "log", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "srt", "vtt", "ass", "sub")

private fun openWithSystem(context: Context, asset: CloudinaryAsset) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(asset.secureUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fall back to browser
        openUrlInBrowser(context, asset.secureUrl)
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) } catch (_: Exception) {}
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    // Wrap in a basic selection-enabled container
    content()
}
