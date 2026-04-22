package com.cloudinaryfiles.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * UI section for managing folder exclusions on an account.
 * Add this to the SetupScreen / edit-account form.
 *
 * Usage:
 *   ExcludedFoldersSection(
 *       excludedFolders = account.excludedFolders,
 *       onFoldersChanged = { newList -> account = account.copy(excludedFolders = newList) }
 *   )
 */
@Composable
fun ExcludedFoldersSection(
    excludedFolders: List<String>,
    onFoldersChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1A2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.FolderOff,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Excluded Folders",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        "Files in these paths won't appear in the list",
                        color = Color.White.copy(0.5f),
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Add, "Add exclusion", tint = Color(0xFF7C4DFF))
                }
            }

            // Exclusion chips
            if (excludedFolders.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No exclusions — all folders are shown",
                    color = Color.White.copy(0.35f),
                    fontSize = 12.sp
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    excludedFolders.forEach { folder ->
                        ExclusionChip(
                            path = folder,
                            onRemove = {
                                onFoldersChanged(excludedFolders.filter { it != folder })
                            }
                        )
                    }
                }
            }
        }
    }

    // Add exclusion dialog
    if (showAddDialog) {
        AddExclusionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { path ->
                if (path.isNotBlank() && !excludedFolders.contains(path.trim())) {
                    onFoldersChanged(excludedFolders + path.trim())
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ExclusionChip(path: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF2A1F3D)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOff,
                null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = path,
                color = Color.White.copy(0.85f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    "Remove",
                    tint = Color.White.copy(0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AddExclusionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    // Quick examples per provider
    val examples = listOf(
        "/Camera Uploads",
        "/Apps/",
        "archive/",
        "backup",
        "thumbnails"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1830))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Exclude a folder",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Enter a folder path or prefix. Files whose path starts with this will be hidden.",
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("e.g. /Camera Uploads", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Color(0xFF7C4DFF)
                    ),
                    leadingIcon = {
                        Icon(Icons.Outlined.Folder, null, tint = Color.Gray)
                    }
                )

                // Quick-add suggestions
                Spacer(Modifier.height(10.dp))
                Text("Suggestions:", color = Color.White.copy(0.4f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    examples.take(3).forEach { example ->
                        SuggestionChip(
                            onClick = { input = example },
                            label = { Text(example, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF2A1F3D),
                                labelColor = Color.White.copy(0.7f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(0.5f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(input) },
                        enabled = input.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text("Add Exclusion")
                    }
                }
            }
        }
    }
}
