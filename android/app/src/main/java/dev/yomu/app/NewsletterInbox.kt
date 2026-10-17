package dev.yomu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yomu.app.sync.LibrarySync
import dev.yomu.app.sync.SyncVault
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun NewsletterInbox(modifier: Modifier = Modifier) {
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
    val listState = rememberLazyListState()
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
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Incoming newsletters", style = MaterialTheme.typography.headlineSmall)
            Text("Save to To read, choose a playlist, or dismiss an issue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            inbox?.optString("receivingAddress")?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row {
                TextButton(enabled = !busy, onClick = { action {} }) { Text(if (busy) "Loading…" else "Refresh") }
                TextButton(onClick = { all = !all }) { Text(if (all) "Show awaiting review" else "Show all deliveries") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        preview?.let { content ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(content.optString("title"), style = MaterialTheme.typography.titleLarge)
                    Text(content.optString("sender"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("This saves one issue. Manage automatic rules on the website.", style = MaterialTheme.typography.bodySmall)
                    val playlists = inbox?.optJSONArray("playlists")
                    val name = (0 until (playlists?.length() ?: 0)).map { playlists!!.getJSONObject(it) }.find { it.optString("id") == playlistId }?.optString("name") ?: "No playlist — To read only"
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { choose = true }) { Text(name) }
                        DropdownMenu(expanded = choose, onDismissRequest = { choose = false }) {
                            DropdownMenuItem(text = { Text("No playlist — To read only") }, onClick = { playlistId = ""; choose = false })
                            for (i in 0 until (playlists?.length() ?: 0)) {
                                val p = playlists!!.getJSONObject(i)
                                DropdownMenuItem(text = { Text(p.getString("name")) }, onClick = { playlistId = p.getString("id"); choose = false })
                            }
                        }
                    }
                    Row {
                        Button(enabled = !busy, onClick = { action {
                            request("/deliveries/$selectedId/route", "POST", JSONObject().put("playlistId", playlistId).put("createRule", false))
                            preview = null; message = "Saved to To read."
                            LibrarySync(context).sync()
                        } }) { Text("Save issue") }
                        TextButton(enabled = !busy, onClick = { preview = null }) { Text("Close preview") }
                    }
                    val readableText = remember(content) { dev.yomu.app.data.ReadingTextCleaner.clean(content.optString("text")) }
                    Text(readableText, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        }
        val deliveries = inbox?.optJSONArray("deliveries")
        val rows = (0 until (deliveries?.length() ?: 0)).map { deliveries!!.getJSONObject(it) }.filter {
            all || it.optString("state") in listOf("needs_routing", "paused", "possible_duplicate", "queued", "failed")
        }
        if (inbox != null && rows.isEmpty()) item { Text("Your inbox is clear. New newsletters will appear here.") }
        for (delivery in rows) item(key = delivery.getString("id")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(delivery.optString("title"), style = MaterialTheme.typography.titleMedium)
                Text(delivery.optString("sender"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (delivery.optLong("receivedAt") > 0) Text(newsletterArrival(delivery.optLong("receivedAt")), style = MaterialTheme.typography.bodySmall)
                val state = delivery.optString("state")
                Text(when (state) { "imported" -> "Saved to library"; "possible_duplicate" -> "Possible duplicate — review before saving"; "needs_routing", "paused" -> "Awaiting review"; "ignored" -> "Dismissed"; "queued" -> "Preparing"; else -> state })
                if (state in listOf("needs_routing", "paused", "possible_duplicate")) Row {
                    TextButton(enabled = !busy, onClick = { action {
                        selectedId = delivery.getString("id"); playlistId = ""
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
