package com.cloudinaryfiles.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File
import java.net.URL

/**
 * @param resolvedUrl For video/audio, the fresh stream URL (with current token).
 *                    For everything else can be null — falls back to asset.secureUrl.
 */
@Composable
fun FileViewerScreen(
    asset: CloudinaryAsset,
    resolvedUrl: String?,
    resolvedHeaders: Map<String, String>? = null,
    startPositionMs: Long = 0L,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }

    val context = LocalContext.current
    val url = resolvedUrl ?: asset.secureUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            asset.isImage     -> ImageViewer(url = url, asset = asset, headers = resolvedHeaders, onDismiss = onDismiss)
            asset.isVideo     -> VideoViewer(url = url, asset = asset, headers = resolvedHeaders, startPositionMs = startPositionMs, onDismiss = onDismiss)
            asset.isPdf       -> PdfViewer(url = url, asset = asset, onDismiss = onDismiss, context = context)
            isTextFile(asset) -> TextViewer(url = url, asset = asset, headers = resolvedHeaders, onDismiss = onDismiss)
            asset.format.lowercase() in setOf("zip","rar","7z","tar","gz","bz2","xz") -> {
                // Archive: cannot preview, offer to open externally
                ArchivePreview(asset = asset, url = url, onDismiss = onDismiss, context = context)
            }
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

@Composable
private fun ImageViewer(url: String, asset: CloudinaryAsset, headers: Map<String, String>? = null, onDismiss: () -> Unit) {
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
            val context = androidx.compose.ui.platform.LocalContext.current
            val imgRequest = remember(url, headers) {
                coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .apply { headers?.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
            }
            AsyncImage(
                model = imgRequest,
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

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit  = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                Modifier.fillMaxWidth().background(Color.Black.copy(0.6f))
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
private fun VideoViewer(url: String, asset: CloudinaryAsset, headers: Map<String, String>? = null, startPositionMs: Long = 0L, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(url, headers) {
        val dsFactory = if (headers != null) {
            val okClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .build()
                    chain.proceed(req)
                }
                .build()
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okClient)
        } else {
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
        }
        val src = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(url))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(src)
            prepare()
            if (startPositionMs > 0L) seekTo(startPositionMs)
            playWhenReady = true
        }
    }
    DisposableEffect(url, headers, startPositionMs) { onDispose { exoPlayer.release() } }

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

// ── PDF viewer (native PdfRenderer — no network dependency) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewer(url: String, asset: CloudinaryAsset, onDismiss: () -> Unit, context: Context) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(url) {
        isLoading = true; error = null; pages = emptyList()
        try {
            val tempFile = File(context.cacheDir, "cv_pdf_${asset.assetId.take(16)}.pdf")
            withContext(Dispatchers.IO) {
                // Download with progress
                val conn = java.net.URL(url).openConnection().apply { connect() }
                val totalBytes = conn.contentLength.toLong().coerceAtLeast(1L)
                conn.getInputStream().use { ins ->
                    tempFile.outputStream().use { out ->
                        val buf = ByteArray(8192); var read: Int; var downloaded = 0L
                        while (ins.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read); downloaded += read
                            downloadProgress = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        }
                    }
                }
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                val screenWidth = context.resources.displayMetrics.widthPixels
                val newPages = mutableListOf<Bitmap>()
                for (i in 0 until minOf(renderer.pageCount, 50)) {  // limit to 50 pages
                    val page = renderer.openPage(i)
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val bmpW = screenWidth.coerceAtMost(1080)
                    val bmpH = (bmpW * ratio).toInt()
                    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    newPages.add(bmp)
                    // Emit progressively so first page shows ASAP
                    if (i == 0) withContext(Dispatchers.Main) { pages = newPages.toList() }
                }
                renderer.close(); pfd.close()
                withContext(Dispatchers.Main) { pages = newPages }
            }
        } catch (e: Exception) {
            error = "Could not load PDF: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                    Text("PDF · ${asset.formattedSize}${if (pageCount > 0) " · $pageCount pages" else ""}",
                        fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            },
            actions = {
                IconButton(onClick = { openUrlInBrowser(context, url) }) {
                    Icon(Icons.Outlined.OpenInBrowser, "Open in browser", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )

        when {
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openUrlInBrowser(context, url) }) {
                            Icon(Icons.Outlined.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open in Browser")
                        }
                    }
                }
            }
            pages.isEmpty() && isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            color = Color(0xFF7C4DFF),
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (downloadProgress < 0.99f)
                                "Downloading… ${"%.0f".format(downloadProgress * 100)}%"
                            else "Rendering pages…",
                            color = Color.White.copy(0.7f)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { index, bitmap ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        // Page number label
                        Text(
                            "Page ${index + 1}${if (pageCount > 0) " / $pageCount" else ""}",
                            color = Color.White.copy(0.3f),
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    // Loading more pages indicator
                    if (isLoading && pages.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF7C4DFF), modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Text viewer ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextViewer(url: String, asset: CloudinaryAsset, headers: Map<String, String>? = null, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        try {
            text = withContext(Dispatchers.IO) {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                    conn.connect()
                    conn.inputStream.reader(Charsets.UTF_8).readText()
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivePreview(asset: CloudinaryAsset, url: String, onDismiss: () -> Unit, context: Context) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0C1C))) {
        TopAppBar(
            title = {
                Column {
                    Text(asset.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                    Text("${asset.format.uppercase()} Archive · ${asset.formattedSize}", fontSize = 10.sp, color = Color.White.copy(0.6f))
                }
            },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0C1C))
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    color = Color.White.copy(0.06f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.FolderZip, null, tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(44.dp))
                    }
                }
                Text("Cannot preview archives", color = Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    """${asset.format.uppercase()} archive files can't be previewed in-app.
Open with an external app to browse the contents.""",
                    color = Color.White.copy(0.6f), fontSize = 13.sp,
                    textAlign = TextAlign.Center, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openWithSystem(context, url) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open with…")
                }
            }
        }
    }
}
