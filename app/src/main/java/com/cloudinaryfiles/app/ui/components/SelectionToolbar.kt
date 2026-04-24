package com.cloudinaryfiles.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, "Clear selection",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp)) {
                Text(
                    text = "$selectedCount",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("selected", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.weight(1f))
            SelActionBtn(Icons.Outlined.SelectAll,      "All",    onSelectAll, MaterialTheme.colorScheme.onSecondaryContainer)
            SelActionBtn(Icons.Outlined.Share,          "Share",  onShare,     MaterialTheme.colorScheme.onSecondaryContainer)
            SelActionBtn(Icons.Outlined.FileDownload,   "Save",   onDownload,  MaterialTheme.colorScheme.onSecondaryContainer)
            SelActionBtn(Icons.Outlined.DeleteOutline,  "Delete", onDelete,    MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelActionBtn(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint.copy(0.8f), fontSize = 9.sp)
    }
}
