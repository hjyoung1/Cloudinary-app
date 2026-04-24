package com.cloudinaryfiles.app.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.ui.theme.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileCard(
    asset: CloudinaryAsset,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    // Inline video state
    isInlineVideoActive: Boolean = false,
    inlineVideoUrl: String? = null,
    onInlineVideoStop: () -> Unit = {},
    onInlineVideoFullScreen: () -> Unit = {},
    onPlayClick: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onOpen: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.10f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val durationText = remember(asset.assetId, asset.duration) {
        val d = asset.duration
        if ((asset.isAudio || asset.isVideo) && d != null && d > 0.0) formatDurationSec(d) else null
    }

    var showInlineControls by remember(isInlineVideoActive) { mutableStateOf(isInlineVideoActive) }

    val borderMod = if (isSelected)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    else Modifier

    // Card height expands when inline video is active
    val thumbnailHeight = if (isInlineVideoActive) 220.dp else 148.dp

    ElevatedCard(
        modifier = modifier.then(borderMod),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaying) 10.dp else if (isSelected) 6.dp else 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isSelected     -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                isInlineVideoActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                isPlaying      -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                else           -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
        ) {
            Column {
                // ── Thumbnail / preview ─────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(thumbnailHeight)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    if (isInlineVideoActive && inlineVideoUrl != null) {
                        // ── Inline video player ──────────────────────────────
                        InlineVideoPlayer(
                            url = inlineVideoUrl,
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onTap = { showInlineControls = !showInlineControls })
                            }
                        )
                        // Dim overlay when controls visible
                        if (showInlineControls) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)))
                            // Stop and Fullscreen buttons
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                InlineVideoControlBtn(
                                    icon = Icons.Filled.StopCircle,
                                    label = "Stop",
                                    tint = Color(0xFFFF6B6B),
                                    onClick = { onInlineVideoStop(); showInlineControls = false }
                                )
                                InlineVideoControlBtn(
                                    icon = Icons.Filled.Fullscreen,
                                    label = "Fullscreen",
                                    tint = Color.White,
                                    onClick = onInlineVideoFullScreen
                                )
                            }
                        }
                    } else {
                        // ── Static thumbnail ──────────────────────────────────
                        ThumbnailContent(asset = asset, isPlaying = isPlaying, scale = scale)
                    }

                    // Format badge — top-left (skip when inline video showing)
                    if (!isInlineVideoActive) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = asset.format.uppercase().take(4),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Duration badge — top-right
                    if (durationText != null && !isInlineVideoActive) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            color = Color.Black.copy(alpha = 0.60f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Outlined.Timer, null,
                                    tint = if (asset.isAudio) AudioAccent else Color(0xFF81D4FA),
                                    modifier = Modifier.size(10.dp))
                                Text(durationText, style = MaterialTheme.typography.labelSmall,
                                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Play/pause button — only if not inline video active
                    if (!isInlineVideoActive && (asset.isAudio || asset.isVideo)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = if (asset.isAudio) scale else 1f
                                    scaleY = if (asset.isAudio) scale else 1f
                                }
                        ) {
                            IconButton(
                                onClick = if (asset.isVideo) onPlayClick else onPlayClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isPlaying)
                                            Brush.radialGradient(listOf(AudioAccent, AudioAccent2))
                                        else
                                            Brush.radialGradient(listOf(Color.White.copy(0.92f), Color.White.copy(0.75f))),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && asset.isAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = if (isPlaying) Color.White else Color(0xFF1A1050),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    // Selection indicator
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                                .size(20.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.85f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Icon(Icons.Filled.Check, null, tint = Color.White,
                                modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // ── Info row ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.displayTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${asset.formattedSize}  ·  ${formatDate(asset.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCopyLink, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Link, "Copy link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ── Thumbnail content (static) ────────────────────────────────────────────────

@Composable
private fun ThumbnailContent(asset: CloudinaryAsset, isPlaying: Boolean, scale: Float) {
    // Best thumbnail URL: use cloudinary thumbnail first, then secureUrl for images
    val displayThumbUrl = when {
        asset.thumbnailUrl.isNotEmpty() -> asset.thumbnailUrl
        asset.isImage                   -> asset.secureUrl   // Show actual image for non-Cloudinary
        else                            -> ""
    }

    if (displayThumbUrl.isNotEmpty()) {
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        AsyncImage(
            model = displayThumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = { imageState = it }
        )
        if (imageState is AsyncImagePainter.State.Loading) {
            Box(Modifier.fillMaxSize().background(assetGradient(asset)))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(0.5f), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        // Scrim so badges are readable
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
    } else {
        // Gradient background + decorative content
        Box(Modifier.fillMaxSize().background(assetGradient(asset)))
        when {
            asset.isAudio -> WaveformDecoration(isPlaying = isPlaying)
            asset.isVideo -> VideoThumbnailDecoration()
            asset.isPdf   -> PdfThumbnailDecoration(asset)
            isTextLike(asset) -> TextFileThumbnailDecoration(asset)
            else -> GenericFileThumbnailDecoration(asset)
        }
    }
}

// ── Decorations for non-image files ──────────────────────────────────────────

@Composable
private fun VideoThumbnailDecoration() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.VideoFile, null, tint = Color.White.copy(0.25f),
            modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun PdfThumbnailDecoration(asset: CloudinaryAsset) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: PDF icon + format badge styled
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0xFFE53935).copy(0.85f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        // Bottom: file name hint
        Text(
            asset.displayTitle,
            color = Color.White.copy(0.6f),
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TextFileThumbnailDecoration(asset: CloudinaryAsset) {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Article, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(16.dp))
            Text(asset.format.uppercase(), color = Color(0xFF80CBC4),
                fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        // Fake "lines of text" decorative blocks
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                val frac = when (i) { 0 -> 0.9f; 1 -> 0.75f; 2 -> 0.85f; else -> 0.55f }
                Box(
                    Modifier.fillMaxWidth(frac).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(0.18f))
                )
            }
        }
    }
}

@Composable
private fun GenericFileThumbnailDecoration(asset: CloudinaryAsset) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.InsertDriveFile, null, tint = Color.White.copy(0.22f),
                modifier = Modifier.size(44.dp))
            Text(asset.format.uppercase().take(6), color = Color.White.copy(0.35f),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── Inline video controls ─────────────────────────────────────────────────────

@Composable
private fun InlineVideoControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.Black.copy(0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Inline ExoPlayer ──────────────────────────────────────────────────────────

@Composable
private fun InlineVideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false   // we have custom overlay controls
            }
        },
        update = { it.player = exoPlayer },
        modifier = modifier
    )
}

// ── Audio waveform ────────────────────────────────────────────────────────────

@Composable
private fun WaveformDecoration(isPlaying: Boolean) {
    val anim = rememberInfiniteTransition(label = "wave")
    val phase by anim.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "phase"
    )
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = listOf(0.4f, 0.7f, 0.55f, 0.9f, 0.65f, 0.8f, 0.5f, 0.75f, 0.45f, 0.85f, 0.6f)
        bars.forEachIndexed { i, baseH ->
            val h = if (isPlaying) {
                val offset = (phase + i * 0.1f) % 1f
                baseH * (0.5f + 0.5f * kotlin.math.sin(offset * 2 * Math.PI.toFloat()))
            } else baseH * 0.3f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AudioAccent.copy(alpha = 0.5f))
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isTextLike(asset: CloudinaryAsset): Boolean =
    asset.format.lowercase() in setOf("txt","md","json","xml","csv","html","htm","js","ts","kt",
        "java","py","rb","go","rs","css","sh","log","yaml","yml","toml","ini","cfg","conf",
        "srt","vtt","ass","sub")

private fun assetGradient(asset: CloudinaryAsset): Brush = when {
    asset.isAudio -> Brush.linearGradient(
        listOf(AudioGradientStart, AudioGradientMid, AudioGradientEnd),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    asset.isVideo -> Brush.linearGradient(listOf(VideoGradientStart, VideoGradientEnd))
    asset.isPdf   -> Brush.linearGradient(listOf(Color(0xFF1A0505), Color(0xFF3D0000)))
    asset.isImage -> Brush.linearGradient(listOf(ImageGradientStart, ImageGradientEnd))
    else          -> Brush.linearGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B3A4B)))
}

fun formatDurationSec(seconds: Double): String {
    val total = seconds.toInt()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { raw.take(10) }
