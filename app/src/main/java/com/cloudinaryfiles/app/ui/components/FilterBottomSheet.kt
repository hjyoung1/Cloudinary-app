package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudinaryfiles.app.data.model.*
import java.time.LocalDate
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

    val startDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = draft.dateRangeStart?.toLocalDate()?.toEpochDay()?.times(86_400_000L)
    )
    val endDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = draft.dateRangeEnd?.toLocalDate()?.toEpochDay()?.times(86_400_000L)
    )
    val startTimeState = rememberTimePickerState(
        initialHour = draft.dateRangeStart?.hour ?: 0,
        initialMinute = draft.dateRangeStart?.minute ?: 0,
        is24Hour = true
    )
    val endTimeState = rememberTimePickerState(
        initialHour = draft.dateRangeEnd?.hour ?: 23,
        initialMinute = draft.dateRangeEnd?.minute ?: 59,
        is24Hour = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { draft = FilterState(); onApply(FilterState()) }) {
                    Text("Reset All", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 1. Search by name ──────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.Search, title = "Search by Name") {
                OutlinedTextField(
                    value = draft.searchQuery,
                    onValueChange = { draft = draft.copy(searchQuery = it) },
                    label = { Text("File name or keyword") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (draft.searchQuery.isNotEmpty())
                            IconButton(onClick = { draft = draft.copy(searchQuery = "") }) {
                                Icon(Icons.Filled.Clear, null)
                            }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Match type", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NameFilterType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.nameFilterType == type,
                            onClick  = { draft = draft.copy(nameFilterType = type) },
                            label    = { Text(type.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // ── 2. File Type ────────────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.Folder, title = "File Type") {
                val types = FileTypeFilter.entries
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    types.take(3).forEach { type ->
                        FilterChip(
                            selected = draft.fileTypeFilter == type,
                            onClick  = { draft = draft.copy(fileTypeFilter = type) },
                            label    = { Text("${type.emoji} ${type.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    types.drop(3).forEach { type ->
                        FilterChip(
                            selected = draft.fileTypeFilter == type,
                            onClick  = { draft = draft.copy(fileTypeFilter = type) },
                            label    = { Text("${type.emoji} ${type.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // ── 3. Sort ─────────────────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.Sort, title = "Sort By") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortBy.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sort ->
                                FilterChip(
                                    selected = draft.sortBy == sort,
                                    onClick  = { draft = draft.copy(sortBy = sort) },
                                    label    = { Text(sort.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── 4. Date Range ─────────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.DateRange, title = "Date Range") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("From", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedCard(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarToday, null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    draft.dateRangeStart?.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) ?: "Any",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (draft.dateRangeStart != null) {
                            TextButton(onClick = { showStartTimePicker = true }, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set time", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("To", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedCard(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarToday, null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    draft.dateRangeEnd?.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) ?: "Any",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (draft.dateRangeEnd != null) {
                            TextButton(onClick = { showEndTimePicker = true }, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set time", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (draft.dateRangeStart != null || draft.dateRangeEnd != null) {
                    TextButton(
                        onClick = { draft = draft.copy(dateRangeStart = null, dateRangeEnd = null) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.Clear, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear dates", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── 5. File Size ────────────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.DataUsage, title = "File Size") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.isSizeFilterEnabled,
                        onCheckedChange = { draft = draft.copy(isSizeFilterEnabled = it) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Filter by size", style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(
                    visible = draft.isSizeFilterEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Size: ${draft.minSizeMB.toInt()} MB – ${draft.maxSizeMB.toInt()} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        RangeSlider(
                            value = draft.minSizeMB..draft.maxSizeMB,
                            onValueChange = { range ->
                                draft = draft.copy(minSizeMB = range.start, maxSizeMB = range.endInclusive)
                            },
                            valueRange = 0f..500f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0 MB", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("500 MB", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 6. Duration ─────────────────────────────────────────────────
            FilterSection(icon = Icons.Outlined.Timer, title = "Duration (Audio)") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.isDurationFilterEnabled,
                        onCheckedChange = { draft = draft.copy(isDurationFilterEnabled = it) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Filter by duration", style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(
                    visible = draft.isDurationFilterEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        val minFmt = formatSecs(draft.minDurationSec.toInt())
                        val maxFmt = formatSecs(draft.maxDurationSec.toInt())
                        Text(
                            "Duration: $minFmt – $maxFmt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        RangeSlider(
                            value = draft.minDurationSec..draft.maxDurationSec,
                            onValueChange = { range ->
                                draft = draft.copy(minDurationSec = range.start, maxDurationSec = range.endInclusive)
                            },
                            valueRange = 0f..600f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0:00", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10:00", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 7. Before / After pivot ────────────────────────────────────
            FilterSection(icon = Icons.Outlined.SwapVert, title = "Before / After File") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.isPivotFilterEnabled,
                        onCheckedChange = { draft = draft.copy(isPivotFilterEnabled = it) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Enable pivot filter", style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(
                    visible = draft.isPivotFilterEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.pivotFileName,
                            onValueChange = { draft = draft.copy(pivotFileName = it) },
                            label = { Text("File name (partial match OK)") },
                            leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BeforeAfterType.entries.forEach { type ->
                                FilterChip(
                                    selected = draft.pivotType == type,
                                    onClick  = { draft = draft.copy(pivotType = type) },
                                    label    = { Text("Show ${type.label} this file") }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Apply button ────────────────────────────────────────────────
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.FilterAlt, null)
                Spacer(Modifier.width(8.dp))
                val count = draft.activeFilterCount
                Text(
                    if (count > 0) "Apply ($count active)" else "Apply Filters",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Date/time pickers ───────────────────────────────────────────────────

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { ms ->
                        val date = LocalDate.ofEpochDay(ms / 86_400_000L)
                        draft = draft.copy(dateRangeStart = LocalDateTime.of(date, LocalTime.of(0, 0)))
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = startDatePickerState) }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { ms ->
                        val date = LocalDate.ofEpochDay(ms / 86_400_000L)
                        draft = draft.copy(dateRangeEnd = LocalDateTime.of(date, LocalTime.of(23, 59)))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = endDatePickerState) }
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Set Start Time") },
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
            title = { Text("Set End Time") },
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

@Composable
private fun FilterSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun formatSecs(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return "%d:%02d".format(m, s)
}
