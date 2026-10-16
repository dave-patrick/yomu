package dev.yomu.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*

internal fun newsletterArrival(time: Long): String = "Received " +
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(time))

@Composable
internal fun DeleteArticleDialog(title: String, dismiss: () -> Unit, delete: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Delete article?") },
        text = { Text("“$title” will leave your library and playlists on connected devices. You can restore it from Deleted items on this phone.") },
        confirmButton = { TextButton(onClick = delete) { Text("Delete article") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun ArticleActions(id: String, title: String, vm: LibraryViewModel) {
    var expanded by remember(id) { mutableStateOf(false) }
    var confirm by remember(id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Article actions") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Move to Archive") }, onClick = { expanded = false; vm.bulkAction(setOf(id), "Archive") })
            DropdownMenuItem(text = { Text("Delete article", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; confirm = true })
        }
    }
    if (confirm) DeleteArticleDialog(title, { confirm = false }) {
        confirm = false
        vm.closeReader()
        vm.deleteReadingItem(id)
    }
}
