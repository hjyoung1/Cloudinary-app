package com.cloudinaryfiles.app.data.model

import java.time.LocalDateTime

enum class SortBy(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    NAME_AZ("Name A → Z"),
    NAME_ZA("Name Z → A"),
    SIZE_LARGEST("Largest First"),
    SIZE_SMALLEST("Smallest First")
}

enum class NameFilterType(val label: String) {
    CONTAINS("Contains"),
    EXACT("Exact Match"),
    STARTS_WITH("Starts With"),
    ENDS_WITH("Ends With")
}

enum class BeforeAfterType(val label: String) {
    BEFORE("Before"),
    AFTER("After")
}

enum class FileTypeFilter(val label: String, val emoji: String) {
    ALL("All Files", "📁"),
    AUDIO("Audio", "🎵"),
    VIDEO("Video", "🎬"),
    IMAGE("Images", "🖼️"),
    PDF("PDF", "📄"),
    OTHER("Other", "📎")
}

data class FilterState(
    val searchQuery: String = "",
    val nameFilterType: NameFilterType = NameFilterType.CONTAINS,
    val dateRangeStart: LocalDateTime? = null,
    val dateRangeEnd: LocalDateTime? = null,
    val pivotFileName: String = "",
    val pivotType: BeforeAfterType = BeforeAfterType.AFTER,
    val isPivotFilterEnabled: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.ALL,
    val sortBy: SortBy = SortBy.NEWEST_FIRST,
    val minSizeMB: Float = 0f,
    val maxSizeMB: Float = 500f,
    val isSizeFilterEnabled: Boolean = false,
    // Duration filter (seconds)
    val minDurationSec: Float = 0f,
    val maxDurationSec: Float = 600f,
    val isDurationFilterEnabled: Boolean = false,
    val excludedFolders: Set<String> = emptySet()
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (searchQuery.isNotEmpty()) count++
            if (dateRangeStart != null || dateRangeEnd != null) count++
            if (isPivotFilterEnabled && pivotFileName.isNotEmpty()) count++
            if (fileTypeFilter != FileTypeFilter.ALL) count++
            if (sortBy != SortBy.NEWEST_FIRST) count++
            if (isSizeFilterEnabled) count++
            if (isDurationFilterEnabled) count++
            if (excludedFolders.isNotEmpty()) count++
            return count
        }

    val isAnyFilterActive: Boolean get() = activeFilterCount > 0
}
