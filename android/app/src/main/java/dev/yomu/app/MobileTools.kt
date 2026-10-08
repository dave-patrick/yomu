package dev.yomu.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yomu.app.data.ReadingAnchor
import dev.yomu.app.data.ReadingItemEntity
import dev.yomu.app.sync.SyncStatusStore
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Secondary item tools stay collapsed so the article remains the main surface. */
@Composable
internal fun ReaderItemTools(item: ReadingItemEntity, vm: LibraryViewModel, offset: Int, listeningOffset: Int?, onReturn: (Int) -> Unit) {
    val context = LocalContext.current
    val annotations by vm.annotationsFor(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var tags by rememberSaveable(item.id, item.tags) { mutableStateOf(item.tags) }
    var note by rememberSaveable(item.id) { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("bookmark") }
    var showNote by rememberSaveable { mutableStateOf(false) }
    val original = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.exportOriginal(item, uri) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri -> if (uri != null) vm.exportNotes(item, uri) }
    val prefs = context.getSharedPreferences("sync_state", 0)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide article tools" else "Source, tags & bookmarks (${annotations.size})") }
    if (expanded) Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Open source") } }
        if (item.sourceType == "newsletter") Text("Email body saved for reading.", style = MaterialTheme.typography.bodySmall)
        if (item.sourceType != "url" && item.sourceType != "newsletter") {
            val available = item.originalFilePath?.let { File(it).isFile } == true
            Text(if (available) "Original saved on this phone" else "Original is not downloaded", style = MaterialTheme.typography.bodySmall)
            if (available) TextButton(onClick = { original.launch(item.sourceName ?: item.title) }) { Text("Save original to files") }
            else TextButton(onClick = { vm.downloadOriginal(item.id) }) { Text("Download original") }
            prefs.getString("file_error:${item.id}", null)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedTextField(tags, { tags = it }, label = { Text("Tags, separated by commas") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { vm.setTags(item.id, tags) }) { Text("Save tags") }
        if (!prefs.getBoolean("mobile_v2", false)) Text("Tags and notes stay on this phone until the server supports mobile library features.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (choice in listOf("bookmark", "highlight", "note")) TextButton(onClick = { kind = choice; showNote = true }) { Text("Add $choice") }
            TextButton(enabled = vm.playback.collectAsStateWithLifecycle().value?.itemId == item.id, onClick = { vm.saveAnnotation(item, listeningOffset ?: offset, "listening bookmark", "") }) { Text("Bookmark listening") }
        }
        annotations.forEach { annotation ->
            val resolved = remember(item.normalizedText, annotation.anchor) { ReadingAnchor.resolve(item.normalizedText, annotation.anchor) }
            Column {
                Text(annotation.note.ifBlank { JSONObject(annotation.anchor).optString("quote") }, style = MaterialTheme.typography.bodySmall)
                Text(annotation.kind + if (resolved == null) " · text changed; position unavailable" else "", style = MaterialTheme.typography.labelSmall)
                Row { TextButton(enabled = resolved != null, onClick = { onReturn(resolved!!) }) { Text("Return here") }; TextButton(onClick = { vm.deleteAnnotation(annotation.id) }) { Text("Remove") } }
            }
        }
        TextButton(enabled = annotations.isNotEmpty(), onClick = { export.launch("Yomu-notes.md") }) { Text("Export notes & bookmarks") }
        if (item.importState != "ready") {
            Text(item.warning ?: "${item.importState}…", style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = { vm.retryImport(item.id) }) { Text("Retry") }; TextButton(onClick = { vm.cancelImport(item.id) }) { Text("Cancel import") } }
        }
    }
    if (showNote) AlertDialog(onDismissRequest = { showNote = false }, title = { Text("Add $kind here") },
        text = { Column { Text("Saved at the first visible sentence. Copy selected text into the note when needed.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = { vm.saveAnnotation(item, offset, kind, note); note = ""; showNote = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showNote = false }) { Text("Cancel") } })
}

@Composable
internal fun MobileHealthTools(vm: LibraryViewModel) {
    val context = LocalContext.current
    val items by vm.allItems.collectAsStateWithLifecycle()
    val deleted by vm.deletedItems.collectAsStateWithLifecycle()
    val ready by vm.offlineVoiceReady.collectAsStateWithLifecycle()
    val sync by remember { SyncStatusStore.get(context) }.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var trashExpanded by rememberSaveable { mutableStateOf(false) }
    var permanentId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var storage by remember { mutableStateOf("Measuring storage…") }
    val speech = context.getSharedPreferences("speech_preferences", 0)
    val content = context.getSharedPreferences("content_preferences", 0)
    var wifi by rememberSaveable { mutableStateOf(content.getBoolean("wifi_only", false)) }
    var charging by rememberSaveable { mutableStateOf(content.getBoolean("charging_only", false)) }
    var rewind by rememberSaveable { mutableIntStateOf(speech.getInt("resume_rewind_sentences", 0)) }
    LaunchedEffect(expanded, refresh, items.size, deleted.size) {
        if (expanded) storage = withContext(Dispatchers.IO) {
            fun size(file: File) = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            fun mb(bytes: Long) = "%.1f MB".format(bytes / (1024.0 * 1024))
            "Originals: ${mb(size(File(context.filesDir, "library/originals")))} · Cache: ${mb(size(context.cacheDir))} · Offline models: ${mb(size(File(context.filesDir, "kokoro")))}"
        }
    }
    SettingsSectionHeader("Library health & recovery", "${items.size} saved items · ${deleted.size} deleted · ${if (ready) "offline voice ready" else "offline voice needed"}", expanded) { expanded = !expanded }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(storage, style = MaterialTheme.typography.bodySmall)
        Text(sync.message, style = MaterialTheme.typography.bodySmall)
        sync.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("${items.count { it.importState != "ready" }} imports need attention", style = MaterialTheme.typography.bodySmall)
        items.filter { it.importState != "ready" }.forEach { item ->
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(item.warning ?: item.importState, style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = { vm.retryImport(item.id) }) { Text("Retry") }; TextButton(onClick = { vm.cancelImport(item.id) }) { Text("Cancel") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Wi-Fi only for background transfers", Modifier.weight(1f)); Switch(wifi, { wifi = it; content.edit().putBoolean("wifi_only", it).apply(); dev.yomu.app.sync.SyncWork.schedule(context) }) }
        Text("Manual Sync now and original downloads remain explicit requests.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Charge before document extraction", Modifier.weight(1f)); Switch(charging, { charging = it; content.edit().putBoolean("charging_only", it).apply() }) }
        Row(Modifier.horizontalScroll(rememberScrollState())) { for (value in 0..3) FilterChip(selected = rewind == value, onClick = { rewind = value; speech.edit().putInt("resume_rewind_sentences", value).apply() }, label = { Text(if (value == 0) "No resume rewind" else "Rewind $value sentences") }) }
        TextButton(onClick = { vm.cleanSpeechCache { refresh++ } }) { Text("Clear inactive speech cache") }
        TextButton(onClick = { trashExpanded = !trashExpanded }) { Text("Deleted items (${deleted.size})") }
        if (trashExpanded) {
            Text("Retained on this phone until permanently removed. Restore uses the original item identity and syncs on the next pass. Archive is separate.", style = MaterialTheme.typography.bodySmall)
            deleted.forEach { row ->
                val title = remember(row.payload) { runCatching { JSONObject(row.payload).getJSONArray("reading_items").getJSONObject(0).getString("title") }.getOrDefault("Deleted item") }
                Text(title, style = MaterialTheme.typography.titleSmall)
                Row { TextButton(onClick = { vm.restoreDeleted(row.id) }) { Text("Restore") }; TextButton(onClick = { permanentId = row.id }) { Text("Remove permanently") } }
            }
        }
    }
    if (permanentId != null) AlertDialog(onDismissRequest = { permanentId = null }, title = { Text("Remove permanently?") }, text = { Text("This removes the recovery copy and original document from this phone. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.permanentlyDelete(permanentId!!); permanentId = null }) { Text("Remove permanently") } }, dismissButton = { TextButton(onClick = { permanentId = null }) { Text("Cancel") } })
}
