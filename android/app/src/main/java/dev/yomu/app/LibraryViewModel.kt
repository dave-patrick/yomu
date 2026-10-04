package dev.yomu.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yomu.app.data.ContentImporter
import dev.yomu.app.data.UrlImportWork
import dev.yomu.app.data.HistoryRow
import dev.yomu.app.data.LibraryRepository
import dev.yomu.app.data.PlaylistEntity
import dev.yomu.app.data.ReadingItemEntity
import dev.yomu.app.data.ReadingListRow
import dev.yomu.app.speech.KokoroModelInstaller
import dev.yomu.app.speech.ModelInstallProgress
import dev.yomu.app.speech.AzureCredentialVault
import dev.yomu.app.speech.AzureRestSpeechProvider
import dev.yomu.app.speech.ElevenLabsCredentialVault
import dev.yomu.app.speech.ElevenLabsRestSpeechProvider
import dev.yomu.app.speech.ElevenLabsUsage
import dev.yomu.app.speech.FishAudioCredentialVault
import dev.yomu.app.speech.FishAudioRestSpeechProvider
import dev.yomu.app.speech.SpeechSynthesisRequest
import dev.yomu.app.speech.SpeechVoice
import dev.yomu.app.speech.SherpaKokoroEspeakProvider
import dev.yomu.app.playback.YomuPlaybackService
import dev.yomu.app.playback.PlaybackEvents
import dev.yomu.app.export.Mp3AudioExporter
import dev.yomu.app.backup.LibraryBackupManager
import dev.yomu.app.backup.OpmlManager
import dev.yomu.app.data.ReadingProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.io.File
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as YomuApplication
    private val repository = LibraryRepository(app.database)
    private val importer = ContentImporter(application, repository.dao)
    private val kokoroInstaller = KokoroModelInstaller(application)
    private val azureVault = AzureCredentialVault(application)
    private val elevenLabsVault = ElevenLabsCredentialVault(application)
    private val fishAudioVault = FishAudioCredentialVault(application)
    private val mp3Exporter = Mp3AudioExporter(application)
    private val backupManager = LibraryBackupManager(application)
    private val opmlManager = OpmlManager(application)
    private val speechPreferences = application.getSharedPreferences("speech_preferences", android.content.Context.MODE_PRIVATE)

    val toRead: StateFlow<List<ReadingListRow>> = repository.dao.observeToReadRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archived: StateFlow<List<ReadingListRow>> = repository.dao.observeArchivedRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val upNext: StateFlow<List<ReadingListRow>> = repository.dao.observeUpNextRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<HistoryRow>> = repository.dao.observeHistoryRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = repository.dao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _offlineVoiceReady = MutableStateFlow(kokoroInstaller.isInstalled())
    val offlineVoiceReady: StateFlow<Boolean> = _offlineVoiceReady.asStateFlow()
    private val _modelInstallProgress = MutableStateFlow<ModelInstallProgress?>(null)
    val modelInstallProgress: StateFlow<ModelInstallProgress?> = _modelInstallProgress.asStateFlow()
    private val _azureKeySaved = MutableStateFlow(azureVault.hasKey())
    val azureKeySaved: StateFlow<Boolean> = _azureKeySaved.asStateFlow()
    private val _azureRegion = MutableStateFlow(azureVault.region())
    val azureRegion: StateFlow<String> = _azureRegion.asStateFlow()
    private val _azureVoices = MutableStateFlow(readCachedVoices("azure_voice_catalog"))
    val azureVoices: StateFlow<List<SpeechVoice>> = _azureVoices.asStateFlow()
    private val _elevenLabsKeySaved = MutableStateFlow(elevenLabsVault.hasKey())
    val elevenLabsKeySaved: StateFlow<Boolean> = _elevenLabsKeySaved.asStateFlow()
    private val _elevenLabsVoices = MutableStateFlow(readCachedVoices("elevenlabs_voice_catalog"))
    val elevenLabsVoices: StateFlow<List<SpeechVoice>> = _elevenLabsVoices.asStateFlow()
    private val _elevenLabsUsage = MutableStateFlow<ElevenLabsUsage?>(null)
    val elevenLabsUsage: StateFlow<ElevenLabsUsage?> = _elevenLabsUsage.asStateFlow()
    private val _fishAudioKeySaved = MutableStateFlow(fishAudioVault.hasKey())
    val fishAudioKeySaved: StateFlow<Boolean> = _fishAudioKeySaved.asStateFlow()
    private val _fishAudioVoices = MutableStateFlow(readCachedVoices("fish_voice_catalog"))
    val fishAudioVoices: StateFlow<List<SpeechVoice>> = _fishAudioVoices.asStateFlow()
    private val _previewPath = MutableStateFlow<String?>(null)
    val previewPath: StateFlow<String?> = _previewPath.asStateFlow()
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    val playlistItems: StateFlow<List<ReadingListRow>> = selectedPlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.dao.observePlaylistRows(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun readCachedVoices(key: String): List<SpeechVoice> = runCatching {
        val json = speechPreferences.getString(key, null) ?: return emptyList()
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            fun string(name: String): String? = item.opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()
            fun strings(name: String): List<String> {
                val values = item.optJSONArray(name) ?: return emptyList()
                return (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
            }
            val id = string("id") ?: return@mapNotNull null
            SpeechVoice(
                id = id, name = string("name") ?: id, locale = string("locale") ?: "",
                gender = string("gender"), accent = string("accent"), age = string("age"),
                description = string("description"), previewUrl = string("previewUrl"),
                localeName = string("localeName"), voiceType = string("voiceType"),
                sampleRateHertz = item.optInt("sampleRateHertz").takeIf { item.has("sampleRateHertz") },
                wordsPerMinute = item.optInt("wordsPerMinute").takeIf { item.has("wordsPerMinute") },
                secondaryLocales = strings("secondaryLocales"), styles = strings("styles"),
                roles = strings("roles"), personalities = strings("personalities"),
                contentCategories = strings("contentCategories"), recommendedModelId = string("recommendedModelId"),
                availableForTiers = strings("availableForTiers"),
            )
        }
    }.getOrDefault(emptyList())

    private fun cacheVoices(key: String, voices: List<SpeechVoice>) {
        val array = JSONArray()
        voices.forEach { voice ->
            val item = JSONObject()
            fun putString(name: String, value: String?) { if (value != null) item.put(name, value) }
            fun putStrings(name: String, values: List<String>) { item.put(name, JSONArray(values)) }
            putString("id", voice.id); putString("name", voice.name); putString("locale", voice.locale)
            putString("gender", voice.gender); putString("accent", voice.accent); putString("age", voice.age)
            putString("description", voice.description); putString("previewUrl", voice.previewUrl)
            putString("localeName", voice.localeName); putString("voiceType", voice.voiceType)
            voice.sampleRateHertz?.let { item.put("sampleRateHertz", it) }
            voice.wordsPerMinute?.let { item.put("wordsPerMinute", it) }
            putStrings("secondaryLocales", voice.secondaryLocales); putStrings("styles", voice.styles)
            putStrings("roles", voice.roles); putStrings("personalities", voice.personalities)
            putStrings("contentCategories", voice.contentCategories); putString("recommendedModelId", voice.recommendedModelId)
            putStrings("availableForTiers", voice.availableForTiers)
            array.put(item)
        }
        speechPreferences.edit().putString(key, array.toString()).apply()
    }

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _reading = MutableStateFlow<ReadingItemEntity?>(null)
    val reading: StateFlow<ReadingItemEntity?> = _reading.asStateFlow()
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()
    private var exportJob: Job? = null
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()
    private val _diagnosticEvents = MutableStateFlow(AppDiagnostics.read(application))
    val diagnosticEvents: StateFlow<List<AppDiagnostics.Entry>> = _diagnosticEvents.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(speechPreferences.getFloat("playback_speed", 1f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    val playback = PlaybackEvents.snapshot

    init {
        viewModelScope.launch {
            PlaybackEvents.messages.collect { message ->
                _status.value = message
                if (message.contains("could not", true) || message.contains("failed", true) || message.contains("unavailable", true)) {
                    AppDiagnostics.record(app, "Playback issue reported")
                    _diagnosticEvents.value = AppDiagnostics.read(app)
                }
            }
        }
    }

    fun importUrl(url: String) = work("Saving link…") {
        val itemId = importer.saveUrlForLater(url.trim())
        UrlImportWork.enqueue(getApplication(), itemId)
        itemId
    }
    fun retryImport(itemId: String) = work("Retrying import…") { UrlImportWork.enqueue(getApplication(), itemId) }
    fun retryPdfPassword(itemId: String, password: String) = work("Unlocking PDF…") {
        importer.retryPdfPassword(itemId, password)
    }

    fun importIntent(intent: Intent?) {
        if (intent == null || (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE)) return
        val context = getApplication<Application>()
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val url = URL.find(sharedText)?.value
        if (url != null) {
            importUrl(url)
            return
        }
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(getUriExtra(intent, Intent.EXTRA_STREAM))
            else -> getUriListExtra(intent, Intent.EXTRA_STREAM)
        }
        if (streams.isEmpty()) {
            _status.value = "This share does not include a public link or supported document."
            return
        }
        streams.forEach { uri ->
            val name = displayName(uri) ?: "Shared document"
            val mime = context.contentResolver.getType(uri) ?: intent.type
            work("Saving $name…") { importer.importDocument(name, mime) {
                context.contentResolver.openInputStream(uri) ?: error("Android could not open this shared file.")
            } }
        }
    }

    fun importUris(uris: List<Uri>) {
        val context = getApplication<Application>()
        uris.forEach { uri ->
            val name = displayName(uri) ?: "Shared document"
            val mime = context.contentResolver.getType(uri)
            work("Saving $name…") { importer.importDocument(name, mime) {
                context.contentResolver.openInputStream(uri) ?: error("Android could not open this file.")
            } }
        }
    }

    fun enqueue(itemId: String) = work("Adding to Up next…") { repository.addToUpNext(itemId) }
    fun removeFromQueue(itemId: String) = work("Removing from Up next…") { repository.removeFromUpNext(itemId) }
    fun moveQueueItem(itemId: String, direction: Int) = work("Updating Up next order…") { repository.moveQueueItem(itemId, direction) }
    fun reorderQueue(itemIds: List<String>) = work("Sorting Up next…") { repository.reorderQueue(itemIds) }
    fun moveToReadItem(itemId: String, direction: Int) = work("Updating To read order…") { repository.moveToReadItem(itemId, direction) }
    fun deleteReadingItem(itemId: String) = work("Deleting saved reading…") {
        repository.dao.getItem(itemId)?.let { item ->
            repository.deleteItem(itemId)
            repository.discardOriginal(item)
        }
    }
    fun archiveReadingItem(itemId: String) = work("Moving to Archive…") { repository.archiveItem(itemId) }
    fun restoreArchivedItem(itemId: String) = work("Restoring to To read…") { repository.restoreArchivedItem(itemId) }
    fun markItemRead(itemId: String) = work("Marking as read…") { repository.markItemRead(itemId) }
    fun markItemUnread(itemId: String) = work("Marking as unread…") { repository.markItemUnread(itemId) }
    fun startNow(itemId: String) {
        work("Starting playback…") {
            val item = repository.dao.getItem(itemId) ?: error("This reading item is no longer available.")
            _reading.value = item
            val resumeSentence = repository.dao.getProgress(itemId)?.sentenceIndex ?: 0
            val intent = Intent(getApplication<Application>(), YomuPlaybackService::class.java)
                .setAction(YomuPlaybackService.ACTION_PLAY_ITEM)
                .putExtra(YomuPlaybackService.EXTRA_ITEM_ID, itemId)
                .putExtra(YomuPlaybackService.EXTRA_SENTENCE, resumeSentence)
            getApplication<Application>().startForegroundService(intent)
        }
    }
    fun openReader(itemId: String) {
        work("Opening reading…") {
            _reading.value = repository.dao.getItem(itemId)
                ?: error("This reading item is no longer available.")
        }
    }
    fun closeReader() { _reading.value = null }
    fun progressFor(itemId: String): Flow<ReadingProgressEntity?> = repository.dao.observeProgress(itemId)
    fun togglePlayback() = playbackControl(YomuPlaybackService.ACTION_TOGGLE)
    fun previousSentence() = playbackControl(YomuPlaybackService.ACTION_PREVIOUS)
    fun nextSentence() = playbackControl(YomuPlaybackService.ACTION_NEXT)
    fun seekSentence(sentence: Int) = playbackControl(YomuPlaybackService.ACTION_SEEK) { putExtra(YomuPlaybackService.EXTRA_SENTENCE, sentence) }
    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(.7f, 2f)
        _playbackSpeed.value = safe
        speechPreferences.edit().putFloat("playback_speed", safe).apply()
        playbackControl(YomuPlaybackService.ACTION_SPEED) { putExtra(YomuPlaybackService.EXTRA_SPEED, safe) }
    }
    fun setSleepTimer(minutes: Int) = playbackControl(YomuPlaybackService.ACTION_SLEEP) { putExtra(YomuPlaybackService.EXTRA_MINUTES, minutes) }
    fun enableCallRecovery() = playbackControl(YomuPlaybackService.ACTION_ENABLE_CALL_MONITOR)
    fun disableCallRecovery() = playbackControl(YomuPlaybackService.ACTION_DISABLE_CALL_MONITOR)
    fun exportMp3(item: ReadingItemEntity, destination: Uri) {
        launchMp3Export { mp3Exporter.export(item, destination) { _exportStatus.value = it } }
    }
    fun exportMp3ToFolder(item: ReadingItemEntity, treeUri: Uri, fileName: String) {
        launchMp3Export {
            val root = DocumentFile.fromTreeUri(getApplication<Application>(), treeUri)
                ?: error("The selected MP3 folder is no longer available. Choose it again in Settings.")
            val document = root.createFile("audio/mpeg", fileName)
                ?: error("Android could not create the MP3 in the selected folder.")
            mp3Exporter.export(item, document.uri) { _exportStatus.value = it }
        }
    }
    fun cancelMp3Export() { exportJob?.cancel() }

    private fun launchMp3Export(action: suspend () -> Unit) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try { action() }
            catch (_: CancellationException) { _exportStatus.value = "MP3 export cancelled." }
            catch (error: Exception) { _exportStatus.value = error.message ?: "MP3 export failed." }
            finally { if (exportJob === coroutineContext[Job]) exportJob = null }
        }
    }
    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            try { backupManager.export(destination) { _backupStatus.value = it } }
            catch (error: Exception) { _backupStatus.value = error.message ?: "Backup failed." }
        }
    }
    fun restoreBackup(source: Uri) {
        viewModelScope.launch {
            try { backupManager.restore(source) { _backupStatus.value = it } }
            catch (error: Exception) { _backupStatus.value = error.message ?: "Restore failed." }
        }
    }
    fun exportOpml(destination: Uri) {
        viewModelScope.launch {
            try { opmlManager.export(destination); _backupStatus.value = "OPML URL lists exported." }
            catch (error: Exception) { _backupStatus.value = error.message ?: "OPML export failed." }
        }
    }
    fun importOpml(source: Uri) {
        viewModelScope.launch {
            try { opmlManager.import(source) { _backupStatus.value = it } }
            catch (error: Exception) { _backupStatus.value = error.message ?: "OPML import failed." }
        }
    }

    private fun playbackControl(action: String, configure: Intent.() -> Unit = {}) {
        val intent = Intent(getApplication<Application>(), YomuPlaybackService::class.java).setAction(action).apply(configure)
        getApplication<Application>().startService(intent)
    }
    fun createPlaylist(name: String) = work("Creating playlist…") { repository.createPlaylist(name) }
    fun openPlaylist(id: String) { selectedPlaylistId.value = id }
    fun addToPlaylist(playlistId: String, itemId: String) = work("Adding to playlist…") { repository.addToPlaylist(playlistId, itemId) }
    fun removeFromPlaylist(playlistId: String, itemId: String) = work("Removing from playlist…") { repository.removeFromPlaylist(playlistId, itemId) }
    fun deleteHistory(id: String) = work("Removing history item…") { repository.removeHistoryEntry(id) }
    fun clearHistory() = work("Clearing history…") { repository.clearHistory() }
    fun clearDiagnosticEvents() { AppDiagnostics.clear(app); _diagnosticEvents.value = emptyList() }

    fun installOfflineVoice() {
        viewModelScope.launch {
            _status.value = null
            _modelInstallProgress.value = ModelInstallProgress(0, 0, "Preparing the offline voice…")
            try {
                withContext(Dispatchers.IO) {
                    kokoroInstaller.install { progress -> _modelInstallProgress.value = progress }
                }
                _offlineVoiceReady.value = true
                _modelInstallProgress.value = null
            } catch (error: Exception) {
                _modelInstallProgress.value = null
                _status.value = error.message ?: "Offline voice installation failed."
            }
        }
    }

    fun saveAzureSettings(key: String, region: String) {
        viewModelScope.launch {
            try {
                val refreshCredentials = key.isNotBlank() || region.trim() != _azureRegion.value
                withContext(Dispatchers.IO) {
                    val effectiveKey = key.takeIf { it.isNotBlank() } ?: azureVault.credentials()?.key
                        ?: error("Enter the Azure Speech key.")
                    azureVault.save(effectiveKey, region)
                }
                if (refreshCredentials) {
                    _azureVoices.value = emptyList()
                    speechPreferences.edit().remove("azure_voice_catalog").apply()
                }
                _azureKeySaved.value = true
                _azureRegion.value = azureVault.region()
                _status.value = "Azure Speech settings saved securely."
            } catch (error: Exception) {
                // Provider keys must never be copied into UI errors: HTTP clients can echo invalid header values.
                _status.value = when {
                    error.message?.contains("one Azure Speech key", ignoreCase = true) == true -> error.message
                    error.message?.contains("Enter the Azure Speech key", ignoreCase = true) == true -> error.message
                    else -> "Could not save Azure Speech settings. Check that you entered one key and the correct region."
                }
            }
        }
    }

    fun clearAzureSettings() {
        azureVault.clear()
        _azureKeySaved.value = false
        _azureVoices.value = emptyList()
        speechPreferences.edit().remove("azure_voice_catalog").apply()
        _previewPath.value = null
    }

    fun stopVoicePreview() { _previewPath.value = null }

    fun loadAzureVoices() = work("Loading Azure voice catalog…") {
        val credentials = azureVault.credentials() ?: error("Save your Azure Speech key first.")
        val voices = withContext(Dispatchers.IO) { AzureRestSpeechProvider(credentials).listVoices() }
        _azureVoices.value = voices.sortedWith(compareBy({ !it.locale.startsWith("en", true) }, { it.locale }, { it.name }))
        cacheVoices("azure_voice_catalog", _azureVoices.value)
    }

    fun auditionAzureVoice(voiceId: String) = work("Preparing voice sample…") {
        val credentials = azureVault.credentials() ?: error("Save your Azure Speech key first.")
        val file = File(getApplication<Application>().cacheDir, "voice-previews/${voiceId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            AzureRestSpeechProvider(credentials).synthesize(SpeechSynthesisRequest(
                "Hello. This is a short voice preview in Yomu, a listening library for articles and documents.", voiceId, file))
        }
        _previewPath.value = file.absolutePath
    }

    fun saveElevenLabsKey(key: String) = work("Saving ElevenLabs key…") {
        withContext(Dispatchers.IO) { elevenLabsVault.save(key) }
        speechPreferences.edit().putString("elevenlabs_tier", "").putBoolean("allow_paid_elevenlabs", false).apply()
        _elevenLabsKeySaved.value = true
        _elevenLabsUsage.value = null
        _elevenLabsVoices.value = emptyList()
        speechPreferences.edit().remove("elevenlabs_voice_catalog").apply()
        _status.value = "ElevenLabs key saved securely. Check the account tier before enabling it."
    }

    fun clearElevenLabsKey() {
        elevenLabsVault.clear()
        speechPreferences.edit().putString("elevenlabs_tier", "").putBoolean("allow_paid_elevenlabs", false).apply()
        _elevenLabsKeySaved.value = false
        _elevenLabsUsage.value = null
        _elevenLabsVoices.value = emptyList()
        speechPreferences.edit().remove("elevenlabs_voice_catalog").apply()
    }

    fun loadElevenLabsCatalog() = work("Checking ElevenLabs account and loading voices…") {
        val key = withContext(Dispatchers.IO) { elevenLabsVault.key() } ?: error("Save your ElevenLabs API key first.")
        val provider = ElevenLabsRestSpeechProvider(key)
        val usage = withContext(Dispatchers.IO) { provider.usage() }
        val voices = withContext(Dispatchers.IO) { provider.listVoices() }
        _elevenLabsUsage.value = usage
        speechPreferences.edit().putString("elevenlabs_tier", usage.tier).apply()
        _elevenLabsVoices.value = voices.sortedWith(compareBy({ !it.locale.startsWith("en", true) }, { it.locale }, { it.name }))
        cacheVoices("elevenlabs_voice_catalog", _elevenLabsVoices.value)
        if (!speechPreferences.contains("elevenlabs_model")) {
            val selectedVoiceId = speechPreferences.getString("elevenlabs_voice", null)
            voices.firstOrNull { it.id == selectedVoiceId }?.recommendedModelId?.let { modelId ->
                speechPreferences.edit().putString("elevenlabs_model", modelId).apply()
            }
        }
        if (!usage.isFree) _status.value = "This ElevenLabs account is ${usage.tier}; it may use paid credits. Paid use stays disabled until you opt in."
    }

    fun auditionElevenLabsVoice(voiceId: String) = work("Loading provider voice sample…") {
        val key = withContext(Dispatchers.IO) { elevenLabsVault.key() } ?: error("Save your ElevenLabs API key first.")
        val voice = _elevenLabsVoices.value.firstOrNull { it.id == voiceId } ?: error("Reload the ElevenLabs voice list first.")
        val file = File(getApplication<Application>().cacheDir, "voice-previews/eleven-${voiceId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            ElevenLabsRestSpeechProvider(key).downloadPreview(voice, file)
        }
        _previewPath.value = file.absolutePath
    }

    fun saveFishAudioKey(key: String) = work("Saving Fish Audio key…") {
        withContext(Dispatchers.IO) { fishAudioVault.save(key) }
        _fishAudioKeySaved.value = true
        _fishAudioVoices.value = emptyList()
        speechPreferences.edit().remove("fish_voice_catalog").apply()
        _status.value = "Fish Audio key saved securely. Yomu uses the free S2.1 Pro model."
    }

    fun clearFishAudioKey() {
        fishAudioVault.clear()
        _fishAudioKeySaved.value = false
        _fishAudioVoices.value = emptyList()
        speechPreferences.edit().remove("fish_voice_catalog").apply()
    }

    fun loadFishAudioCatalog() = work("Loading licensed Fish Audio voices…") {
        val key = withContext(Dispatchers.IO) { fishAudioVault.key() } ?: error("Save your Fish Audio API key first.")
        val voices = withContext(Dispatchers.IO) { FishAudioRestSpeechProvider(key).listVoices() }
        _fishAudioVoices.value = voices.sortedBy { it.name.lowercase() }
        cacheVoices("fish_voice_catalog", _fishAudioVoices.value)
    }

    fun auditionFishAudioVoice(voiceId: String) = work("Preparing Fish Audio voice sample…") {
        val key = withContext(Dispatchers.IO) { fishAudioVault.key() } ?: error("Save your Fish Audio API key first.")
        val file = File(getApplication<Application>().cacheDir, "voice-previews/fish-${voiceId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            FishAudioRestSpeechProvider(key).synthesize(SpeechSynthesisRequest(
                "Hello. This is a short voice preview in Yomu, a listening library for articles and documents.", voiceId, file))
        }
        _previewPath.value = file.absolutePath
    }

    fun auditionOfflineVoice(voiceId: Int) = work("Preparing offline voice sample…") {
        check(kokoroInstaller.isInstalled()) { "Install the offline voice package first." }
        val file = File(getApplication<Application>().cacheDir, "voice-previews/kokoro-$voiceId-${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val provider = SherpaKokoroEspeakProvider(kokoroInstaller.modelDirectory, 2)
            try { provider.generate("Hello. This is a short voice preview in Yomu, a listening library for articles and documents.", voiceId, 1f).save(file) }
            finally { provider.close() }
        }
        _previewPath.value = file.absolutePath
    }

    private fun work(startMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            _status.value = startMessage
            try {
                action()
                _status.value = null
            } catch (error: Exception) {
                AppDiagnostics.record(getApplication(), "Action failed (${error::class.simpleName ?: "unknown error"})")
                _diagnosticEvents.value = AppDiagnostics.read(app)
                _status.value = error.message ?: "That action could not be completed."
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun getUriExtra(intent: Intent, key: String): Uri? = if (android.os.Build.VERSION.SDK_INT >= 33)
        intent.getParcelableExtra(key, Uri::class.java) else intent.getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private fun getUriListExtra(intent: Intent, key: String): List<Uri> {
        val rows: ArrayList<Uri>? = if (android.os.Build.VERSION.SDK_INT >= 33)
            intent.getParcelableArrayListExtra<Uri>(key, Uri::class.java) else intent.getParcelableArrayListExtra(key)
        return rows.orEmpty()
    }

    companion object { private val URL = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE) }
}
