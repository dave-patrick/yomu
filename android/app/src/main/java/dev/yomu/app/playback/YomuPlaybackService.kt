package dev.yomu.app.playback

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.yomu.app.YomuApplication
import dev.yomu.app.data.LibraryRepository
import dev.yomu.app.speech.AzureCredentialVault
import dev.yomu.app.speech.AzureRestSpeechProvider
import dev.yomu.app.speech.ElevenLabsCredentialVault
import dev.yomu.app.speech.ElevenLabsRestSpeechProvider
import dev.yomu.app.speech.KokoroModelInstaller
import dev.yomu.app.speech.SherpaKokoroEspeakProvider
import dev.yomu.app.speech.SpeechSynthesisRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.text.BreakIterator
import java.util.Locale

/** Sentence-sized playback keeps the reader synchronized with speech and permits voice failover. */
class YomuPlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val dao by lazy { (application as YomuApplication).database.libraryDao() }
    private val repository by lazy { LibraryRepository((application as YomuApplication).database) }
    private val vault by lazy { AzureCredentialVault(this) }
    private val elevenLabsVault by lazy { ElevenLabsCredentialVault(this) }
    private val installer by lazy { KokoroModelInstaller(this) }
    private val kokoroProviderLock = Any()
    private var kokoroProvider: SherpaKokoroEspeakProvider? = null
    private var itemId: String? = null
    private var title: String = "Yomu"
    private var sentences: List<String> = emptyList()
    private var sourceText = ""
    private var sentenceIndex = 0
    private lateinit var playNowResume: PlayNowResumeState
    private var isGenerating = false
    private var localOnly = false
    private var playbackSpeed = 1f
    private var offlineNoticePlayed = false
    private var lastElevenLabsFailure: String? = null
    private var sleepTimer: Job? = null
    private var sleepUntil = 0L
    private var stopAtEnd = false
    private var playbackPhase = "Paused"
    private var pendingOfflineFile: File? = null
    private var pendingOfflineIndex = -1
    private val sentenceBuffer = SentenceLookAhead<GeneratedSentence>(scope)
    private var generationJob: Job? = null
    private val speechHttpClient = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(60)).build()
    private val speechPreferences by lazy { getSharedPreferences("speech_preferences", MODE_PRIVATE) }
    private val voiceChange = Runnable { applyVoiceChange() }
    private val voiceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in VOICE_PREFERENCE_KEYS) { handler.removeCallbacks(voiceChange); handler.post(voiceChange) }
    }
    private var telephonyManager: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null
    private var legacyCallStateListener: PhoneStateListener? = null
    private var callActive = false
    private var resumeAfterCall = false
    private var pausingForCall = false
    private var callResumeAttempts = 0
    private val callResumeRetry = object : Runnable {
        override fun run() {
            if (!resumeAfterCall || callActive || player.currentMediaItem == null) return
            if (player.isPlaying) { handler.postDelayed(this, CALL_RESUME_RETRY_MS); return }
            if (callResumeAttempts >= MAX_CALL_RESUME_ATTEMPTS) { resumeAfterCall = false; return }
            callResumeAttempts++
            player.play()
            handler.postDelayed(this, CALL_RESUME_RETRY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            // ExoPlayer holds this only while ready/buffering; speech preparation has its own lease.
            setWakeMode(C.WAKE_MODE_LOCAL)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { publishSnapshot() }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    if (callActive && !ready && pausingForCall) pausingForCall = false
                    else if (callActive && ready && !pausingForCall) {
                        resumeAfterCall = false; pausingForCall = true; player.pause()
                    } else if (resumeAfterCall && !callActive && !ready && !pausingForCall) {
                        resumeAfterCall = false; handler.removeCallbacks(callResumeRetry)
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (player.currentMediaItem?.mediaId?.endsWith(":-1") == true) {
                            val file = pendingOfflineFile
                            val index = pendingOfflineIndex
                            pendingOfflineFile = null
                            pendingOfflineIndex = -1
                            if (file != null) playFile(file, index) else generateAndPlayNext()
                        } else {
                            sentenceIndex++
                            generateAndPlayNext()
                        }
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val mediaId = mediaItem?.mediaId ?: return
                    val separator = mediaId.lastIndexOf(':')
                    if (separator <= 0 || mediaId.substring(0, separator) != itemId) return
                    val index = mediaId.substring(separator + 1).toIntOrNull() ?: return
                    if (index < 0 || index !in sentences.indices) return

                    sentenceIndex = index
                    publishSnapshot()
                    saveCheckpoint()
                    val id = itemId ?: return
                    scope.launch(Dispatchers.IO) {
                        dao.upsertProgress(dev.yomu.app.data.ReadingProgressEntity(
                            itemId = id,
                            sentenceIndex = index,
                            characterOffset = characterOffset(index),
                            lastListenedAt = System.currentTimeMillis(),
                        ))
                    }
                    prefetchFollowing(index + 1)
                }
            })
        }
        session = MediaLibrarySession.Builder(this, player, SessionCallbacks()).build()
        // The phone UI sends service intents, without connecting a MediaController.
        // Register now so Media3 observes playback and starts its foreground notification.
        addSession(session)
        val playbackPrefs = getSharedPreferences("yomu_playback", MODE_PRIVATE)
        playNowResume = PlayNowResumeState(
            playbackPrefs.getString("return_item_id", null)?.let {
                ResumePoint(it, playbackPrefs.getInt("return_sentence", 0))
            },
        )
        playbackSpeed = getSharedPreferences("speech_preferences", MODE_PRIVATE).getFloat("playback_speed", 1f).coerceIn(.7f, 2f)
        player.playbackParameters = PlaybackParameters(playbackSpeed)
        val speechPrefs = getSharedPreferences("speech_preferences", MODE_PRIVATE)
        if (speechPrefs.getBoolean("call_resume_enabled", checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            registerPhoneStateMonitor()
        }
        restorePlaybackCheckpoint()
        speechPreferences.registerOnSharedPreferenceChangeListener(voiceListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    private fun isAllowedCarBrowser(controller: MediaSession.ControllerInfo): Boolean =
        controller.isTrusted || controller.packageName == packageName ||
            (controller.packageName == CAR_HOST_PACKAGE && controller.isPackageNameVerified)

    private fun carFolder(id: String, label: String, folderType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED) =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(label).setArtist("Yomu")
                .setFolderType(folderType).setIsBrowsable(true).setIsPlayable(false).build(),
        ).build()

    private fun carReadingItem(item: dev.yomu.app.data.ReadingItemEntity) =
        MediaItem.Builder().setMediaId("$CAR_ITEM_PREFIX${item.id}").setMediaMetadata(
            MediaMetadata.Builder().setTitle(item.title).setArtist(item.sourceName ?: "Yomu")
                .setDescription(item.sourceType.replaceFirstChar(Char::uppercase))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .setDurationMs(item.estimatedSecondsAtOneX.coerceAtLeast(0) * 1_000L)
                .setIsBrowsable(false).setIsPlayable(true).build(),
        ).build()

    private fun handleGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        if (!isAllowedCarBrowser(browser)) LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED, params)
        else LibraryResult.ofItem(carFolder(CAR_ROOT_ID, "Yomu", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params),
    )

    private fun handleGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        if (!isAllowedCarBrowser(browser)) {
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED, params))
        }
        val result = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val children = withContext(Dispatchers.IO) {
                    when (parentId) {
                        CAR_ROOT_ID -> buildList {
                            add(carFolder(CAR_TO_READ_ID, "To read"))
                            add(carFolder(CAR_UP_NEXT_ID, "Up next"))
                            add(carFolder(CAR_PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                        }
                        CAR_TO_READ_ID -> dao.getToReadInManualOrder().map(::carReadingItem)
                        CAR_UP_NEXT_ID -> dao.getUpNextItems().map(::carReadingItem)
                        CAR_PLAYLISTS_ID -> dao.getPlaylists().map { playlist ->
                            carFolder("$CAR_PLAYLIST_PREFIX${playlist.id}", playlist.name, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        }
                        else -> parentId.removePrefix(CAR_PLAYLIST_PREFIX).takeIf { parentId.startsWith(CAR_PLAYLIST_PREFIX) }
                            ?.let { dao.getPlaylistItems(it).map(::carReadingItem) }.orEmpty()
                    }
                }
                val start = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(0)).coerceAtMost(children.size.toLong()).toInt()
                val end = if (pageSize <= 0) children.size else (start + pageSize).coerceAtMost(children.size)
                result.set(LibraryResult.ofItemList(children.subList(start, end), params))
            } catch (_: Exception) {
                result.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO, params))
            }
        }
        return result
    }

    private fun handleGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (!isAllowedCarBrowser(browser)) {
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED))
        }
        if (mediaId == CAR_ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItem(carFolder(CAR_ROOT_ID, "Yomu"), null))
        if (mediaId in setOf(CAR_TO_READ_ID, CAR_UP_NEXT_ID, CAR_PLAYLISTS_ID)) {
            val label = when (mediaId) { CAR_TO_READ_ID -> "To read"; CAR_UP_NEXT_ID -> "Up next"; else -> "Playlists" }
            return Futures.immediateFuture(LibraryResult.ofItem(carFolder(mediaId, label), null))
        }
        if (mediaId.startsWith(CAR_PLAYLIST_PREFIX)) {
            val result = SettableFuture.create<LibraryResult<MediaItem>>()
            scope.launch {
                try {
                    val playlist = withContext(Dispatchers.IO) { dao.getPlaylist(mediaId.removePrefix(CAR_PLAYLIST_PREFIX)) }
                    if (playlist == null) result.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                    else result.set(LibraryResult.ofItem(carFolder(mediaId, playlist.name), null))
                } catch (_: Exception) {
                    result.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO))
                }
            }
            return result
        }
        if (!mediaId.startsWith(CAR_ITEM_PREFIX)) return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        val result = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            try {
                val item = withContext(Dispatchers.IO) { dao.getItem(mediaId.removePrefix(CAR_ITEM_PREFIX)) }
                if (item == null) result.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                else result.set(LibraryResult.ofItem(carReadingItem(item), null))
            } catch (_: Exception) {
                result.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO))
            }
        }
        return result
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_ITEM -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { playItem(it, intent.getIntExtra(EXTRA_SENTENCE, 0)) }
            ACTION_STOP -> player.pause()
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else {
                val rewind = speechPreferences.getInt("resume_rewind_sentences", 0).coerceIn(0, 3)
                if (rewind > 0 && sentenceIndex > 0) { sentenceIndex = (sentenceIndex - rewind).coerceAtLeast(0); player.stop(); generateAndPlayNext() }
                else if (player.currentMediaItem == null) generateAndPlayNext() else player.play()
            }
            ACTION_NEXT -> { sentenceIndex = (sentenceIndex + 1).coerceAtMost(sentences.size); player.stop(); generateAndPlayNext() }
            ACTION_PREVIOUS -> { sentenceIndex = (sentenceIndex - 1).coerceAtLeast(0); player.stop(); generateAndPlayNext() }
            ACTION_SEEK -> intent.getIntExtra(EXTRA_SENTENCE, -1).takeIf { it >= 0 }?.let { playItem(itemId ?: return START_NOT_STICKY, it) }
            ACTION_SPEED -> {
                playbackSpeed = intent.getFloatExtra(EXTRA_SPEED, 1f).coerceIn(.7f, 2f)
                getSharedPreferences("speech_preferences", MODE_PRIVATE).edit().putFloat("playback_speed", playbackSpeed).apply()
                player.playbackParameters = PlaybackParameters(playbackSpeed)
            }
            ACTION_SLEEP -> {
                sleepTimer?.cancel()
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                stopAtEnd = minutes == -1
                sleepUntil = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                publishSnapshot()
                if (minutes > 0) sleepTimer = scope.launch {
                    delay(minutes * 60_000L)
                    invalidatePreparedSpeech()
                    player.pause()
                    sleepUntil = 0L
                    publishSnapshot()
                }
            }
            ACTION_ENABLE_CALL_MONITOR -> registerPhoneStateMonitor()
            ACTION_DISABLE_CALL_MONITOR -> {
                unregisterPhoneStateMonitor()
                handler.removeCallbacks(callResumeRetry)
                resumeAfterCall = false
                pausingForCall = false
            }
        }
        return START_STICKY
    }

    private fun playItem(id: String, startAt: Int = 0, skipReturnCapture: Boolean = false) {
        invalidatePreparedSpeech()
        playNowResume.onStart(itemId, sentenceIndex, id, skipReturnCapture)
        persistPlayNowResumePoint()
        if (itemId == id && sentences.isNotEmpty()) {
            sentenceIndex = startAt.coerceIn(sentences.indices)
            player.stop()
            generateAndPlayNext()
            return
        }
        scope.launch { withSpeechCpuAwake {
            val item = withContext(Dispatchers.IO) { dao.getItem(id) } ?: return@withSpeechCpuAwake
            itemId = id
            title = item.title
            localOnly = false
            offlineNoticePlayed = false
            sourceText = item.normalizedText
            sentences = splitSentences(sourceText)
            sentenceIndex = startAt.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
            dao.upsertProgress(dev.yomu.app.data.ReadingProgressEntity(itemId = id, sentenceIndex = sentenceIndex, characterOffset = characterOffset(sentenceIndex), lastListenedAt = System.currentTimeMillis()))
            publishSnapshot()
            saveCheckpoint()
            generateAndPlayNext()
        } }
    }

    private suspend fun <T> withSpeechCpuAwake(block: suspend () -> T): T {
        val lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yomu:speech-preparation")
        lock.setReferenceCounted(false)
        return SpeechCpuGuard({ timeout -> lock.acquire(timeout) }, { if (lock.isHeld) lock.release() }).run(block)
    }

    private fun invalidatePreparedSpeech() {
        sentenceBuffer.reset()
        speechHttpClient.dispatcher.cancelAll()
        generationJob?.cancel()
        generationJob = null
        isGenerating = false
        if (player.currentMediaItemIndex >= 0 && player.mediaItemCount > player.currentMediaItemIndex + 1) {
            player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        }
    }

    private fun applyVoiceChange() {
        val resumeWaiting = isGenerating || player.playWhenReady
        invalidatePreparedSpeech()
        localOnly = false
        offlineNoticePlayed = false
        pendingOfflineFile = null
        pendingOfflineIndex = -1
        if (itemId == null || sentences.isEmpty()) return
        if (player.playbackState == Player.STATE_ENDED || player.currentMediaItem == null) {
            if (resumeWaiting) generateAndPlayNext()
        } else prefetchFollowing(sentenceIndex + 1)
        PlaybackEvents.report("Voice updated. The next sentence uses your new selection.")
    }

    private fun bufferDepth(): Int = when {
        speechPreferences.getBoolean("offline_only", false) -> 1
        speechPreferences.getBoolean("online_primary", true) &&
            speechPreferences.getString("online_provider", "azure") == "azure" &&
            speechPreferences.getString("azure_voice", "").orEmpty().contains("DragonHD", true) -> 4
        else -> 2
    }

    private fun prepareSentence(index: Int): Deferred<GeneratedSentence> {
        val id = itemId.orEmpty()
        val text = sentences[index]
        return sentenceBuffer.get(index) { withSpeechCpuAwake {
            val task = kotlin.coroutines.coroutineContext[Job]
            val file = File(cacheDir, "speech/${id.hashCode()}/buffer-$index-${System.nanoTime()}.wav")
            GeneratedSentence(index, file, synthesize(text, file) { task?.ensureActive() })
        } }
    }

    private fun generateAndPlayNext() {
        val id = itemId ?: return
        if (isGenerating) return
        if (sentenceIndex >= sentences.size) {
            player.pause()
            PlaybackEvents.publish(null)
            scope.launch { withSpeechCpuAwake {
                withContext(Dispatchers.IO) {
                    dao.upsertProgress(dev.yomu.app.data.ReadingProgressEntity(itemId = id, sentenceIndex = sentences.size, characterOffset = 0, completed = true, lastListenedAt = System.currentTimeMillis()))
                    repository.completeItem(id)
                }
                getSharedPreferences("yomu_playback", MODE_PRIVATE).edit().remove("item_id").remove("sentence").apply()
                if (stopAtEnd) { stopAtEnd = false; itemId = null; return@withSpeechCpuAwake }
                val interrupted = playNowResume.takeAfterCompletion(id)
                persistPlayNowResumePoint()
                if (interrupted != null) playItem(interrupted.itemId, interrupted.sentenceIndex, skipReturnCapture = true)
                else {
                    val next = withContext(Dispatchers.IO) { dao.getUpNext().firstOrNull()?.itemId }
                    if (next != null) playItem(next, skipReturnCapture = true)
                }
            } }
            return
        }
        isGenerating = true
        playbackPhase = "Preparing"
        publishSnapshot()
        val index = sentenceIndex
        val epoch = sentenceBuffer.epoch
        sentenceBuffer.discardBefore(index)
        val prepared = prepareSentence(index)
        for (next in index + 1..minOf(sentences.lastIndex, index + bufferDepth())) prepareSentence(next)
        generationJob = scope.launch { withSpeechCpuAwake {
            try {
                val generated = prepared.await()
                if (epoch != sentenceBuffer.epoch || itemId != id) return@withSpeechCpuAwake
                startGeneratedPlayback(generated.file, generated.index, generated.offline, epoch)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (epoch == sentenceBuffer.epoch) {
                    playbackPhase = "Failed"
                    publishSnapshot()
                    PlaybackEvents.report("Could not start speech. Check your voice settings or install the offline voice in Settings.")
                    player.pause()
                }
            } finally { if (epoch == sentenceBuffer.epoch) isGenerating = false }
        } }
    }

    private suspend fun startGeneratedPlayback(file: File, index: Int, usedOffline: Boolean, epoch: Long) {
        localOnly = usedOffline
        if (usedOffline && !offlineNoticePlayed &&
            !getSharedPreferences("speech_preferences", MODE_PRIVATE).getBoolean("offline_only", false)) {
            // Announce the service loss once, then begin the already-prepared offline sentence.
            val notice = File(cacheDir, "speech/failover.wav")
            withContext(Dispatchers.IO) { synthesizeOffline("Service unavailable. Continuing with the offline voice.", notice) }
            if (epoch != sentenceBuffer.epoch) return
            pendingOfflineFile = file
            pendingOfflineIndex = index
            offlineNoticePlayed = true
            player.setMediaItem(mediaItem(notice, -1, "Offline voice"))
            player.prepare()
            player.play()
        } else playFile(file, index)
    }

    private fun prefetchFollowing(index: Int) {
        val id = itemId ?: return
        if (index !in sentences.indices) return
        val epoch = sentenceBuffer.epoch
        sentenceBuffer.discardBefore(sentenceIndex)
        val end = minOf(sentences.lastIndex, index + bufferDepth() - 1)
        val prepared = (index..end).map { it to prepareSentence(it) }
        scope.launch {
            for ((next, future) in prepared) {
                try {
                    val generated = future.await()
                    fun canAppend(): Boolean = sentenceBuffer.epoch == epoch && itemId == id &&
                        player.playbackState != Player.STATE_ENDED && next > sentenceIndex &&
                        player.mediaItemCount > 0 &&
                        player.getMediaItemAt(player.mediaItemCount - 1).mediaId == "$id:${next - 1}"
                    if (!canAppend()) continue
                    val needsNotice = generated.offline && !offlineNoticePlayed && !speechPreferences.getBoolean("offline_only", false)
                    if (needsNotice) {
                        val notice = File(cacheDir, "speech/failover-${System.nanoTime()}.wav")
                        withSpeechCpuAwake { withContext(Dispatchers.IO) {
                            synthesizeOffline("Service unavailable. Continuing with the offline voice.", notice)
                        } }
                        if (!canAppend()) continue
                        player.addMediaItems(listOf(mediaItem(notice, -1, "Offline voice"), mediaItem(generated.file, next, title)))
                        offlineNoticePlayed = true
                    } else player.addMediaItem(mediaItem(generated.file, next, title))
                    localOnly = generated.offline
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { break } // The ended-state path reports failure if this sentence is needed.
            }
        }
    }

    private fun playFile(file: File, index: Int) {
        player.setMediaItem(mediaItem(file, index, title))
        player.playbackParameters = PlaybackParameters(playbackSpeed)
        player.prepare()
        player.play()
        publishSnapshot()
        val id = itemId ?: return
        scope.launch(Dispatchers.IO) {
            dao.upsertProgress(dev.yomu.app.data.ReadingProgressEntity(itemId = id, sentenceIndex = index, characterOffset = characterOffset(index), lastListenedAt = System.currentTimeMillis()))
        }
        prefetchFollowing(index + 1)
        saveCheckpoint()
    }

    private data class GeneratedSentence(val index: Int, val file: File, val offline: Boolean)

    private fun saveCheckpoint() {
        getSharedPreferences("yomu_playback", MODE_PRIVATE).edit()
            .putString("item_id", itemId).putInt("sentence", sentenceIndex).apply()
    }

    private fun persistPlayNowResumePoint() {
        val point = playNowResume.point
        getSharedPreferences("yomu_playback", MODE_PRIVATE).edit().apply {
            if (point == null) remove("return_item_id").remove("return_sentence")
            else putString("return_item_id", point.itemId).putInt("return_sentence", point.sentenceIndex)
        }.apply()
    }

    private fun restorePlaybackCheckpoint() {
        val prefs = getSharedPreferences("yomu_playback", MODE_PRIVATE)
        val savedId = prefs.getString("item_id", null) ?: return
        val savedIndex = prefs.getInt("sentence", 0)
        scope.launch {
            val item = withContext(Dispatchers.IO) { dao.getItem(savedId) } ?: return@launch
            itemId = item.id; title = item.title; sourceText = item.normalizedText; sentences = splitSentences(sourceText)
            sentenceIndex = savedIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
            publishSnapshot()
            // Restore the listening position paused. Resume requires an explicit user play action.
        }
    }

    private fun characterOffset(index: Int): Int = dev.yomu.app.data.ReadingAnchor.offsets(sourceText, sentences).getOrNull(index) ?: sourceText.length

    private fun publishSnapshot() {
        val id = itemId ?: return
        PlaybackEvents.publish(PlaybackEvents.Snapshot(id, title, player.isPlaying, sentenceIndex, sentences.size,
            if (player.isPlaying) "Playing" else if (isGenerating) "Preparing" else playbackPhase.takeIf { it == "Failed" } ?: "Paused", sleepUntil, stopAtEnd))
    }

    @Suppress("MissingPermission")
    private fun registerPhoneStateMonitor() {
        if (telephonyManager != null || !getSharedPreferences("speech_preferences", MODE_PRIVATE).getBoolean("call_resume_enabled", true)) return
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager = manager
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onPhoneCallState(state)
            }
            callStateCallback = callback
            manager.registerTelephonyCallback(mainExecutor, callback)
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Used only on Android versions below 12")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onPhoneCallState(state)
            }
            legacyCallStateListener = listener
            @Suppress("DEPRECATION")
            manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun onPhoneCallState(state: Int) {
        val busy = state != TelephonyManager.CALL_STATE_IDLE
        if (busy && !callActive) {
            callActive = true
            resumeAfterCall = player.playWhenReady
            if (resumeAfterCall) { pausingForCall = true; player.pause() }
        } else if (!busy && callActive) {
            callActive = false
            pausingForCall = false
            if (resumeAfterCall && player.currentMediaItem != null) {
                callResumeAttempts = 0
                handler.removeCallbacks(callResumeRetry)
                handler.postDelayed(callResumeRetry, CALL_RESUME_RETRY_MS)
            } else resumeAfterCall = false
        }
    }

    private fun unregisterPhoneStateMonitor() {
        val manager = telephonyManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= 31) callStateCallback?.let(manager::unregisterTelephonyCallback)
        else legacyCallStateListener?.let {
            @Suppress("DEPRECATION")
            manager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        telephonyManager = null; callStateCallback = null; legacyCallStateListener = null
    }

    private fun mediaItem(file: File, index: Int, mediaTitle: String) = MediaItem.Builder()
        .setMediaId("${itemId.orEmpty()}:$index")
        .setUri(file.toURI().toString())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(mediaTitle).setArtist("Yomu").build())
        .build()

    private fun synthesize(text: String, file: File, checkActive: () -> Unit = {}): Boolean {
        checkActive()
        val prefs = getSharedPreferences("speech_preferences", MODE_PRIVATE)
        val offlineOnly = prefs.getBoolean("offline_only", false)
        if (offlineOnly) {
            check(installer.isInstalled()) { "The Kokoro offline voice is not installed." }
            synthesizeOffline(text, file, prefs.getInt("kokoro_voice", 7))
            return true
        }
        val onlineFirst = prefs.getBoolean("online_primary", true)
        val azureVoice = prefs.getString("azure_voice", "en-GB-SoniaNeural") ?: "en-GB-SoniaNeural"
        val retryOnlineAtSentenceBoundary = prefs.getBoolean("try_online_after_failover", false)
        if (onlineFirst && (!localOnly || retryOnlineAtSentenceBoundary)) {
            val primary = prefs.getString("online_provider", "azure") ?: "azure"
            val order = if (prefs.getBoolean("online_fallback_enabled", true)) listOf(primary, if (primary == "azure") "elevenlabs" else "azure") else listOf(primary)
            for (providerId in order) {
                val succeeded = when (providerId) {
                    "azure" -> vault.credentials()?.let { credentials -> runCatching {
                        AzureRestSpeechProvider(credentials, speechHttpClient).synthesize(SpeechSynthesisRequest(text, azureVoice, file))
                    }.isSuccess } ?: false
                    "elevenlabs" -> {
                        val tier = prefs.getString("elevenlabs_tier", "").orEmpty()
                        val mayUse = tier.equals("free", true) || tier.startsWith("free_", true) || prefs.getBoolean("allow_paid_elevenlabs", false)
                        val key = elevenLabsVault.key()
                        val voice = prefs.getString("elevenlabs_voice", "").orEmpty()
                        if (mayUse && tier.isNotBlank() && key != null && voice.isNotBlank()) {
                            val model = prefs.getString("elevenlabs_model", ElevenLabsRestSpeechProvider.MODEL_ID)
                                ?: ElevenLabsRestSpeechProvider.MODEL_ID
                            val attempt = runCatching {
                                ElevenLabsRestSpeechProvider(key, speechHttpClient).synthesize(SpeechSynthesisRequest(text, voice, file, model))
                            }
                            attempt.exceptionOrNull()?.message?.let { reason ->
                                if (reason != lastElevenLabsFailure) {
                                    lastElevenLabsFailure = reason
                                    PlaybackEvents.report("ElevenLabs voice failed: $reason")
                                }
                            }
                            attempt.isSuccess
                        } else false
                    }
                    else -> false
                }
                checkActive()
                if (succeeded) return false
            }
        }
        check(installer.isInstalled()) { "No working online voice and the offline voice is not installed." }
        val voiceId = prefs.getInt("kokoro_voice", 7)
        synthesizeOffline(text, file, voiceId)
        return true
    }

    private fun synthesizeOffline(text: String, file: File, voiceId: Int = getSharedPreferences("speech_preferences", MODE_PRIVATE).getInt("kokoro_voice", 7)) {
        file.parentFile?.mkdirs()
        // Keep Kokoro warm for this playback session. Recreating OfflineTts for every sentence
        // reloads the model and creates an audible pause between each generated audio chunk.
        synchronized(kokoroProviderLock) { playbackKokoroProvider().generate(text, voiceId, 1f).save(file) }
    }

    private fun playbackKokoroProvider(): SherpaKokoroEspeakProvider = synchronized(kokoroProviderLock) {
        kokoroProvider ?: SherpaKokoroEspeakProvider(installer.modelDirectory, 2).also { kokoroProvider = it }
    }

    private inner class SessionCallbacks : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            mediaLibrarySession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = handleGetLibraryRoot(mediaLibrarySession, browser, params)

        override fun onGetChildren(
            mediaLibrarySession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> =
            handleGetChildren(mediaLibrarySession, browser, parentId, page, pageSize, params)

        override fun onGetItem(
            mediaLibrarySession: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = handleGetItem(mediaLibrarySession, browser, mediaId)

        override fun onConnect(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val nextCommand = SessionCommand(COMMAND_NEXT, Bundle.EMPTY)
            val previousCommand = SessionCommand(COMMAND_PREVIOUS, Bundle.EMPTY)
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(nextCommand).add(previousCommand).build()
            val playerCommands = mediaSession.player.availableCommands.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onPlayerCommandRequest(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int = when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                sentenceIndex = (sentenceIndex + 1).coerceAtMost(sentences.size)
                player.stop()
                generateAndPlayNext()
                SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                sentenceIndex = (sentenceIndex - 1).coerceAtLeast(0)
                player.stop()
                generateAndPlayNext()
                SessionResult.RESULT_SUCCESS
            }
            else -> super.onPlayerCommandRequest(mediaSession, controller, playerCommand)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requested = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val requestedId = requested?.mediaId?.takeIf { it.startsWith(CAR_ITEM_PREFIX) }
                ?.removePrefix(CAR_ITEM_PREFIX)
            if (requestedId == null) {
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }

            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    player.pause()
                    invalidatePreparedSpeech()
                    val selected = withContext(Dispatchers.IO) { dao.getItem(requestedId) }
                        ?: error("This reading is no longer in Yomu.")
                    val selectedSentences = splitSentences(selected.normalizedText)
                    check(selectedSentences.isNotEmpty()) { "This reading has no text to speak." }
                    val progress = withContext(Dispatchers.IO) { dao.getProgress(selected.id) }
                    val selectedIndex = if (progress?.completed == true) 0
                    else (progress?.sentenceIndex ?: 0).coerceIn(selectedSentences.indices)

                    playNowResume.onStart(itemId, sentenceIndex, selected.id)
                    persistPlayNowResumePoint()
                    itemId = selected.id
                    title = selected.title
                    sentences = selectedSentences
                    sourceText = selected.normalizedText
                    sentenceIndex = selectedIndex
                    offlineNoticePlayed = false
                    playbackSpeed = getSharedPreferences("speech_preferences", MODE_PRIVATE)
                        .getFloat("playback_speed", 1f).coerceIn(.7f, 2f)
                    player.playbackParameters = PlaybackParameters(playbackSpeed)

                    val audio = File(cacheDir, "speech/${selected.id.hashCode()}/auto-$selectedIndex-${System.currentTimeMillis()}.wav")
                    audio.parentFile?.mkdirs()
                    val usedOffline = withContext(Dispatchers.IO) { synthesize(sentences[selectedIndex], audio) }
                    localOnly = usedOffline
                    pendingOfflineFile = null
                    pendingOfflineIndex = -1
                    val firstItem = if (usedOffline) {
                        val notice = File(cacheDir, "speech/failover-auto.wav")
                        withContext(Dispatchers.IO) {
                            synthesizeOffline("Online speech is unavailable. Continuing with your offline voice.", notice)
                        }
                        pendingOfflineFile = audio
                        pendingOfflineIndex = selectedIndex
                        offlineNoticePlayed = true
                        mediaItem(notice, -1, "Offline voice")
                    } else mediaItem(audio, selectedIndex, selected.title)

                    withContext(Dispatchers.IO) {
                        dao.upsertProgress(dev.yomu.app.data.ReadingProgressEntity(
                            itemId = selected.id,
                            sentenceIndex = selectedIndex,
                            characterOffset = characterOffset(selectedIndex),
                            lastListenedAt = System.currentTimeMillis(),
                        ))
                    }
                    publishSnapshot()
                    saveCheckpoint()
                    result.set(MediaSession.MediaItemsWithStartPosition(listOf(firstItem), 0, 0L))
                } catch (error: Exception) {
                    result.setException(error)
                }
            }
            return result
        }

        @androidx.media3.common.util.UnstableApi
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val prefs = getSharedPreferences("yomu_playback", MODE_PRIVATE)
                    val savedId = prefs.getString("item_id", null) ?: error("There is no saved Yomu reading to resume.")
                    val item = withContext(Dispatchers.IO) { dao.getItem(savedId) }
                        ?: error("The saved Yomu reading is no longer available.")
                    val restoredSentences = splitSentences(item.normalizedText)
                    check(restoredSentences.isNotEmpty()) { "The saved reading has no text to resume." }
                    val restoredIndex = prefs.getInt("sentence", 0).coerceIn(restoredSentences.indices)
                    itemId = item.id
                    title = item.title
                    sentences = restoredSentences
                    sourceText = item.normalizedText
                    sentenceIndex = restoredIndex
                    playbackSpeed = getSharedPreferences("speech_preferences", MODE_PRIVATE)
                        .getFloat("playback_speed", 1f).coerceIn(.7f, 2f)
                    player.playbackParameters = PlaybackParameters(playbackSpeed)

                    val mediaItems = if (!isForPlayback) {
                        listOf(MediaItem.Builder().setMediaId("${item.id}:$restoredIndex")
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist("Yomu").build()).build())
                    } else {
                        val audio = File(cacheDir, "speech/${item.id.hashCode()}/resume-$restoredIndex-${System.currentTimeMillis()}.wav")
                        val usedOffline = withContext(Dispatchers.IO) { synthesize(restoredSentences[restoredIndex], audio) }
                        localOnly = usedOffline
                        if (usedOffline) {
                            offlineNoticePlayed = true
                            val notice = File(cacheDir, "speech/failover-resume.wav")
                            withContext(Dispatchers.IO) { synthesizeOffline("Online speech is unavailable. Continuing with your offline voice.", notice) }
                            pendingOfflineFile = audio
                            pendingOfflineIndex = restoredIndex
                            listOf(mediaItem(notice, -1, "Offline voice"))
                        } else listOf(mediaItem(audio, restoredIndex, item.title))
                    }
                    publishSnapshot()
                    result.set(MediaSession.MediaItemsWithStartPosition(mediaItems, 0, 0L))
                } catch (error: Exception) {
                    result.setException(error)
                }
            }
            return result
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_PLAY_ITEM -> args.getString(EXTRA_ITEM_ID)?.let { playItem(it) }
                COMMAND_NEXT -> { sentenceIndex = (sentenceIndex + 1).coerceAtMost(sentences.size); player.stop(); generateAndPlayNext() }
                COMMAND_PREVIOUS -> { sentenceIndex = (sentenceIndex - 1).coerceAtLeast(0); player.stop(); generateAndPlayNext() }
                COMMAND_SEEK_SENTENCE -> args.getInt(EXTRA_SENTENCE, -1).takeIf { it >= 0 }?.let { playItem(itemId ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)), it) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onDestroy() {
        speechPreferences.unregisterOnSharedPreferenceChangeListener(voiceListener)
        sentenceBuffer.reset()
        speechHttpClient.dispatcher.cancelAll()
        saveCheckpoint()
        handler.removeCallbacksAndMessages(null)
        unregisterPhoneStateMonitor()
        synchronized(kokoroProviderLock) { kokoroProvider?.close(); kokoroProvider = null }
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val VOICE_PREFERENCE_KEYS = setOf("azure_voice", "elevenlabs_voice", "elevenlabs_model", "kokoro_voice", "online_provider", "online_primary", "online_fallback_enabled", "offline_only", "try_online_after_failover", "allow_paid_elevenlabs")
        private const val CAR_ROOT_ID = "yomu:root"
        private const val CAR_TO_READ_ID = "yomu:to-read"
        private const val CAR_UP_NEXT_ID = "yomu:up-next"
        private const val CAR_PLAYLISTS_ID = "yomu:playlists"
        private const val CAR_PLAYLIST_PREFIX = "yomu:playlist:"
        private const val CAR_ITEM_PREFIX = "yomu:item:"
        private const val CAR_HOST_PACKAGE = "com.google.android.projection.gearhead"
        const val ACTION_PLAY_ITEM = "dev.yomu.app.playback.PLAY_ITEM"
        const val ACTION_STOP = "dev.yomu.app.playback.STOP"
        const val ACTION_TOGGLE = "dev.yomu.app.playback.TOGGLE"
        const val ACTION_NEXT = "dev.yomu.app.playback.NEXT"
        const val ACTION_PREVIOUS = "dev.yomu.app.playback.PREVIOUS"
        const val ACTION_SEEK = "dev.yomu.app.playback.SEEK"
        const val ACTION_SPEED = "dev.yomu.app.playback.SPEED"
        const val ACTION_SLEEP = "dev.yomu.app.playback.SLEEP"
        const val ACTION_ENABLE_CALL_MONITOR = "dev.yomu.app.playback.ENABLE_CALL_MONITOR"
        const val ACTION_DISABLE_CALL_MONITOR = "dev.yomu.app.playback.DISABLE_CALL_MONITOR"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_SENTENCE = "sentence"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_MINUTES = "minutes"
        const val CALL_RESUME_RETRY_MS = 750L
        const val MAX_CALL_RESUME_ATTEMPTS = 12
        const val COMMAND_PLAY_ITEM = "dev.yomu.app.PLAY_ITEM"
        const val COMMAND_NEXT = "dev.yomu.app.NEXT_SENTENCE"
        const val COMMAND_PREVIOUS = "dev.yomu.app.PREVIOUS_SENTENCE"
        const val COMMAND_SEEK_SENTENCE = "dev.yomu.app.SEEK_SENTENCE"

        private fun splitSentences(text: String): List<String> {
            val iterator = BreakIterator.getSentenceInstance(Locale.US).apply { setText(text) }
            val output = mutableListOf<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(output::add)
                start = end; end = iterator.next()
            }
            if (output.isEmpty() && text.isNotBlank()) output += text.trim()
            return output
        }
    }
}
