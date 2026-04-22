package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    // Pulse animation when audio is playing
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

    // Only show border when selected — no other selection visual
    val borderMod = if (isSelected)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    else Modifier

    ElevatedCard(
        modifier = modifier.then(borderMod),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaying) 10.dp else if (isSelected) 6.dp else 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                isPlaying  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                else       -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
        ) {
            Column {
                // ── Thumbnail / preview — taller for more visual impact ──────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    // Background / thumbnail
                    if (asset.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = asset.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Scrim so badges are readable
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    } else {
                        Box(Modifier.fillMaxSize().background(assetGradient(asset)))
                        when {
                            asset.isAudio -> WaveformDecoration(isPlaying = isPlaying)
                            !asset.isVideo && !asset.isImage -> {
                                // Generic file icon
                                Icon(
                                    imageVector = when {
                                        asset.isPdf   -> Icons.Outlined.PictureAsPdf
                                        else          -> Icons.Outlined.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier.align(Alignment.Center).size(52.dp)
                                )
                            }
                        }
                    }

                    // Format badge — top-left
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

                    // Duration badge — top-right
                    if (durationText != null) {
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
                                Icon(
                                    Icons.Outlined.Timer, null,
                                    tint = if (asset.isAudio) AudioAccent else Color(0xFF81D4FA),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Play/pause button — audio AND video
                    if (asset.isAudio || asset.isVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer { scaleX = if (asset.isAudio) scale else 1f; scaleY = if (asset.isAudio) scale else 1f }
                        ) {
                            IconButton(
                                onClick = if (asset.isVideo) onOpen else onPlayClick,
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

                    // Selection indicator — small dot in top-left when in selection mode
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
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // ── Compact info row ──────────────────────────────────────────
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
                    // Link copy — only action shown in the card footer
                    IconButton(onClick = onCopyLink, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Link, "Copy link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Support composables ───────────────────────────────────────────────────────

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

private fun assetGradient(asset: CloudinaryAsset): Brush = when {
    asset.isAudio -> Brush.linearGradient(
        listOf(AudioGradientStart, AudioGradientMid, AudioGradientEnd),
        start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    asset.isVideo -> Brush.linearGradient(listOf(VideoGradientStart, VideoGradientEnd))
    asset.isPdf   -> Brush.linearGradient(listOf(Color(0xFF1A0A0A), Color(0xFF3D0000)))
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
