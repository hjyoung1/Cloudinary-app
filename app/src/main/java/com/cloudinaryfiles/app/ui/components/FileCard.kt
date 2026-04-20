package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCard(
    asset: CloudinaryAsset,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onCopyLink: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val durationText = remember(asset.assetId) {
        if (asset.isAudio && asset.duration != null && asset.duration > 0) {
            val total = asset.duration.toInt()
            val m = total / 60; val s = total % 60
            if (total >= 3600) "%d:%02d:%02d".format(total / 3600, m % 60, s)
            else "%d:%02d".format(m, s)
        } else null
    }

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaying) 12.dp else 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // ── Thumbnail area ──────────────────────────────────────────────
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
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(assetGradient(asset)))
                    if (asset.isAudio) WaveformDecoration(isPlaying = isPlaying)
                }

                // Format badge (top-left)
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = asset.format.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration badge (top-right) for audio
                if (durationText != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Timer, null,
                                tint = AudioAccent,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(3.dp))
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

                // Play button
                if (asset.isAudio || asset.isVideo) {
                    Box(
                        modifier = Modifier.align(Alignment.Center).scale(scale)
                    ) {
                        IconButton(
                            onClick = onPlayClick,
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
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) Color.White else Color(0xFF1A1050),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // ── Content ────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = asset.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = asset.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatDate(asset.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCopyLink,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = "Copy link", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("COPY", style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                    }
                    IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformDecoration(isPlaying: Boolean) {
    val anim = rememberInfiniteTransition(label = "wave")
    val phase by anim.animateFloat(
        initialValue = 0f, targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying)
            infiniteRepeatable(tween(1200, easing = LinearEasing))
        else
            snap(),
        label = "phase"
    )
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = listOf(0.4f, 0.7f, 0.55f, 0.9f, 0.65f, 0.8f, 0.5f, 0.75f, 0.45f, 0.85f, 0.6f)
        bars.forEachIndexed { i, baseH ->
            val animatedH = if (isPlaying) {
                val offset = (phase + i * 0.1f) % 1f
                baseH * (0.5f + 0.5f * kotlin.math.sin(offset * 2 * Math.PI.toFloat()))
            } else baseH * 0.3f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(animatedH)
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
    else          -> Brush.linearGradient(listOf(ImageGradientStart, ImageGradientEnd))
}

private fun formatDate(raw: String): String = try {
    OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { raw.take(10) }
