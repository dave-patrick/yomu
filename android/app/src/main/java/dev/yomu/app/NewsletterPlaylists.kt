package dev.yomu.app

import dev.yomu.app.data.PlaylistEntity
import org.json.JSONArray
import org.json.JSONObject

/** Show new/renamed phone playlists immediately, retaining website-only choices. */
internal fun newsletterPlaylistChoices(local: List<PlaylistEntity>, remote: JSONArray?): JSONArray {
    val choices = linkedMapOf<String, JSONObject>()
    local.forEach { choices[it.id] = JSONObject().put("id", it.id).put("name", it.name) }
    for (i in 0 until (remote?.length() ?: 0)) {
        val playlist = remote!!.getJSONObject(i)
        choices.putIfAbsent(playlist.getString("id"), playlist)
    }
    return JSONArray(choices.values.toList())
}

internal suspend fun ensureNewsletterPlaylist(
    id: String, knownServerIds: Set<String>, uploadsAllowed: Boolean,
    sync: suspend () -> Unit, refresh: suspend () -> Set<String>,
) {
    if (id.isEmpty() || id in knownServerIds) return
    check(uploadsAllowed) { "This playlist is on your phone only. Enable Two-way or Phone → website sync in Settings, then assign again." }
    sync()
    check(id in refresh()) { "The playlist has not reached the website yet. Check Web library & sync for conflicts, then try again." }
}
