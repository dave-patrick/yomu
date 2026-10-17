package dev.yomu.app

import dev.yomu.app.data.PlaylistEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NewsletterPlaylistsTest {
    @Test fun newPhonePlaylistAppearsBeforeItReachesServer() {
        val choices = newsletterPlaylistChoices(listOf(PlaylistEntity("new", "New playlist", 1, 0)), JSONArray())
        assertEquals("new", choices.getJSONObject(0).getString("id"))
    }

    @Test fun renamedLocalPlaylistWinsWithoutDuplicateIds() {
        val remote = JSONArray().put(JSONObject().put("id", "same").put("name", "Old name"))
        val choices = newsletterPlaylistChoices(listOf(PlaylistEntity("same", "New name", 1, 0)), remote)
        assertEquals(1, choices.length())
        assertEquals("New name", choices.getJSONObject(0).getString("name"))
    }

    @Test fun websiteOnlyPlaylistRemainsAvailable() {
        val choices = newsletterPlaylistChoices(emptyList(), JSONArray().put(JSONObject().put("id", "web").put("name", "Website")))
        assertEquals("web", choices.getJSONObject(0).getString("id"))
    }

    @Test fun missingDestinationSyncsThenChecksFreshServerList() = runBlocking {
        val calls = mutableListOf<String>()
        ensureNewsletterPlaylist("new", emptySet(), true, { calls += "sync" }, { calls += "refresh"; setOf("new") })
        assertEquals(listOf("sync", "refresh"), calls)
    }

    @Test fun existingServerDestinationDoesNotTriggerLibraryUpload() = runBlocking {
        ensureNewsletterPlaylist("existing", setOf("existing"), false, { fail("Unexpected sync") }, { fail("Unexpected refresh"); emptySet() })
    }

    @Test fun downloadOnlyModeCannotUploadNewPhonePlaylist() = runBlocking {
        val failure = runCatching {
            ensureNewsletterPlaylist("new", emptySet(), false, { fail("Must respect download-only mode") }, { emptySet() })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("Two-way"))
    }

    @Test fun failedPlaylistSyncStopsAssignmentPreparation() = runBlocking {
        val failure = runCatching { ensureNewsletterPlaylist("new", emptySet(), true, {}, { emptySet() }) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("conflicts"))
    }
}
