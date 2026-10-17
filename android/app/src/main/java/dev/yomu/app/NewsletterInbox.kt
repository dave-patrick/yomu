package dev.yomu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yomu.app.sync.LibrarySync
import dev.yomu.app.sync.SyncVault
import dev.yomu.app.sync.SyncOptions
import dev.yomu.app.data.PlaylistEntity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun NewsletterInbox(localPlaylists: List<PlaylistEntity>, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val vault = remember { SyncVault(context) }
    val client = remember { OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build() }
    val scope = rememberCoroutineScope()
    var inbox by remember { mutableStateOf<JSONObject?>(null) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var selectedId by remember { mutableStateOf("") }
    var playlistId by remember { mutableStateOf("") }
    var choose by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulkChoose by remember { mutableStateOf(false) }
    var bulkPlaylist by remember { mutableStateOf("") }
    var bulkAutomate by remember { mutableStateOf(false) }
    var automate by remember { mutableStateOf(false) }
    val playlistChoices = remember(localPlaylists, inbox) { newsletterPlaylistChoices(localPlaylists, inbox?.optJSONArray("playlists")) }
    val listState = rememberLazyListState()
    val deliveries = inbox?.optJSONArray("deliveries")
    val rows = (0 until (deliveries?.length() ?: 0)).map { deliveries!!.getJSONObject(it) }.filter {
        all || it.optString("state") in listOf("needs_routing", "paused", "possible_duplicate", "queued", "failed")
    }
    val selectable = rows.filter { it.optString("state") in listOf("needs_routing", "paused", "possible_duplicate", "imported") }.map { it.getString("id") }.toSet()
    LaunchedEffect(inbox, all) { selected = selected.intersect(selectable) }
    suspend fun request(path: String = "", method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(vault.token() != null) { "Connect your web library in Settings → Web library & sync first." }
        val builder = Request.Builder().url(vault.server() + "/api/newsletters" + path)
            .header("Authorization", "Bearer ${vault.token()}")
        if (method != "GET") builder.method(method, if (method == "DELETE") null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val data = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            check(response.isSuccessful) { data.optString("detail", "Could not load inbox. Try Refresh.") }
            data
        }
    }
    suspend fun ensureDestination(id: String) {
        fun serverIds(): Set<String> {
            val playlists = inbox?.optJSONArray("playlists")
            return (0 until (playlists?.length() ?: 0)).map { playlists!!.getJSONObject(it).getString("id") }.toSet()
        }
        ensureNewsletterPlaylist(id, serverIds(), SyncOptions(context).direction().uploads,
            sync = { LibrarySync(context).sync() },
            refresh = { inbox = request(); serverIds() })
    }
    fun action(work: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; message = null
            try { work(); inbox = request() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = error.message ?: "Could not reach your inbox. Try Refresh." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { action {} }
    BackHandler(enabled = selecting && !bulkChoose && !busy) { selecting = false; selected = emptySet() }
    Column(modifier.fillMaxSize()) {
        if (selecting) Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = !busy, onClick = { selecting = false; selected = emptySet() }) { Text("Done selecting") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = !busy, onClick = { selected = selectable }) { Text("Select all") }
                        TextButton(enabled = !busy && selected.isNotEmpty(), onClick = { selected = emptySet() }) { Text("Clear") }
                    }
                    Button(enabled = !busy && selected.isNotEmpty(), onClick = { bulkPlaylist = ""; bulkAutomate = false; bulkChoose = true }) { Text("Assign selected…") }
                }
            }
        }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Incoming newsletters", style = MaterialTheme.typography.headlineSmall)
            Text("Save to To read, choose a playlist, or dismiss an issue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            inbox?.optString("receivingAddress")?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row {
                TextButton(enabled = !busy, onClick = { action {} }) { Text(if (busy) "Loading…" else "Refresh") }
                TextButton(enabled = !busy, onClick = { all = !all }) { Text(if (all) "Show awaiting review" else "Show all deliveries") }
            }
            if (!selecting) TextButton(enabled = !busy && selectable.isNotEmpty(), onClick = {
                selecting = true; selected = emptySet(); preview = null
            }) { Text("Select issues") }
            Text(if (selecting) "Possible duplicates are included only when selected. Preparing and failed issues cannot be assigned yet." else "Hold an issue to start selecting.", style = MaterialTheme.typography.bodySmall)
            InboxAutomations(inbox?.optJSONArray("rules"), playlistChoices, busy) { id, target, enabled ->
                action {
                    ensureDestination(target)
                    request("/rules/$id", "PUT", JSONObject().put("playlistId", target).put("enabled", enabled))
                    message = "Automation updated."
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        preview?.let { content ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(content.optString("title"), style = MaterialTheme.typography.titleLarge)
                    Text(content.optString("sender"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Save this issue once, or also automate future issues from this newsletter.", style = MaterialTheme.typography.bodySmall)
                    val playlists = playlistChoices
                    val name = (0 until (playlists?.length() ?: 0)).map { playlists!!.getJSONObject(it) }.find { it.optString("id") == playlistId }?.optString("name") ?: "No playlist — To read only"
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { choose = true }) { Text(name) }
                        DropdownMenu(expanded = choose, onDismissRequest = { choose = false }) {
                            DropdownMenuItem(text = { Text("No playlist — To read only") }, onClick = { playlistId = ""; automate = false; choose = false })
                            for (i in 0 until (playlists?.length() ?: 0)) {
                                val p = playlists!!.getJSONObject(i)
                                DropdownMenuItem(text = { Text(p.getString("name")) }, onClick = { playlistId = p.getString("id"); choose = false })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(automate, enabled = !busy && playlistId.isNotEmpty(), role = Role.Checkbox,
                        onValueChange = { automate = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = automate, onCheckedChange = null, enabled = !busy && playlistId.isNotEmpty())
                        Text("Automatically assign future issues", Modifier.weight(1f))
                    }
                    if (automate) Text("Matches " + content.optString("listId").ifBlank { content.optString("sender") } + ". Check forwarded sender details before saving.", style = MaterialTheme.typography.bodySmall)
                    Row {
                        Button(enabled = !busy, onClick = { action {
                            ensureDestination(playlistId)
                            request("/deliveries/$selectedId/route", "POST", JSONObject().put("playlistId", playlistId).put("createRule", automate))
                            preview = null; message = if (automate) "Issue saved and automation created." else "Saved to To read."
                            LibrarySync(context).sync()
                        } }) { Text(if (automate) "Save issue & automation" else "Save issue") }
                        TextButton(enabled = !busy, onClick = { preview = null }) { Text("Close preview") }
                    }
                    val readableText = remember(content) { dev.yomu.app.data.ReadingTextCleaner.clean(content.optString("text")) }
                    Text(readableText, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        }
        if (inbox != null && rows.isEmpty()) item { Text("Your inbox is clear. New newsletters will appear here.") }
        for (delivery in rows) item(key = delivery.getString("id")) {
            val id = delivery.getString("id")
            val state = delivery.optString("state")
            Column(Modifier.fillMaxWidth().combinedClickable(enabled = !busy && id in selectable,
                onLongClickLabel = "Select issue",
                onLongClick = { selecting = true; selected = selected + id; preview = null },
                onClick = {
                    if (selecting || state == "imported") {
                        selecting = true; selected = if (id in selected) selected - id else selected + id; preview = null
                    } else action {
                        selectedId = id; playlistId = ""; automate = false
                        preview = request("/deliveries/$id"); listState.scrollToItem(0)
                    }
                }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selecting && id in selectable) Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = id in selected, enabled = !busy, onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id })
                    Text(delivery.optString("title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                } else Text(delivery.optString("title"), style = MaterialTheme.typography.titleMedium)
                Text(delivery.optString("sender"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (delivery.optLong("receivedAt") > 0) Text(newsletterArrival(delivery.optLong("receivedAt")), style = MaterialTheme.typography.bodySmall)
                Text(when (state) { "imported" -> "Saved to library"; "possible_duplicate" -> "Possible duplicate — review before saving"; "needs_routing", "paused" -> "Awaiting review"; "ignored" -> "Dismissed"; "queued" -> "Preparing"; else -> state })
                if (!selecting && state in listOf("needs_routing", "paused", "possible_duplicate")) Row {
                    TextButton(enabled = !busy, onClick = { action {
                        selectedId = delivery.getString("id"); playlistId = ""; automate = false
                        preview = request("/deliveries/$selectedId")
                        listState.scrollToItem(0)
                    } }) { Text("Preview & assign") }
                    TextButton(enabled = !busy, onClick = { action { request("/deliveries/${delivery.getString("id")}", "DELETE") } }) { Text("Dismiss") }
                }
                if (state == "failed") Row {
                    TextButton(enabled = !busy, onClick = { action { request("/deliveries/${delivery.getString("id")}/retry", "POST") } }) { Text("Retry") }
                    TextButton(enabled = !busy, onClick = { action { request("/deliveries/${delivery.getString("id")}", "DELETE") } }) { Text("Dismiss") }
                }
                HorizontalDivider()
            }
        }
    }
    }
    if (bulkChoose) AlertDialog(onDismissRequest = { if (!busy) bulkChoose = false },
        title = { Text("Assign ${selected.size} " + if (selected.size == 1) "issue" else "issues") },
        text = {
            val playlists = playlistChoices
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                item { message?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                item { Text("Save new issues to To read and add selected issues to your chosen playlist. Existing saved articles are reused.") }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(bulkAutomate, enabled = !busy && bulkPlaylist.isNotEmpty(), role = Role.Checkbox,
                        onValueChange = { bulkAutomate = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bulkAutomate, onCheckedChange = null, enabled = !busy && bulkPlaylist.isNotEmpty())
                        Text("Automatically assign future issues", Modifier.weight(1f))
                    }
                    Text(if (bulkPlaylist.isEmpty()) "Choose a playlist to enable automation." else "Matches each newsletter ID, or its sender if no ID exists. Review forwarded senders before enabling.", style = MaterialTheme.typography.bodySmall)
                }
                item { Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(bulkPlaylist.isEmpty(), enabled = !busy, role = Role.RadioButton,
                    onValueChange = { bulkPlaylist = ""; bulkAutomate = false }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = bulkPlaylist.isEmpty(), onClick = null)
                    Text("To read only — no playlist")
                } }
                for (i in 0 until (playlists?.length() ?: 0)) {
                    val playlist = playlists!!.getJSONObject(i)
                    item(key = playlist.getString("id")) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(bulkPlaylist == playlist.getString("id"), enabled = !busy, role = Role.RadioButton,
                            onValueChange = { bulkPlaylist = playlist.getString("id") }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = bulkPlaylist == playlist.getString("id"), onClick = null)
                            Text(playlist.getString("name"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && selected.isNotEmpty(), onClick = { action {
            val ids = selected.toList()
            val destination = bulkPlaylist
            val createRules = bulkAutomate
            ensureDestination(destination)
            val result = request("/deliveries/bulk-route", "POST", JSONObject().put("deliveryIds", org.json.JSONArray(ids)).put("playlistId", destination).put("createRules", createRules))
            bulkChoose = false; selected = emptySet(); selecting = false; preview = null
            message = "${result.getInt("count")} issues assigned." + if (bulkAutomate) " Future issues will be assigned automatically." else ""
            try { LibrarySync(context).sync() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { message = "Issues assigned. Phone sync is pending; use Sync now in Settings." }
        } }) { Text(if (busy) "Assigning…" else "Assign issues") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { bulkChoose = false }) { Text("Cancel") } })
}
