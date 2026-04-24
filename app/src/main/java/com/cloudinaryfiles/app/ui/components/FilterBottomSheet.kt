package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudinaryfiles.app.data.model.*
import com.cloudinaryfiles.app.ui.theme.AudioAccent
import com.cloudinaryfiles.app.ui.theme.AudioAccent2
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    current: FilterState,
    onApply: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker   by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker   by remember { mutableStateOf(false) }

    val startDateState = rememberDatePickerState(
        initialSelectedDateMillis = draft.dateRangeStart?.toLocalDate()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    )
    val endDateState = rememberDatePickerState(
        initialSelectedDateMillis = draft.dateRangeEnd?.toLocalDate()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    )
    val startTimeState = rememberTimePickerState(
        initialHour = draft.dateRangeStart?.hour ?: 0,
        initialMinute = draft.dateRangeStart?.minute ?: 0, is24Hour = true
    )
    val endTimeState = rememberTimePickerState(
        initialHour = draft.dateRangeEnd?.hour ?: 23,
        initialMinute = draft.dateRangeEnd?.minute ?: 59, is24Hour = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E0C1C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.2f)))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Filters", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    val c = draft.activeFilterCount
                    if (c > 0) Text("$c active", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (draft.activeFilterCount > 0) {
                    TextButton(
                        onClick = { draft = FilterState(); onApply(FilterState()) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Clear, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Sort By (always visible, no toggle) ────────────────────────
            FilterBlock(
                icon = Icons.Outlined.Sort,
                title = "Sort By",
                accent = Color(0xFF7C4DFF),
                active = draft.sortBy != SortBy.NEWEST_FIRST
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    SortBy.entries.take(3).forEach { sort ->
                        SortPill(sort.label, draft.sortBy == sort) { draft = draft.copy(sortBy = sort) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    SortBy.entries.drop(3).forEach { sort ->
                        SortPill(sort.label, draft.sortBy == sort) { draft = draft.copy(sortBy = sort) }
                    }
                }
            }

            // ── File Type ──────────────────────────────────────────────────
            FilterBlock(
                icon = Icons.Outlined.Folder,
                title = "File Type",
                accent = Color(0xFF00BFA5),
                active = draft.fileTypeFilter != FileTypeFilter.ALL
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(FileTypeFilter.entries) { type ->
                        TypePill(
                            emoji = type.emoji,
                            label = type.label,
                            selected = draft.fileTypeFilter == type,
                            onClick = { draft = draft.copy(fileTypeFilter = type) }
                        )
                    }
                }
            }

            // ── Search ─────────────────────────────────────────────────────
            FilterBlock(
                icon = Icons.Outlined.Search,
                title = "Search by Name",
                accent = Color(0xFF40C4FF),
                active = draft.searchQuery.isNotBlank()
            ) {
                OutlinedTextField(
                    value = draft.searchQuery,
                    onValueChange = { draft = draft.copy(searchQuery = it) },
                    placeholder = { Text("File name or keyword", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (draft.searchQuery.isNotEmpty())
                            IconButton(onClick = { draft = draft.copy(searchQuery = "") }) {
                                Icon(Icons.Filled.Clear, null, modifier = Modifier.size(16.dp))
                            }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF40C4FF),
                        unfocusedBorderColor = Color.White.copy(0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(0.8f)
                    )
                )
                Spacer(Modifier.height(10.dp))
                Text("Match type", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.5f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NameFilterType.entries.forEach { type ->
                        MatchPill(type.label, draft.nameFilterType == type) {
                            draft = draft.copy(nameFilterType = type)
                        }
                    }
                }
            }

            // ── Date Range ─────────────────────────────────────────────────
            FilterBlock(
                icon = Icons.Outlined.DateRange,
                title = "Date Range",
                accent = Color(0xFFFF6D00),
                active = draft.dateRangeStart != null || draft.dateRangeEnd != null
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // From
                    Column(Modifier.weight(1f)) {
                        Text("From", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                        Spacer(Modifier.height(4.dp))
                        DateButton(
                            label = draft.dateRangeStart?.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) ?: "Any",
                            active = draft.dateRangeStart != null,
                            onClick = { showStartDatePicker = true }
                        )
                        if (draft.dateRangeStart != null) {
                            TextButton(onClick = { showStartTimePicker = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set time", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF6D00))
                            }
                        }
                    }
                    // To
                    Column(Modifier.weight(1f)) {
                        Text("To", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                        Spacer(Modifier.height(4.dp))
                        DateButton(
                            label = draft.dateRangeEnd?.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) ?: "Any",
                            active = draft.dateRangeEnd != null,
                            onClick = { showEndDatePicker = true }
                        )
                        if (draft.dateRangeEnd != null) {
                            TextButton(onClick = { showEndTimePicker = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set time", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF6D00))
                            }
                        }
                    }
                }
                if (draft.dateRangeStart != null || draft.dateRangeEnd != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { draft = draft.copy(dateRangeStart = null, dateRangeEnd = null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.Clear, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear dates", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── File Size ──────────────────────────────────────────────────
            FilterBlock(
                icon = Icons.Outlined.DataUsage,
                title = "File Size",
                accent = Color(0xFFE040FB),
                active = draft.isSizeFilterEnabled
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = draft.isSizeFilterEnabled,
                        onCheckedChange = { draft = draft.copy(isSizeFilterEnabled = it) })
                    Spacer(Modifier.width(10.dp))
                    Text("Filter by file size", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.8f))
                }
                AnimatedVisibility(visible = draft.isSizeFilterEnabled,
                    enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${draft.minSizeMB.toInt()} MB",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFFE040FB),
                                fontWeight = FontWeight.Bold)
                            Text("${draft.maxSizeMB.toInt()} MB",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFFE040FB),
                                fontWeight = FontWeight.Bold)
                        }
                        RangeSlider(
                            value = draft.minSizeMB..draft.maxSizeMB,
                            onValueChange = { draft = draft.copy(minSizeMB = it.start, maxSizeMB = it.endInclusive) },
                            valueRange = 0f..500f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE040FB),
                                activeTrackColor = Color(0xFFE040FB)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0 MB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                            Text("500 MB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                        }
                    }
                }
            }

            // ── Duration (audio) ───────────────────────────────────────────
            FilterBlock(
                icon = Icons.Outlined.Timer,
                title = "Duration (Audio)",
                accent = AudioAccent,
                active = draft.isDurationFilterEnabled
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = draft.isDurationFilterEnabled,
                        onCheckedChange = { draft = draft.copy(isDurationFilterEnabled = it) })
                    Spacer(Modifier.width(10.dp))
                    Text("Filter by audio length", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.8f))
                }
                AnimatedVisibility(visible = draft.isDurationFilterEnabled,
                    enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmtSecs(draft.minDurationSec.toInt()),
                                style = MaterialTheme.typography.labelSmall, color = AudioAccent,
                                fontWeight = FontWeight.Bold)
                            Text(fmtSecs(draft.maxDurationSec.toInt()),
                                style = MaterialTheme.typography.labelSmall, color = AudioAccent,
                                fontWeight = FontWeight.Bold)
                        }
                        RangeSlider(
                            value = draft.minDurationSec..draft.maxDurationSec,
                            onValueChange = { draft = draft.copy(minDurationSec = it.start, maxDurationSec = it.endInclusive) },
                            valueRange = 0f..600f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = AudioAccent, activeTrackColor = AudioAccent)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0:00", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                            Text("10:00", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                        }
                    }
                }
            }

            // ── Apply ──────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(AudioAccent2, AudioAccent)),
                        RoundedCornerShape(16.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FilterAlt, null, tint = Color.White,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        val count = draft.activeFilterCount
                        Text(
                            if (count > 0) "Apply Filters ($count active)" else "Apply Filters",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDateState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        draft = draft.copy(dateRangeStart = LocalDateTime.of(d, LocalTime.of(0, 0)))
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = startDateState) }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDateState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        draft = draft.copy(dateRangeEnd = LocalDateTime.of(d, LocalTime.of(23, 59)))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = endDateState) }
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Start Time") },
            text = { TimePicker(state = startTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    val base = draft.dateRangeStart ?: LocalDateTime.now()
                    draft = draft.copy(dateRangeStart = base.withHour(startTimeState.hour).withMinute(startTimeState.minute))
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") } }
        )
    }

    if (showEndTimePicker) {
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("End Time") },
            text = { TimePicker(state = endTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    val base = draft.dateRangeEnd ?: LocalDateTime.now()
                    draft = draft.copy(dateRangeEnd = base.withHour(endTimeState.hour).withMinute(endTimeState.minute))
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") } }
        )
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun FilterBlock(
    icon: ImageVector,
    title: String,
    accent: Color,
    active: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(if (active) 0.07f else 0.03f),
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.4f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Box(Modifier.size(30.dp).background(accent.copy(0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                }
            }
            content()
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF7C4DFF).copy(0.2f) else Color.White.copy(0.05f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF).copy(0.6f)) else null
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(12.dp).padding(end = 2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                color = if (selected) Color(0xFF7C4DFF) else Color.White.copy(0.7f))
        }
    }
}

@Composable
private fun TypePill(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF00BFA5).copy(0.2f) else Color.White.copy(0.05f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BFA5).copy(0.6f)) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                color = if (selected) Color(0xFF00BFA5) else Color.White.copy(0.7f))
        }
    }
}

@Composable
private fun MatchPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF40C4FF).copy(0.15f) else Color.White.copy(0.05f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF40C4FF).copy(0.5f)) else null
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(0xFF40C4FF) else Color.White.copy(0.6f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DateButton(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) Color(0xFFFF6D00).copy(0.15f) else Color.White.copy(0.05f),
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6D00).copy(0.5f)) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarToday, null,
                tint = if (active) Color(0xFFFF6D00) else Color.White.copy(0.4f),
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = if (active) Color(0xFFFF6D00) else Color.White.copy(0.6f))
        }
    }
}

private fun fmtSecs(s: Int): String = "%d:%02d".format(s / 60, s % 60)
