package com.cloudinaryfiles.app.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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

val CardShape = RoundedCornerShape(18.dp)
val PlayBtnShape = RoundedCornerShape(12.dp)  // squarish with slight rounding

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileCard(
    asset: CloudinaryAsset,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isInlineVideoActive: Boolean = false,
    inlineVideoUrl: String? = null,
    inlineVideoPlayer: ExoPlayer? = null,       // shared player passed in to preserve seek pos
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
    authHeaders: Map<String, String>? = null,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val durationText = remember(asset.assetId, asset.duration) {
        val d = asset.duration
        if ((asset.isAudio || asset.isVideo) && d != null && d > 0.0) formatDurationSec(d) else null
    }

    var showInlineControls by remember(isInlineVideoActive) { mutableStateOf(false) }

    val selectedBorder = if (isSelected)
        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CardShape) else Modifier

    ElevatedCard(
        modifier = modifier.then(selectedBorder),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaying) 10.dp else if (isSelected) 6.dp else 3.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isSelected          -> MaterialTheme.colorScheme.primaryContainer.copy(0.12f)
                isInlineVideoActive -> Color(0xFF0D0030)
                isPlaying           -> MaterialTheme.colorScheme.primaryContainer.copy(0.18f)
                else                -> Color(0xFF1A1830)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onSelectToggle()
                        else onClick()
                    },
                    onLongClick = onLongPress
                )
        ) {
            Column {
                // ── Thumbnail area (fixed height — no expansion) ──────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                ) {
                    if (isInlineVideoActive && inlineVideoPlayer != null) {
                        // ── Inline ExoPlayer (uses passed-in player to preserve seek pos) ──
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = inlineVideoPlayer
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    useController = false
                                }
                            },
                            update = { it.player = inlineVideoPlayer },
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onTap = { showInlineControls = !showInlineControls })
                            }
                        )
                        // Dim overlay + controls on tap
                        if (showInlineControls) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                InlineCtrlBtn(
                                    icon = Icons.Filled.StopCircle, label = "Stop",
                                    tint = Color(0xFFFF6B6B),
                                    onClick = { onInlineVideoStop(); showInlineControls = false }
                                )
                                InlineCtrlBtn(
                                    icon = Icons.Filled.Fullscreen, label = "Full",
                                    tint = Color.White,
                                    onClick = onInlineVideoFullScreen
                                )
                            }
                        }
                    } else {
                        // ── Static thumbnail ──────────────────────────────────
                        ThumbnailContent(asset = asset, isPlaying = isPlaying, scale = scale, authHeaders = authHeaders)
                    }

                    // ── Selection overlay (large centered checkmark, dims card) ──
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isSelected) Color(0xFF6C3CE1).copy(0.45f)
                                    else Color.Black.copy(0.30f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, null,
                                            tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color.White.copy(0.15f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.size(44.dp)
                                ) {}
                            }
                        }
                    }

                    // Duration badge top-right (skip inline + selection mode)
                    if (durationText != null && !isInlineVideoActive && !isSelectionMode) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                            color = Color.Black.copy(0.60f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Outlined.Timer, null,
                                    tint = if (asset.isAudio) AudioAccent else Color(0xFF81D4FA),
                                    modifier = Modifier.size(9.dp))
                                Text(durationText, color = Color.White,
                                    fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Play button (not in selection mode, not inline playing)
                    if (!isInlineVideoActive && !isSelectionMode && (asset.isAudio || asset.isVideo)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = if (asset.isAudio) scale else 1f
                                    scaleY = if (asset.isAudio) scale else 1f
                                }
                        ) {
                            // Squarish play button
                            Surface(
                                onClick = onPlayClick,
                                shape = PlayBtnShape,
                                color = if (isPlaying)
                                    Color(0xFF6C3CE1).copy(0.92f)
                                else
                                    Color.White.copy(0.88f),
                                modifier = Modifier.size(46.dp),
                                shadowElevation = 4.dp
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying && asset.isAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (isPlaying) Color.White else Color(0xFF1A1050),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Info row ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Format pill on the left
                    Surface(
                        color = formatBadgeColor(asset).copy(0.18f),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = asset.format.uppercase().take(4),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = formatBadgeColor(asset),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.displayTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${asset.formattedSize}  ·  ${formatDate(asset.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCopyLink, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Link, "Copy link",
                            tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                            modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── Thumbnail content ─────────────────────────────────────────────────────────

@Composable
private fun ThumbnailContent(
    asset: CloudinaryAsset,
    isPlaying: Boolean,
    scale: Float,
    authHeaders: Map<String, String>? = null
) {
    // Videos: never show thumbnail, always show gradient (avoid spoilers + perf)
    // Images: show actual image filling the card
    val showImage = !asset.isVideo && asset.resolvedThumbnailUrl.isNotEmpty()
    val isCloudinary = asset.secureUrl.contains("cloudinary.com")
    val imageUrl = when {
        asset.resolvedThumbnailUrl.isNotEmpty() -> asset.resolvedThumbnailUrl
        isCloudinary && asset.isImage           -> asset.secureUrl
        else                                    -> ""
    }

    // Gradient background always rendered (shows under loading/error states)
    Box(Modifier.fillMaxSize().background(assetGradient(asset)))

    if (showImage && imageUrl.isNotEmpty()) {
        var imageState by remember(imageUrl) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val imgReq = remember(imageUrl, authHeaders) {
            coil.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .apply { authHeaders?.forEach { (k, v) -> addHeader(k, v) } }
                .build()
        }
        AsyncImage(
            model = imgReq,
            contentDescription = null,
            contentScale = ContentScale.Crop,   // fills whole card — no black bars
            modifier = Modifier.fillMaxSize(),
            onState = { imageState = it }
        )
        if (imageState is AsyncImagePainter.State.Loading) {
            Box(Modifier.fillMaxSize().background(assetGradient(asset)))
        }
        // Light scrim so badges remain readable
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.15f)))
    } else {
        // Decorative content for non-image types
        when {
            asset.isAudio -> WaveformDecoration(isPlaying)
            asset.isVideo -> VideoDecoration()
            asset.isPdf   -> PdfDecoration(asset)
            isTextLike(asset) -> TextDecoration(asset)
            else          -> GenericDecoration(asset)
        }
    }
}

// ── Decorations ───────────────────────────────────────────────────────────────

@Composable
private fun VideoDecoration() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(0.18f),
            modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun PdfDecoration(asset: CloudinaryAsset) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(color = Color(0xFFD32F2F).copy(0.85f), shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
        Text(asset.displayTitle, color = Color.White.copy(0.5f), fontSize = 8.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TextDecoration(asset: CloudinaryAsset) {
    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Code, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(14.dp))
            Text(asset.format.uppercase(), color = Color(0xFF80CBC4),
                fontWeight = FontWeight.Bold, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(0.9f, 0.72f, 0.83f, 0.55f, 0.78f).forEach { frac ->
                Box(Modifier.fillMaxWidth(frac).height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.15f)))
            }
        }
    }
}

@Composable
private fun GenericDecoration(asset: CloudinaryAsset) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.InsertDriveFile, null, tint = Color.White.copy(0.18f),
                modifier = Modifier.size(40.dp))
            Text(asset.format.uppercase().take(6), color = Color.White.copy(0.30f),
                fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun WaveformDecoration(isPlaying: Boolean) {
    val anim = rememberInfiniteTransition(label = "wave")
    val phase by anim.animateFloat(0f, if (isPlaying) 1f else 0f,
        infiniteRepeatable(animation = tween(1200, easing = LinearEasing)), "phase")
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        listOf(0.4f,0.72f,0.55f,0.88f,0.63f,0.78f,0.48f,0.70f,0.45f,0.82f,0.58f).forEachIndexed { i, b ->
            val h = if (isPlaying) b * (0.5f + 0.5f * kotlin.math.sin(((phase + i*0.1f)%1f)*2*Math.PI.toFloat())) else b*0.25f
            Box(Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp))
                .background(AudioAccent.copy(0.55f)))
        }
    }
}

// ── Inline video control buttons ──────────────────────────────────────────────

@Composable
private fun InlineCtrlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = onClick, color = Color.Black.copy(0.6f), shape = PlayBtnShape,
            modifier = Modifier.size(52.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Text(label, color = Color.White.copy(0.85f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isTextLike(asset: CloudinaryAsset) = asset.format.lowercase() in setOf(
    "txt","md","json","xml","csv","html","htm","js","ts","kt","java","py","rb","go",
    "rs","css","sh","log","yaml","yml","toml","ini","cfg","conf","srt","vtt","ass","sub"
)

private fun formatBadgeColor(asset: CloudinaryAsset): Color = when {
    asset.isAudio -> AudioAccent
    asset.isVideo -> Color(0xFF64B5F6)
    asset.isImage -> Color(0xFF81C784)
    asset.isPdf   -> Color(0xFFEF9A9A)
    isTextLike(asset) -> Color(0xFF80CBC4)
    else -> Color(0xFFB0BEC5)
}

private fun assetGradient(asset: CloudinaryAsset): Brush = when {
    asset.isAudio -> Brush.linearGradient(listOf(AudioGradientStart, AudioGradientMid, AudioGradientEnd),
        Offset(0f,0f), Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
    asset.isVideo -> Brush.linearGradient(listOf(Color(0xFF0D0030), Color(0xFF1A0050), Color(0xFF0A001A)))
    asset.isPdf   -> Brush.linearGradient(listOf(Color(0xFF1A0505), Color(0xFF2D0000)))
    asset.isImage -> Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF0D2137)))
    isTextLike(asset) -> Brush.linearGradient(listOf(Color(0xFF0A1A18), Color(0xFF0D2420)))
    else -> Brush.linearGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838)))
}

fun formatDurationSec(seconds: Double): String {
    val total = seconds.toInt()
    val h = total/3600; val m = (total%3600)/60; val s = total%60
    return if (h > 0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)
}

private fun formatDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { raw.take(10) }
