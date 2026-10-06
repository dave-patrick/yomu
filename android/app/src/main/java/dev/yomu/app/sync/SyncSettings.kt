package dev.yomu.app.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.yomu.app.SettingsSectionHeader
import dev.yomu.app.frostedCardColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SyncSettings() {
    val context = LocalContext.current.applicationContext
    val vault = remember { SyncVault(context) }
    val sync = remember { LibrarySync(context) }
    val scope = rememberCoroutineScope()
    val options = remember { SyncOptions(context) }
    var direction by remember { mutableStateOf(options.direction()) }
    var minutes by remember { mutableStateOf(options.minutes()) }
    var directionMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(vault.server()) }
    var token by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(vault.token() != null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val syncState by remember { SyncStatusStore.get(context) }.state.collectAsStateWithLifecycle()
    var actionError by remember { mutableStateOf<String?>(null) }
    val status = syncState.message
    val working = busy || syncState.inProgress
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader("Web library & sync", if (connected) "${direction.label} · ${syncIntervalLabel(minutes)}\n$status" else "Connect your website and phone library", expanded) { expanded = !expanded }
        if (expanded) {
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=frostedCardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (connected) Text(runCatching { java.net.URI(server).host }.getOrDefault(server), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(modifier=Modifier.fillMaxWidth(),enabled=!working,onClick={ directionMenu=true }) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Direction");Text(direction.label) }
            }
            DropdownMenu(expanded=directionMenu,onDismissRequest={ directionMenu=false }) {
                SyncDirection.entries.forEach { choice -> DropdownMenuItem(text={ Text(choice.label) },onClick={
                    directionMenu=false
                    scope.launch { busy=true; try { sync.setOptions(choice,minutes);direction=choice;actionError=null } catch(error: kotlinx.coroutines.CancellationException) { throw error } catch(error: Exception) { actionError=error.message } finally { busy=false } }
                }) }
            }
        }
        if (direction != SyncDirection.BOTH) Text(when(direction) {
            SyncDirection.BOTH -> ""
            SyncDirection.UPLOAD -> "Send phone changes to the website. Website changes are not applied to this phone."
            SyncDirection.DOWNLOAD -> "Bring website changes to this phone. Phone changes are not uploaded; website values can replace edits to matching items."
        },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(modifier=Modifier.fillMaxWidth(),enabled=!working,onClick={ intervalMenu=true }) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Sync interval");Text(syncIntervalLabel(minutes)) }
            }
            DropdownMenu(expanded=intervalMenu,onDismissRequest={ intervalMenu=false }) {
                SyncOptions.intervals.forEach { choice -> DropdownMenuItem(text={ Text(syncIntervalLabel(choice)) },onClick={
                    intervalMenu=false
                    scope.launch { busy=true; try { sync.setOptions(direction,choice);minutes=choice;actionError=null } catch(error: kotlinx.coroutines.CancellationException) { throw error } catch(error: Exception) { actionError=error.message } finally { busy=false } }
                }) }
            }
        }
        Text(if(minutes==0) "Sync only when you tap Sync now." else if(minutes<15) "Every $minutes min while open; 15 min or longer in the background." else "Background sync may be delayed by Android.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!connected) {
            OutlinedTextField(server,{ server=it },label={ Text("Yomu website (https://…)") },singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{ token=it },label={ Text("Private access key") },visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(if(direction.uploads) "Connecting uploads your current library and documents to your private server. Existing items are merged; provider settings are excluded." else "Connecting downloads your website library. Your phone library is not uploaded.", style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(enabled=!working,onClick={ scope.launch {
                busy=true
                actionError=null
                try {
                    if(!connected) { sync.configure(server.trim(),token);token="";connected=true }
                    sync.sync()
                } catch(error: kotlinx.coroutines.CancellationException) { throw error }
                catch(error: Exception) { actionError=error.message ?: "Sync failed. Try again." }
                finally { busy=false }
            } }) { Text(if(working) "Syncing…" else if(connected) "Sync now" else "Connect & sync") }
            if(connected) OutlinedButton(enabled=!working,onClick={ scope.launch { busy=true; try { sync.disconnect();connected=false;actionError=null } catch(error: kotlinx.coroutines.CancellationException) { throw error } catch(error: Exception) { actionError=error.message } finally { busy=false } } }) { Text("Disconnect") }
        }
        if (syncState.lastSuccess > 0) Text("Last successful sync: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(syncState.lastSuccess)), style=MaterialTheme.typography.bodySmall)
        if (syncState.lastAttempt > 0) Text("Last attempt: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(syncState.lastAttempt)), style=MaterialTheme.typography.bodySmall)
        (syncState.error ?: actionError)?.let { Text(it, color=MaterialTheme.colorScheme.error) }
        if (syncState.error != null && !syncState.retryable) Text("Automatic retries are paused. Fix the issue, then tap Sync now or reconnect.", style=MaterialTheme.typography.bodySmall)
        }
        }
        }
    }
}

private fun syncIntervalLabel(minutes: Int) = when(minutes) {
    0 -> "Manual only"
    1 -> "Every minute"
    60 -> "Every hour"
    240 -> "Every 4 hours"
    else -> "Every $minutes minutes"
}
