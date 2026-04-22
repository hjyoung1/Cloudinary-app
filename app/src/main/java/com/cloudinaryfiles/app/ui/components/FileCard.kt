package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.*
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
    onSelectToggle: () -> Unit = {},        // tapping the checkbox corner
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pulse animation for playing state
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    // Checkbox animation
    val checkboxScale by animateFloatAsState(
        targetValue = if (isSelectionMode) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkboxScale"
    )

    val durationText = remember(asset.publicId, asset.duration) {
        val d = asset.duration
        if ((asset.isAudio || asset.isVideo) && d != null && d > 0.0) formatDurationSec(d) else null
    }

    // Card border when selected
    val cardBorder = if (isSelected)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    else Modifier

    ElevatedCard(
        modifier = modifier.then(cardBorder),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaying) 12.dp else if (isSelected) 6.dp else 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                isPlaying  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
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
                // ── Thumbnail / Preview area ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    if (asset.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = asset.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    } else {
                        Box(Modifier.fillMaxSize().background(assetGradient(asset)))
                        if (asset.isAudio) WaveformDecoration(isPlaying = isPlaying)
                        // Icon for non-media non-image files
                        if (!asset.isAudio && !asset.isVideo && !asset.isImage) {
                            Icon(
                                imageVector = fileTypeIcon(asset),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.align(Alignment.Center).size(48.dp)
                            )
                        }
                    }

                    // Format badge — top-left (moved right if checkbox visible)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = if (isSelectionMode) 36.dp else 8.dp, top = 8.dp),
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
                            color = Color.Black.copy(alpha = 0.6f),
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

                    // Play button (audio/video)
                    if (asset.isAudio || asset.isVideo) {
                        Box(
                            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                                scaleX = scale; scaleY = scale
                            }
                        ) {
                            IconButton(
                                onClick = if (asset.isVideo) onOpen else onPlayClick,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isPlaying)
                                            Brush.radialGradient(listOf(AudioAccent, AudioAccent2))
                                        else
                                            Brush.radialGradient(listOf(Color.White.copy(0.9f), Color.White.copy(0.7f))),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && asset.isAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = if (isPlaying) Color.White else Color(0xFF1A1050),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // ── Checkbox (M3 selection) ──────────────────────────────
                    // Always rendered but animated in/out; tap it to toggle selection
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(26.dp)
                            .graphicsLayer { scaleX = checkboxScale; scaleY = checkboxScale }
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.45f)
                            )
                            .combinedClickable(onClick = onSelectToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            // Empty circle hint
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White.copy(0.7f), CircleShape)
                            )
                        }
                    }
                }

                // ── Bottom info area (M3 redesign) ────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left: title + meta
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.displayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = asset.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatDate(asset.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Open button for viewable non-audio files
                        if (!asset.isAudio) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                onClick = onOpen,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        openIcon(asset), null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        openLabel(asset),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Right: action icons stacked vertically
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(onClick = onCopyLink, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.Link, "Copy link",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.Share, "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(onClick = onInfo, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.Info, "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Selected overlay (subtle tint — border already shows selection)
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun fileTypeIcon(asset: CloudinaryAsset) = when {
    asset.isPdf     -> Icons.Outlined.PictureAsPdf
    asset.isImage   -> Icons.Outlined.Image
    else            -> Icons.Outlined.InsertDriveFile
}

private fun openIcon(asset: CloudinaryAsset) = when {
    asset.isVideo   -> Icons.Filled.Fullscreen
    asset.isImage   -> Icons.Outlined.ZoomOutMap
    asset.isPdf     -> Icons.Outlined.PictureAsPdf
    else            -> Icons.Outlined.OpenInNew
}

private fun openLabel(asset: CloudinaryAsset) = when {
    asset.isVideo   -> "PLAY"
    asset.isImage   -> "VIEW"
    asset.isPdf     -> "PDF"
    else            -> "OPEN"
}

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
