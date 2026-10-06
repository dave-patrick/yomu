package dev.yomu.app.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SyncSettings() {
    val context = LocalContext.current.applicationContext
    val vault = remember { SyncVault(context) }
    val sync = remember { LibrarySync(context) }
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(vault.server()) }
    var token by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(vault.token() != null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getSharedPreferences("sync_state",0).getString("status", "Not connected").orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Web library & sync", style = MaterialTheme.typography.titleMedium)
        Text("Sync saved content, playlists, Up next, history, and reading progress with your Yomu website. Your speech keys stay on this phone.", style = MaterialTheme.typography.bodyMedium)
        Text(status, color = MaterialTheme.colorScheme.primary)
        if(!connected) {
            OutlinedTextField(server,{ server=it },label={ Text("Yomu website (https://…)") },singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{ token=it },label={ Text("Private access key") },visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Connecting uploads your current library and documents to your private server. Existing items are merged; provider settings are excluded.", style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(enabled=!busy,onClick={ scope.launch {
                busy=true
                try {
                    if(!connected) { sync.configure(server.trim(),token);token="";connected=true }
                    status=sync.sync()
                } catch(error: Exception) { status=error.message ?: "Sync failed. Try again." }
                finally { busy=false }
            } }) { Text(if(busy) "Syncing…" else if(connected) "Sync now" else "Connect & sync") }
            if(connected) OutlinedButton(enabled=!busy,onClick={ scope.launch { sync.disconnect();connected=false;status="Disconnected. Your phone library is preserved." } }) { Text("Disconnect") }
        }
    }
}
