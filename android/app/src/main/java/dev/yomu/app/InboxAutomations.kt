package dev.yomu.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
internal fun InboxAutomations(rules: JSONArray?, playlists: JSONArray?, busy: Boolean, update: (String, String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(enabled = !busy, onClick = { expanded = !expanded }) { Text("Inbox automations (${rules?.length() ?: 0})") }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Future issues go to their chosen playlist automatically, even while this phone is offline.", style = MaterialTheme.typography.bodySmall)
        if ((rules?.length() ?: 0) == 0) Text("Choose a playlist when assigning an issue, then enable Automatically assign future issues.")
        for (i in 0 until (rules?.length() ?: 0)) {
            val rule = rules!!.getJSONObject(i)
            key(rule.getString("id")) {
                var choose by remember { mutableStateOf(false) }
                val target = rule.getString("playlist_id")
                val enabled = rule.optInt("enabled") == 1
                val destinations = (0 until (playlists?.length() ?: 0)).map { playlists!!.getJSONObject(it) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.optString("list_id").ifBlank { rule.optString("sender") }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Switch(checked = enabled, enabled = !busy && destinations.any { it.getString("id") == target },
                            onCheckedChange = { update(rule.getString("id"), target, it) })
                    }
                    Text((if (enabled) "Active" else "Paused") + " → " + (destinations.find { it.getString("id") == target }?.optString("name") ?: "Playlist removed"))
                    Box {
                        TextButton(enabled = !busy && destinations.isNotEmpty(), onClick = { choose = true }) { Text("Change playlist") }
                        DropdownMenu(expanded = choose, onDismissRequest = { choose = false }) {
                            destinations.forEach { playlist ->
                                DropdownMenuItem(text = { Text(playlist.getString("name")) }, onClick = {
                                    choose = false; update(rule.getString("id"), playlist.getString("id"), enabled)
                                })
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
