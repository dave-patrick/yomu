package dev.yomu.app
import androidx.compose.material.icons.filled.Email

import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.graphics.Typeface
import android.graphics.Color as PlatformColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.documentfile.provider.DocumentFile
import dev.yomu.app.data.HistoryRow
import dev.yomu.app.data.PlaylistEntity
import dev.yomu.app.data.ReadingItemEntity
import dev.yomu.app.data.ReadingListRow
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    private fun startLibrarySync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        val lastSync = getSharedPreferences("sync_state", 0).getLong("lastSync", 0)
                        if (dev.yomu.app.sync.SyncStatusStore.get(applicationContext).canRetryAutomatically() &&
                            dev.yomu.app.sync.SyncOptions(applicationContext).isDue(lastSync))
                            dev.yomu.app.sync.LibrarySync(applicationContext).sync()
                    }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) { /* Offline edits remain pending for the next attempt. */ }
                    delay(30_000)
                }
            }
        }
    }
    private var pendingShare by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startLibrarySync()
        pendingShare = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
        setContent { YomuApp(sharedIntent = pendingShare, onShareConsumed = { pendingShare = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
    }
}

@Composable
private fun YomuBrandSplash() {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF090B10)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.yomu_icon),
                contentDescription = "Yomu book and headphones logo",
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.yomu_wordmark),
                contentDescription = "Yomu wordmark",
                modifier = Modifier.fillMaxWidth().height(92.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your reading, ready to listen.",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD4DCE9),
            )
        }
    }
}

private val accentOptions = listOf(
    "Blue" to Color(0xFF3E8BFF), "Cyan" to Color(0xFF38BDF8), "Teal" to Color(0xFF28C7B7),
    "Violet" to Color(0xFF9B8CFF), "Rose" to Color(0xFFFF7892), "Amber" to Color(0xFFFFBD4A),
)

private data class OfflineVoiceOption(val id: Int, val name: String, val accent: String, val gender: String)
private val kokoroVoices = listOf(
    OfflineVoiceOption(0, "American Female 1", "American", "Female"),
    OfflineVoiceOption(1, "Bella", "American", "Female"),
    OfflineVoiceOption(2, "Nicole", "American", "Female"),
    OfflineVoiceOption(3, "Sarah", "American", "Female"),
    OfflineVoiceOption(4, "Sky", "American", "Female"),
    OfflineVoiceOption(5, "Adam", "American", "Male"),
    OfflineVoiceOption(6, "Michael", "American", "Male"),
    OfflineVoiceOption(7, "Emma", "British", "Female"),
    OfflineVoiceOption(8, "Isabella", "British", "Female"),
    OfflineVoiceOption(9, "George", "British", "Male"),
    OfflineVoiceOption(10, "Lewis", "British", "Male"),
)

private val atkinsonHyperlegibleFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)
private val lexendFamily = FontFamily(Font(R.font.lexend, FontWeight.Normal))
private val literataFamily = FontFamily(Font(R.font.literata, FontWeight.Normal))
private val loraFamily = FontFamily(Font(R.font.lora, FontWeight.Normal))
private val sourceSans3Family = FontFamily(Font(R.font.source_sans_3, FontWeight.Normal))
private val sourceSerif4Family = FontFamily(Font(R.font.source_serif_4, FontWeight.Normal))

private val readerFontOptions = listOf(
    "Atkinson Hyperlegible", "Casual", "Cursive", "Default app font", "Lexend", "Literata", "Lora",
    "Monospace", "Sans serif black", "Sans serif condensed", "Sans serif light", "Sans serif medium",
    "Serif", "Source Sans 3", "Source Serif 4",
)

private fun normalizeFontOption(name: String): String = when (name) {
    "Sans serif", "System default (phone app font)" -> "Default app font"
    "System serif" -> "Serif"
    "System monospace" -> "Monospace"
    "System cursive" -> "Cursive"
    "System casual" -> "Casual"
    "System sans black" -> "Sans serif black"
    "System sans condensed" -> "Sans serif condensed"
    "System sans light" -> "Sans serif light"
    "System sans medium" -> "Sans serif medium"
    else -> name
}

private fun readerFontFamily(name: String): FontFamily = when (name) {
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    "Cursive" -> FontFamily.Cursive
    "Casual" -> FontFamily(typeface = Typeface.create("casual", Typeface.NORMAL))
    "Sans serif condensed" -> FontFamily(typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    "Sans serif light" -> FontFamily(typeface = Typeface.create("sans-serif-light", Typeface.NORMAL))
    "Sans serif medium" -> FontFamily(typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL))
    "Sans serif black" -> FontFamily(typeface = Typeface.create("sans-serif-black", Typeface.NORMAL))
    "Atkinson Hyperlegible" -> atkinsonHyperlegibleFamily
    "Lexend" -> lexendFamily
    "Literata" -> literataFamily
    "Lora" -> loraFamily
    "Source Sans 3" -> sourceSans3Family
    "Source Serif 4" -> sourceSerif4Family
    else -> FontFamily.SansSerif
}

private fun readerFontDescription(name: String): String = when (name) {
    "Atkinson Hyperlegible" -> "Clear, distinct letterforms designed for easier reading."
    "Lexend" -> "An open, spacious sans serif with a steady rhythm."
    "Literata" -> "A book-focused serif with a refined, traditional feel."
    "Lora" -> "A warm, balanced serif for long-form reading."
    "Source Sans 3" -> "A clean, neutral sans serif built for screen text."
    "Source Serif 4" -> "A sturdy editorial serif designed for comfortable text."
    "Casual" -> "A casual display font supplied by Android."
    "Cursive" -> "A cursive font supplied by the phone."
    "Monospace" -> "A fixed-width font supplied by the phone."
    "Sans serif condensed" -> "A narrower sans serif that fits more text per line."
    "Sans serif light" -> "A lighter-weight system sans serif."
    "Sans serif medium" -> "A medium-weight system sans serif."
    "Sans serif black" -> "A heavier system sans serif."
    "Serif" -> "A serif font supplied by the phone."
    else -> "Follows the font selected for apps in Samsung/Android system settings."
}

private fun appTypography(family: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun YomuApp(sharedIntent: Intent?, onShareConsumed: () -> Unit, vm: LibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    val libraryPrefs = remember { context.getSharedPreferences("library_preferences", 0) }
    val speechPrefs = remember { context.getSharedPreferences("speech_preferences", 0) }
    val contentPrefs = remember { context.getSharedPreferences("content_preferences", 0) }
    var exportTreeUri by rememberSaveable { mutableStateOf(prefs.getString("export_tree_uri", null)) }
    val exportFolderDisplayName = remember(exportTreeUri) {
        exportTreeUri?.let { raw -> runCatching { DocumentFile.fromTreeUri(context, Uri.parse(raw))?.name }.getOrNull() }
    }
    var callPermissionGranted by remember { mutableStateOf(context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var callRecoveryEnabled by rememberSaveable { mutableStateOf(speechPrefs.getBoolean("call_resume_enabled", callPermissionGranted)) }
    var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme_mode", null) ?: if (prefs.getBoolean("dark", true)) "dark" else "light") }
    val dark = themeMode == "dark" || (themeMode == "system" && isSystemInDarkTheme())
    var accentIndex by rememberSaveable { mutableStateOf(prefs.getInt("accent", 0).coerceIn(accentOptions.indices)) }
    var customHex by rememberSaveable { mutableStateOf(prefs.getString("customHex", "") ?: "") }
    var hexDraft by rememberSaveable { mutableStateOf(customHex) }
    var readerTextScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("reader_text_scale", 1f).coerceIn(.8f, 1.6f)) }
    var readerSpacing by rememberSaveable { mutableStateOf(prefs.getString("reader_spacing", "Comfortable") ?: "Comfortable") }
    var readerFont by rememberSaveable { mutableStateOf(normalizeFontOption(prefs.getString("reader_font", "Default app font") ?: "Default app font")) }
    var appFont by rememberSaveable { mutableStateOf(normalizeFontOption(prefs.getString("app_font", "Default app font") ?: "Default app font")) }
    var readerHighlight by rememberSaveable { mutableStateOf(prefs.getString("reader_highlight", "Background") ?: "Background") }
    var durationDisplay by rememberSaveable { mutableStateOf(prefs.getString("duration_display", "Elapsed + remaining") ?: "Elapsed + remaining") }
    var listSpacing by rememberSaveable { mutableStateOf(libraryPrefs.getString("list_spacing", "Comfortable") ?: "Comfortable") }
    val listSpacingDp = when (listSpacing) { "Compact" -> 2; "Relaxed" -> 10; else -> 5 }
    var toReadSortMode by rememberSaveable { mutableStateOf(libraryPrefs.getString("to_read_sort", "Newest") ?: "Newest") }
    var upNextSortMode by rememberSaveable { mutableStateOf(libraryPrefs.getString("up_next_sort", "Manual") ?: "Manual") }
    val enqueueAndResetQueue: (String) -> Unit = { itemId ->
        vm.enqueue(itemId)
        upNextSortMode = "Manual"
        libraryPrefs.edit().putString("up_next_sort", "Manual").apply()
    }
    var selectedAzureVoice by rememberSaveable { mutableStateOf(speechPrefs.getString("azure_voice", "en-GB-SoniaNeural") ?: "en-GB-SoniaNeural") }
    var selectedElevenLabsVoice by rememberSaveable { mutableStateOf(speechPrefs.getString("elevenlabs_voice", "") ?: "") }
    var selectedOfflineVoice by rememberSaveable { mutableStateOf(speechPrefs.getInt("kokoro_voice", 7)) }
    var retryOnlineAfterFailover by rememberSaveable { mutableStateOf(speechPrefs.getBoolean("try_online_after_failover", false)) }
    var onlineProvider by rememberSaveable { mutableStateOf(speechPrefs.getString("online_provider", "azure") ?: "azure") }
    var onlineFallbackEnabled by rememberSaveable { mutableStateOf(speechPrefs.getBoolean("online_fallback_enabled", true)) }
    var offlineOnly by rememberSaveable { mutableStateOf(speechPrefs.getBoolean("offline_only", false)) }
    var allowPaidElevenLabs by rememberSaveable { mutableStateOf(speechPrefs.getBoolean("allow_paid_elevenlabs", false)) }
    var swipeRightAction by rememberSaveable { mutableStateOf(libraryPrefs.getString("swipe_right_action", "Add to Up next") ?: "Add to Up next") }
    var swipeLeftAction by rememberSaveable { mutableStateOf(libraryPrefs.getString("swipe_left_action", "Add to playlist") ?: "Add to playlist") }
    var readFootnotes by rememberSaveable { mutableStateOf(contentPrefs.getBoolean("read_footnotes", true)) }
    val accent = remember(accentIndex, customHex) { parseHex(customHex) ?: accentOptions[accentIndex].second }
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF07101D), background = Color(0xFF090B10),
        surface = Color(0xFF11151C), surfaceVariant = Color(0xFF1C222C), onSurface = Color(0xFFF3F5F8),
        onSurfaceVariant = Color(0xFFB8C0CC), secondary = accent.copy(alpha = .82f),
        secondaryContainer = accent.copy(alpha = .22f).compositeOver(Color(0xFF11151C)), onSecondaryContainer = accent,
        outline = Color(0xFF39414D),
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, background = Color(0xFFF6F8FB),
        surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8EDF5), onSurface = Color(0xFF161B24),
        onSurfaceVariant = Color(0xFF505B69), secondary = accent,
        secondaryContainer = accent.copy(alpha = .16f).compositeOver(Color(0xFFF6F8FB)), onSecondaryContainer = Color(0xFF07101D),
        outline = Color(0xFFCDD3DD),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = appTypography(readerFontFamily(appFont)),
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(20.dp),
            extraSmall = RoundedCornerShape(16.dp),
        ),
    ) {
        val toRead by vm.toRead.collectAsStateWithLifecycle()
        val archived by vm.archived.collectAsStateWithLifecycle()
        val upNext by vm.upNext.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        val playlistItems by vm.playlistItems.collectAsStateWithLifecycle()
        val offlineVoiceReady by vm.offlineVoiceReady.collectAsStateWithLifecycle()
        val modelInstallProgress by vm.modelInstallProgress.collectAsStateWithLifecycle()
        val azureKeySaved by vm.azureKeySaved.collectAsStateWithLifecycle()
        val azureRegion by vm.azureRegion.collectAsStateWithLifecycle()
        val azureVoices by vm.azureVoices.collectAsStateWithLifecycle()
        val elevenLabsKeySaved by vm.elevenLabsKeySaved.collectAsStateWithLifecycle()
        val elevenLabsVoices by vm.elevenLabsVoices.collectAsStateWithLifecycle()
        val elevenLabsUsage by vm.elevenLabsUsage.collectAsStateWithLifecycle()
        val previewPath by vm.previewPath.collectAsStateWithLifecycle()
        val status by vm.status.collectAsStateWithLifecycle()
        val exportStatus by vm.exportStatus.collectAsStateWithLifecycle()
        val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
        val diagnosticEvents by vm.diagnosticEvents.collectAsStateWithLifecycle()
        val reading by vm.reading.collectAsStateWithLifecycle()
        val playback by vm.playback.collectAsStateWithLifecycle()
        val playbackSpeed by vm.playbackSpeed.collectAsStateWithLifecycle()
        var destination by rememberSaveable { mutableStateOf(Destination.TO_READ) }
        var navigationHistory by remember { mutableStateOf(emptyList<Destination>()) }
        var showBrandSplash by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(1_400)
            showBrandSplash = false
        }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        var libraryQuery by rememberSaveable(destination) { mutableStateOf("") }
        if (showSearch) AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Search your library") },
            text = { OutlinedTextField(libraryQuery, { libraryQuery = it }, label = { Text("Title, text, source or tag") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { libraryQuery = "" }) { Text("Clear") } },
        )
        val showInbox = destination == Destination.INBOX
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showAbout by rememberSaveable { mutableStateOf(false) }
        var showAdd by rememberSaveable { mutableStateOf(false) }
        var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
        var showClearHistory by rememberSaveable { mutableStateOf(false) }
        var deleteItemPending by remember { mutableStateOf<ReadingListRow?>(null) }
        var pendingBackupRestore by remember { mutableStateOf<Uri?>(null) }
        var playlistItemPending by remember { mutableStateOf<ReadingListRow?>(null) }
        var pdfPasswordPending by remember { mutableStateOf<ReadingListRow?>(null) }
        var pdfPassword by rememberSaveable { mutableStateOf("") }
        var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
        BackHandler(enabled = showInbox || reading != null || selectedPlaylist != null || showAbout || showSettings || navigationHistory.isNotEmpty()) {
            when {
                reading != null -> vm.closeReader()
                selectedPlaylist != null -> selectedPlaylist = null
                showAbout -> { showAbout = false; showSettings = true }
                showSettings -> showSettings = false
                navigationHistory.isNotEmpty() -> {
                    destination = navigationHistory.last()
                    navigationHistory = navigationHistory.dropLast(1)
                }
            }
        }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) vm.importUris(uris)
        }
        val mp3Export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
            val item = reading
            if (uri != null && item != null) vm.exportMp3(item, uri)
        }
        val requestCallPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callPermissionGranted = granted
            callRecoveryEnabled = granted
            speechPrefs.edit().putBoolean("call_resume_enabled", granted).apply()
            if (granted) vm.enableCallRecovery()
        }
        val chooseExportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                exportTreeUri = uri.toString()
                prefs.edit().putString("export_tree_uri", exportTreeUri).apply()
            }
        }
        val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) vm.exportBackup(uri)
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingBackupRestore = uri }
        val opmlExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
            if (uri != null) vm.exportOpml(uri)
        }
        val opmlImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importOpml(uri)
        }

        LaunchedEffect(sharedIntent) {
            if (sharedIntent != null) {
                vm.importIntent(sharedIntent)
                onShareConsumed()
            }
        }
        LaunchedEffect(status) {
            status?.let { message ->
                snackbar.showSnackbar(message)
                if (message.startsWith("This share") || message.contains("could not") || message.contains("HTTP")) Unit
            }
        }
        LaunchedEffect(exportStatus) { exportStatus?.let { snackbar.showSnackbar(it) } }
        LaunchedEffect(backupStatus) { backupStatus?.let { snackbar.showSnackbar(it) } }

        if (showBrandSplash) {
            YomuBrandSplash()
        } else Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = .16f).compositeOver(MaterialTheme.colorScheme.background),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primary.copy(alpha = .09f).compositeOver(MaterialTheme.colorScheme.background)))),
            contentWindowInsets = WindowInsets.navigationBars,
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (!showInbox && reading == null && !showSettings && !showAbout && selectedPlaylist == null) {
                    FloatingActionButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add a link or document")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    expandedHeight = 112.dp,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .88f)),
                    title = {
                        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Image(painter = painterResource(R.drawable.yomu_icon), contentDescription = "Yomu book and headphones logo",
                                    modifier = Modifier.size(90.dp), contentScale = ContentScale.Fit)
                                Image(painter = painterResource(R.drawable.yomu_wordmark), contentDescription = "Yomu wordmark",
                                    modifier = Modifier.width(190.dp).height(64.dp), contentScale = ContentScale.Fit)
                            }
                            if (reading != null || showSettings || showAbout || selectedPlaylist != null || destination != Destination.TO_READ) {
                                Text(if (showInbox) "Inbox" else if (reading != null) reading!!.title else if (showAbout) "About" else if (showSettings) "Settings" else selectedPlaylist?.name ?: destination.title,
                                    modifier = if (reading != null) Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = if (reading != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall,
                                    fontWeight = if (reading != null) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        if (showInbox || reading != null || selectedPlaylist != null || showSettings || showAbout) IconButton(onClick = {
                            when { reading != null -> vm.closeReader(); showInbox -> { destination = navigationHistory.lastOrNull() ?: Destination.TO_READ; navigationHistory = navigationHistory.dropLast(1) }; selectedPlaylist != null -> selectedPlaylist = null; showAbout -> { showAbout = false; showSettings = true }; else -> showSettings = false }
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        if (!showSettings && !showAbout && reading == null && selectedPlaylist == null) {
                            if (destination == Destination.TO_READ || destination == Destination.UP_NEXT) {
                                IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, contentDescription = "Search your library", tint = if (libraryQuery.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = { if (destination != Destination.HISTORY) navigationHistory = navigationHistory + destination; destination = Destination.HISTORY }) { Icon(Icons.Default.History, contentDescription = "Read history") }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                        } else if (showSettings) {
                            IconButton(onClick = {
                                themeMode = when (themeMode) { "system" -> "light"; "light" -> "dark"; else -> "system" }
                                prefs.edit().putString("theme_mode", themeMode).putBoolean("dark", themeMode != "light").apply()
                            }) {
                                Icon(Icons.Default.WbSunny, contentDescription = "Cycle system, light, and dark themes")
                            }
                        } else if (reading != null) {
                            ArticleActions(reading!!.id, reading!!.title, vm)
                        } else if (selectedPlaylist != null) {
                            var playlistMenu by remember { mutableStateOf(false) }
                            var renamePlaylist by remember { mutableStateOf(false) }
                            var deletePlaylist by remember { mutableStateOf(false) }
                            val playlist = selectedPlaylist!!
                            if (renamePlaylist) NameDialog("Rename playlist", "Save", { renamePlaylist = false }, { vm.renamePlaylist(playlist.id, it); renamePlaylist = false })
                            if (deletePlaylist) AlertDialog(onDismissRequest = { deletePlaylist = false }, title = { Text("Delete playlist?") },
                                text = { Text("Saved articles stay in your library. Only this playlist is removed.") },
                                confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlist.id); deletePlaylist = false; selectedPlaylist = null }) { Text("Delete playlist") } },
                                dismissButton = { TextButton(onClick = { deletePlaylist = false }) { Text("Cancel") } })
                            Box {
                                IconButton(onClick = { playlistMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Playlist actions") }
                                DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                                    DropdownMenuItem(text = { Text("Rename playlist") }, onClick = { playlistMenu = false; renamePlaylist = true })
                                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { playlistMenu = false; deletePlaylist = true })
                                    DropdownMenuItem(text = { Text("Back to playlists") }, onClick = { playlistMenu = false; selectedPlaylist = null })
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 8.dp)) },
            bottomBar = {
                if (reading == null && !showSettings && !showAbout && selectedPlaylist == null) {
                    Column {
                        playback?.let { state ->
                            CompactPlayer(
                                state = state,
                                onOpen = { vm.openReader(state.itemId) },
                                onToggle = vm::togglePlayback,
                                onPrevious = vm::previousSentence,
                                onNext = vm::nextSentence,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .90f)) {
                            Destination.entries.filter { it != Destination.HISTORY }.forEach { page ->
                                NavigationBarItem(
                                    selected = destination == page,
                                    onClick = {
                                        if (destination != page) navigationHistory = navigationHistory + destination
                                        destination = page
                                    },
                                    icon = { Icon(page.icon, contentDescription = null) },
                                    label = { Text(page.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
                    },
        ) { padding ->
            when {
                reading != null -> androidx.compose.runtime.key(reading!!.id) { ReaderScreen(reading!!, vm, onClose = vm::closeReader,
                    onExportMp3 = { name ->
                        val folder = exportTreeUri?.let(Uri::parse)
                        if (folder == null) mp3Export.launch(name) else vm.exportMp3ToFolder(reading!!, folder, name)
                    }, exportStatus = exportStatus, cancelExport = vm::cancelMp3Export,
                    readerTextScale = readerTextScale, readerSpacing = readerSpacing, readerHighlight = readerHighlight, readerFont = readerFont,
                    durationDisplay = durationDisplay, modifier = Modifier.padding(padding)) }
                showAbout -> AboutScreen(modifier = Modifier.padding(padding))
                showSettings -> SettingsScreen(themeMode, { value -> themeMode = value; prefs.edit().putString("theme_mode", value).putBoolean("dark", value != "light").apply() }, accentIndex,
                    { index -> accentIndex = index; customHex = ""; prefs.edit().putInt("accent", index).remove("customHex").apply() },
                    hexDraft, { hexDraft = it }, {
                        parseHex(hexDraft)?.let { color ->
                            customHex = hexDraft.trim(); prefs.edit().putString("customHex", customHex).apply()
                            accentIndex = 0
                        } ?: scope.launch { snackbar.showSnackbar("Enter a color like #3E8BFF") }
                    }, readerTextScale, { value -> readerTextScale = value; prefs.edit().putFloat("reader_text_scale", value).apply() },
                    readerSpacing, { value -> readerSpacing = value; prefs.edit().putString("reader_spacing", value).apply() },
                    readerFont, { value -> readerFont = value; prefs.edit().putString("reader_font", value).apply() },
                    appFont, { value -> appFont = value; prefs.edit().putString("app_font", value).apply() },
                    readerHighlight, { value -> readerHighlight = value; prefs.edit().putString("reader_highlight", value).apply() },
                    durationDisplay, { value -> durationDisplay = value; prefs.edit().putString("duration_display", value).apply() },
                    listSpacing, { value -> listSpacing = value; libraryPrefs.edit().putString("list_spacing", value).apply() },
                    offlineVoiceReady, modelInstallProgress, vm::installOfflineVoice,
                    azureKeySaved, azureRegion, azureVoices, previewPath,
                    vm::saveAzureSettings, vm::loadAzureVoices, vm::auditionAzureVoice, vm::clearAzureSettings,
                    selectedAzureVoice, { voiceId -> selectedAzureVoice = voiceId; speechPrefs.edit().putString("azure_voice", voiceId).apply() },
                    selectedOfflineVoice, { voiceId -> selectedOfflineVoice = voiceId; speechPrefs.edit().putInt("kokoro_voice", voiceId).apply() }, vm::auditionOfflineVoice,
                    vm::stopVoicePreview,
                    retryOnlineAfterFailover, { value -> retryOnlineAfterFailover = value; speechPrefs.edit().putBoolean("try_online_after_failover", value).apply() },
                    onlineProvider, { value -> onlineProvider = value; speechPrefs.edit().putString("online_provider", value).apply() },
                    onlineFallbackEnabled, { value -> onlineFallbackEnabled = value; speechPrefs.edit().putBoolean("online_fallback_enabled", value).apply() },
                    allowPaidElevenLabs, { value -> allowPaidElevenLabs = value; speechPrefs.edit().putBoolean("allow_paid_elevenlabs", value).apply() },
                    elevenLabsKeySaved, elevenLabsVoices, elevenLabsUsage, vm::saveElevenLabsKey, vm::loadElevenLabsCatalog,
                    vm::clearElevenLabsKey, vm::auditionElevenLabsVoice,
                    selectedElevenLabsVoice, speechPrefs.getString("elevenlabs_model", null), { voiceId ->
                        selectedElevenLabsVoice = voiceId
                        val modelId = elevenLabsVoices.firstOrNull { it.id == voiceId }?.recommendedModelId
                        speechPrefs.edit().putString("elevenlabs_voice", voiceId).apply {
                            if (!modelId.isNullOrBlank()) putString("elevenlabs_model", modelId)
                        }
                    },
                    callRecoveryEnabled, { enabled ->
                        if (enabled && !callPermissionGranted) requestCallPermission.launch(android.Manifest.permission.READ_PHONE_STATE)
                        else {
                            callRecoveryEnabled = enabled
                            speechPrefs.edit().putBoolean("call_resume_enabled", enabled).apply()
                            if (enabled) vm.enableCallRecovery() else vm.disableCallRecovery()
                        }
                    },
                    exportFolderDisplayName, { chooseExportFolder.launch(null) },
                    { backupLauncher.launch("Yomu-backup.zip") }, { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    { opmlExportLauncher.launch("Yomu-links.opml") }, { opmlImportLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "application/octet-stream")) },
                    readFootnotes, { value -> readFootnotes = value; contentPrefs.edit().putBoolean("read_footnotes", value).apply() },
                    offlineOnly, { value -> offlineOnly = value; speechPrefs.edit().putBoolean("offline_only", value).apply(); if (value) vm.stopVoicePreview() },
                    swipeRightAction, { value -> swipeRightAction = value; libraryPrefs.edit().putString("swipe_right_action", value).apply() },
                    swipeLeftAction, { value -> swipeLeftAction = value; libraryPrefs.edit().putString("swipe_left_action", value).apply() },
                    playbackSpeed, vm::setPlaybackSpeed,
                    diagnosticEvents, vm::clearDiagnosticEvents,
                    mobileTools = { MobileHealthTools(vm) },
                    onOpenAbout = { showSettings = false; showAbout = true },
                    modifier = Modifier.padding(padding))
                selectedPlaylist != null -> PlaylistDetail(selectedPlaylist!!, playlistItems, vm, enqueueAndResetQueue, listSpacingDp, modifier = Modifier.padding(padding))
                else -> when (destination) {
                    Destination.TO_READ -> LibraryList(toRead, emptyLabel = "Nothing waiting to be read", hint = "Share a public article, PDF, or Word document to start your library.", tapHint = "Tap to play · hold to add to Up next", onTap = { row ->
                        if (row.importState == "failed") {
                            if (row.sourceType == "pdf") { pdfPasswordPending = row; pdfPassword = "" }
                            else vm.retryImport(row.id)
                        } else vm.startNow(row.id)
                    }, onPlayNow = { row ->
                        if (row.importState == "failed") {
                            if (row.sourceType == "pdf") { pdfPasswordPending = row; pdfPassword = "" }
                            else vm.retryImport(row.id)
                        } else { enqueueAndResetQueue(row.id); scope.launch { snackbar.showSnackbar("Added to Up next") } }
                    }, onRemoveQueue = {}, onSelectPlaylist = { playlistItemPending = it }, modifier = Modifier.padding(padding),
                        sortMode = toReadSortMode,
                        onSortModeChange = { mode, _ -> toReadSortMode = mode; libraryPrefs.edit().putString("to_read_sort", mode).apply() },
                        externalQuery = libraryQuery, vm = vm, showSortPicker = true, allowReorder = toReadSortMode == "Manual",
                        onMoveItemUp = { vm.moveToReadItem(it.id, -1) }, onMoveItemDown = { vm.moveToReadItem(it.id, 1) },
                        showDurations = false, swipeRightAction = swipeRightAction, swipeLeftAction = swipeLeftAction, itemSpacingDp = listSpacingDp,
                        archiveActionEnabled = true, readStateActionEnabled = true,
                        onMenuPlay = { vm.startNow(it.id) }, longPressLabel = "Add to Up next",
                        onMarkStateChange = { if (it.completed) vm.markItemUnread(it.id) else vm.markItemRead(it.id) },
                        onSwipeAction = { row, action -> if (action == "Delete") deleteItemPending = row else handleLibrarySwipe(action, row, vm, enqueueAndResetQueue) { playlistItemPending = row } })
                    Destination.UP_NEXT -> LibraryList(upNext, emptyLabel = "Up next is clear", hint = "Hold an item in To read to add it here. Tap a queued item to play.", onTap = { if (it.importState == "failed") vm.retryImport(it.id) else vm.startNow(it.id) }, onPlayNow = { if (it.importState == "failed") vm.retryImport(it.id) else vm.startNow(it.id) }, onRemoveQueue = { vm.removeFromQueue(it.id) }, onSelectPlaylist = { playlistItemPending = it }, modifier = Modifier.padding(padding), queueMode = true,
                        sortMode = upNextSortMode,
                        onSortModeChange = { mode, sorted -> upNextSortMode = mode; libraryPrefs.edit().putString("up_next_sort", mode).apply(); vm.reorderQueue(sorted.map { it.id }) },
                        externalQuery = libraryQuery, vm = vm, showSortPicker = true, allowReorder = upNextSortMode == "Manual", swipeRightAction = swipeRightAction, swipeLeftAction = swipeLeftAction,
                        onMoveItemUp = { vm.moveQueueItem(it.id, -1) }, onMoveItemDown = { vm.moveQueueItem(it.id, 1) }, itemSpacingDp = listSpacingDp,
                        archiveActionEnabled = true, readStateActionEnabled = true,
                        onMarkStateChange = { if (it.completed) vm.markItemUnread(it.id) else vm.markItemRead(it.id) },
                        onSwipeAction = { row, action -> if (action == "Delete") deleteItemPending = row else handleLibrarySwipe(action, row, vm, enqueueAndResetQueue) { playlistItemPending = row } })
                    Destination.PLAYLISTS -> PlaylistList(playlists, vm = vm, onCreate = { showNewPlaylist = true }, onSelect = { selectedPlaylist = it; vm.openPlaylist(it.id) }, modifier = Modifier.padding(padding))
                    Destination.INBOX -> NewsletterInbox(Modifier.padding(padding))
                    Destination.HISTORY -> HistoryList(history, onRemove = vm::deleteHistory, onMarkUnread = vm::markItemUnread, onClear = { showClearHistory = true }, modifier = Modifier.padding(padding))
                    Destination.ARCHIVE -> ArchiveScreen(archived, onPlay = { vm.startNow(it.id) }, onRestore = vm::restoreArchivedItem,
                        onDelete = { deleteItemPending = it },
                        onMarkRead = vm::markItemRead, onMarkUnread = vm::markItemUnread, modifier = Modifier.padding(padding))
                }
            }
        }

        if (showAdd) AddContentSheet(onDismiss = { showAdd = false }, onUrl = { url -> showAdd = false; vm.importUrl(url) }, onFiles = { showAdd = false; picker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain")) })
        pdfPasswordPending?.let { item ->
            AlertDialog(
                onDismissRequest = { pdfPasswordPending = null; pdfPassword = "" },
                containerColor = frostedDialogContainerColor(),
                title = { Text("Unlock PDF") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Enter the document password. Yomu uses it only to extract text and does not save it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = pdfPassword, onValueChange = { pdfPassword = it }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), label = { Text("PDF password") })
                    }
                },
                confirmButton = {
                    TextButton(enabled = pdfPassword.isNotEmpty(), onClick = {
                        vm.retryPdfPassword(item.id, pdfPassword)
                        pdfPassword = ""; pdfPasswordPending = null
                    }) { Text("Unlock") }
                },
                dismissButton = { TextButton(onClick = { pdfPasswordPending = null; pdfPassword = "" }) { Text("Cancel") } },
            )
        }
        if (showNewPlaylist) NameDialog("New playlist", "Create", onDismiss = { showNewPlaylist = false }, onConfirm = { name -> vm.createPlaylist(name); showNewPlaylist = false })
        if (playlistItemPending != null) AlertDialog(onDismissRequest = { playlistItemPending = null }, containerColor = frostedDialogContainerColor(), title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) Text("Create a playlist first, then add reading items from this menu.")
                else Column { playlists.forEach { playlist -> TextButton(onClick = { vm.addToPlaylist(playlist.id, playlistItemPending!!.id); playlistItemPending = null }) { Text(playlist.name) } } }
            }, confirmButton = { TextButton(onClick = { playlistItemPending = null }) { Text("Close") } })
        if (showClearHistory) AlertDialog(onDismissRequest = { showClearHistory = false }, containerColor = frostedDialogContainerColor(), title = { Text("Clear read history?") }, text = { Text("This removes all history entries. Saved items and playlists are kept.") }, confirmButton = { TextButton(onClick = { vm.clearHistory(); showClearHistory = false }) { Text("Clear history") } }, dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Cancel") } })
        deleteItemPending?.let { item -> AlertDialog(
            onDismissRequest = { deleteItemPending = null },
            containerColor = frostedDialogContainerColor(),
            title = { Text("Delete saved reading?") },
            text = { Text("${item.title} will be removed from To read, Up next, Archive, playlists, and history. You can restore it from Deleted items in Settings until you permanently remove it.") },
            confirmButton = { TextButton(onClick = { vm.deleteReadingItem(item.id); deleteItemPending = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteItemPending = null }) { Text("Cancel") } },
        ) }
        pendingBackupRestore?.let { backupUri -> AlertDialog(
            onDismissRequest = { pendingBackupRestore = null }, title = { Text("Replace this Yomu library?") },
            containerColor = frostedDialogContainerColor(),
            text = { Text("Restore saved items, playlists, reading positions, history, and appearance from this backup. This replaces the current library. Yomu will close after a successful restore; reopen it to use the restored library. Provider API keys are not included.") },
            confirmButton = { TextButton(onClick = { pendingBackupRestore = null; vm.restoreBackup(backupUri) }) { Text("Restore and close") } },
            dismissButton = { TextButton(onClick = { pendingBackupRestore = null }) { Text("Cancel") } },
        ) }
    }
}

private enum class Destination(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TO_READ("To read", Icons.Default.Bookmarks), UP_NEXT("Up next", Icons.Default.QueueMusic),
    PLAYLISTS("Playlists", Icons.Default.Bookmarks), INBOX("Inbox", Icons.Default.Email), HISTORY("History", Icons.Default.History), ARCHIVE("Archive", Icons.Default.Archive)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LibraryList(
    rows: List<ReadingListRow>, emptyLabel: String, hint: String,
    onTap: (ReadingListRow) -> Unit, onPlayNow: (ReadingListRow) -> Unit,
    onRemoveQueue: (ReadingListRow) -> Unit, onSelectPlaylist: (ReadingListRow) -> Unit,
    modifier: Modifier = Modifier, queueMode: Boolean = false,
    removeLabel: String = "Remove from Up next", collectionLabel: String? = null,
    sortMode: String = "Manual", onSortModeChange: (String, List<ReadingListRow>) -> Unit = { _, _ -> },
    showSortPicker: Boolean = false, allowReorder: Boolean = false,
    onMoveItemUp: (ReadingListRow) -> Unit = {}, onMoveItemDown: (ReadingListRow) -> Unit = {},
    showDurations: Boolean = true,
    swipeRightAction: String = "Off", swipeLeftAction: String = "Off",
    tapHint: String? = null,
    itemSpacingDp: Int = 5,
    archiveActionEnabled: Boolean = false,
    readStateActionEnabled: Boolean = false,
    onSwipeAction: (ReadingListRow, String) -> Unit = { _, _ -> },
    onMarkStateChange: (ReadingListRow) -> Unit = {},
    onMenuPlay: (ReadingListRow) -> Unit = onPlayNow,
    longPressLabel: String = "Play now",
    vm: LibraryViewModel? = null,
    externalQuery: String? = null,
) {
    val effectiveSortMode = if (!showDurations && sortMode in setOf("Shortest", "Longest")) "Newest" else sortMode
    val allItems = if (vm != null) vm.allItems.collectAsStateWithLifecycle().value else emptyList()
    val memberships = if (vm != null) vm.memberships.collectAsStateWithLifecycle().value else emptyList()
    val playlists = if (vm != null) vm.playlists.collectAsStateWithLifecycle().value else emptyList()
    var localQuery by rememberSaveable { mutableStateOf("") }
    val query = externalQuery ?: localQuery
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf("All types") }
    var stateFilter by rememberSaveable { mutableStateOf("All states") }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var bulkPlaylist by remember { mutableStateOf(false) }
    val corpus = remember(allItems) { allItems.associateBy { it.id } }
    val shownRows = remember(rows, effectiveSortMode, queueMode, query, typeFilter, stateFilter, allItems) {
        sortLibraryRows(rows.filter { row ->
            val item = corpus[row.id]
            (typeFilter == "All types" || row.sourceType.equals(typeFilter, true)) &&
            (stateFilter == "All states" || when(stateFilter) { "Ready" -> row.importState == "ready"; "Needs attention" -> row.importState != "ready"; "Partly heard" -> row.sentenceIndex > 0; "Read" -> row.completed; else -> !row.completed }) &&
            (query.isBlank() || listOf(row.title, row.sourceName.orEmpty(), item?.normalizedText.orEmpty(), item?.tags.orEmpty()).any { it.contains(query.trim(), true) })
        }, effectiveSortMode, queueMode)
    }
    LaunchedEffect(rows) { selected = selected.intersect(rows.map { it.id }.toSet()) }
    if (confirmBulkDelete) AlertDialog(onDismissRequest = { confirmBulkDelete = false }, title = { Text("Delete ${selected.size} items?") },
        text = { Text("Items leave the library on connected devices. You can restore them from Deleted items on this phone until you permanently remove them.") },
        confirmButton = { TextButton(onClick = { vm?.bulkAction(selected, "Delete"); selected = emptySet(); confirmBulkDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel") } })
    if (bulkPlaylist) AlertDialog(onDismissRequest = { bulkPlaylist = false }, title = { Text("Add ${selected.size} items to playlist") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { playlists.forEach { playlist -> TextButton(onClick = { vm?.bulkAction(selected, "Playlist", playlist.id); selected = emptySet(); bulkPlaylist = false }) { Text(playlist.name) } } } },
        confirmButton = { TextButton(onClick = { bulkPlaylist = false }) { Text("Close") } })
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filter your library", style = MaterialTheme.typography.headlineSmall)
            LibraryFilterChoices("Document type", listOf("All types", "Articles", "PDF", "Word (.docx)", "Word (.doc)", "Text"), when(typeFilter) { "url" -> "Articles"; "pdf" -> "PDF"; "docx" -> "Word (.docx)"; "doc" -> "Word (.doc)"; "text" -> "Text"; else -> "All types" }) {
                typeFilter = when(it) { "Articles" -> "url"; "PDF" -> "pdf"; "Word (.docx)" -> "docx"; "Word (.doc)" -> "doc"; "Text" -> "text"; else -> "All types" }
            }
            LibraryFilterChoices("Reading status", listOf("All states", "Ready", "Needs attention", "Partly heard", "Read", "Unread"), stateFilter) { stateFilter = it }
            if (showSortPicker) LibraryFilterChoices("Sort by", if (showDurations) librarySortOptions else listOf("Manual", "Newest", "Oldest", "Title A–Z"), effectiveSortMode) {
                onSortModeChange(it, sortLibraryRows(rows, it, queueMode))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { typeFilter = "All types"; stateFilter = "All states" }) { Text("Reset filters") }
                Button(onClick = { showFilters = false }) { Text("Show ${shownRows.size} items") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(collectionLabel ?: if (queueMode) "Your listening order" else "Saved for when you have a moment", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tapHint ?: if (queueMode) "Tap to play · hold to play now" else "Tap to queue · hold to play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val totalMinutes = shownRows.sumOf { it.estimatedSecondsAtOneX } / 60
            if (showDurations && shownRows.isNotEmpty()) Text("${shownRows.size} items  ·  ${formatDuration(totalMinutes * 60)} total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (vm != null) {
                if (externalQuery == null) OutlinedTextField(query, { localQuery = it }, label = { Text("Search title, text, source or tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else if (query.isNotBlank()) Text("Search: $query", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter and sort library", tint = if (typeFilter != "All types" || stateFilter != "All states") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(listOfNotNull(typeFilter.takeUnless { it == "All types" }?.let { when(it) { "url" -> "Articles"; "docx" -> "Word (.docx)"; "doc" -> "Word (.doc)"; else -> it.uppercase() } }, stateFilter.takeUnless { it == "All states" }, effectiveSortMode.takeIf { showSortPicker }).joinToString(" · "), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { selectionMode = !selectionMode; selected = emptySet() }) { Text(if (selectionMode) "Done selecting" else "Select items") }
                }
                if (selectionMode) {
                    Text("${selected.size} selected", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { selected = shownRows.map { it.id }.toSet() }) { Text("Select visible") }
                        for (action in listOf("Queue", "Archive", "Read", "Playlist", "Delete")) TextButton(enabled = selected.isNotEmpty(), onClick = {
                            when(action) { "Delete" -> confirmBulkDelete = true; "Playlist" -> bulkPlaylist = true; else -> { vm.bulkAction(selected, action); selected = emptySet() } }
                        }) { Text(action) }
                    }
                }
            }

        }
        if (shownRows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(if (queueMode) Icons.Default.QueueMusic else Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(18.dp))
                Text(emptyLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp)) {
                items(shownRows, key = { it.id }) { row ->
                    if (selectionMode) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + row.id else selected - row.id })
                        Text(row.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else ReadingItemCard(row, queueMode, removeLabel, allowReorder, showDurations, swipeRightAction, swipeLeftAction, itemSpacingDp,
                        onTap = { onTap(row) }, onPlayNow = { onPlayNow(row) }, onRemoveQueue = { onRemoveQueue(row) },
                        onSelectPlaylist = { onSelectPlaylist(row) }, onMoveUp = { onMoveItemUp(row) }, onMoveDown = { onMoveItemDown(row) },
                        onSwipeAction = { onSwipeAction(row, it) }, onMarkStateChange = { onMarkStateChange(row) },
                        archiveActionEnabled = archiveActionEnabled, readStateActionEnabled = readStateActionEnabled,
                        onMenuPlayNow = { onMenuPlay(row) }, longPressLabel = longPressLabel,
                        onReadQuietly = { vm?.readQuietly(row.id) }, quietAvailable = vm != null,
                        membershipLabel = memberships.filter { it.itemId == row.id }.mapNotNull { membership -> playlists.find { it.id == membership.playlistId }?.name }.joinToString(", "),
                        onDelete = if (vm != null) ({ vm.deleteReadingItem(row.id) }) else null)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterChoices(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) }) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingItemCard(row: ReadingListRow, queueMode: Boolean, removeLabel: String, allowReorder: Boolean, showDuration: Boolean,
                           swipeRightAction: String, swipeLeftAction: String, itemSpacingDp: Int,
                           onTap: () -> Unit, onPlayNow: () -> Unit, onRemoveQueue: () -> Unit, onSelectPlaylist: () -> Unit,
                           onMoveUp: () -> Unit, onMoveDown: () -> Unit, onSwipeAction: (String) -> Unit,
                            onMarkStateChange: () -> Unit, archiveActionEnabled: Boolean, readStateActionEnabled: Boolean,
                            onMenuPlayNow: () -> Unit = onPlayNow, longPressLabel: String = "Play now",
                            onReadQuietly: () -> Unit = {}, quietAvailable: Boolean = false, membershipLabel: String = "",
                            onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) DeleteArticleDialog(row.title, { confirmDelete = false }) { confirmDelete = false; onDelete?.invoke() }
    val ready = row.importState == "ready"
    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { rowWidth -> rowWidth * 0.55f },
    )
    LaunchedEffect(swipeState.settledValue) {
        val action = when (swipeState.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
            SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
            SwipeToDismissBoxValue.Settled -> "Off"
        }
        if (action != "Off") onSwipeAction(action)
        if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) swipeState.reset()
    }
    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.fillMaxWidth().padding(vertical = itemSpacingDp.dp),
        enableDismissFromStartToEnd = swipeRightAction != "Off",
        enableDismissFromEndToStart = swipeLeftAction != "Off",
        backgroundContent = {
            val action = when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
                SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
                SwipeToDismissBoxValue.Settled -> ""
            }
            val destructive = action == "Delete"
            val actionColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            val actionIcon = when (action) {
                "Delete" -> Icons.Default.DeleteOutline
                "Add to playlist" -> Icons.Default.Add
                "Play now" -> Icons.Default.PlayArrow
                "Add to Up next" -> Icons.Default.QueueMusic
                "Archive" -> Icons.Default.Archive
                else -> null
            }
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                .background(when {
                    action.isBlank() || action == "Off" -> Color.Transparent
                    destructive -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                })
                .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start) {
                if (action.isNotBlank() && action != "Off") {
                    actionIcon?.let { Icon(it, contentDescription = null, tint = actionColor) }
                    Spacer(Modifier.width(8.dp))
                    Text(action, color = actionColor, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
      Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(
            enabled = ready || row.importState == "failed",
            role = Role.Button,
            onClick = onTap,
            onLongClick = onPlayNow,
            onLongClickLabel = longPressLabel,
        ).frostedGlass(),
        colors = frostedCardColors(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressTrail(row.sentenceIndex > 0, row.completed)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(row.sourceName ?: row.sourceType.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.sourceType == "newsletter") Text(newsletterArrival(row.importedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.importState != "ready") {
                    Spacer(Modifier.height(6.dp))
                    Text(row.warning ?: when (row.importState) { "failed" -> if (row.sourceType == "url") "Import failed · tap to retry" else "Import failed · share the document again to retry"; else -> "Preparing text…" }, style = MaterialTheme.typography.bodySmall, color = if (row.importState == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                if (membershipLabel.isNotBlank()) Text("Playlists: $membershipLabel", style = MaterialTheme.typography.labelSmall)
                if (row.importState == "ready" && row.sentenceIndex > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Partly heard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (showDuration) Text(formatDuration(row.estimatedSecondsAtOneX), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${row.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp),
                        containerColor = menuSurfaceColor(), tonalElevation = 10.dp,
                        modifier = Modifier.frostedGlass(RoundedCornerShape(20.dp))) {
                        if (quietAvailable) DropdownMenuItem(text = { Text("Read quietly") }, enabled = ready, onClick = { menu = false; onReadQuietly() })
                        DropdownMenuItem(text = { Text("Play now") }, enabled = ready, onClick = { menu = false; onMenuPlayNow() })
                        if (readStateActionEnabled) DropdownMenuItem(text = { Text(if (row.completed) "Mark as unread" else "Mark as read") }, onClick = { menu = false; onMarkStateChange() })
                        if (archiveActionEnabled) DropdownMenuItem(text = { Text("Move to Archive") }, onClick = { menu = false; onSwipeAction("Archive") })
                        if (queueMode) DropdownMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemoveQueue() })
                        if (allowReorder) {
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMoveUp() })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMoveDown() })
                        }
                        DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { menu = false; onSelectPlaylist() })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Delete article", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
      }
    }
}

private val swipeActionOptions = listOf("Off", "Add to Up next", "Play now", "Add to playlist", "Archive", "Delete")

private fun handleLibrarySwipe(action: String, row: ReadingListRow, vm: LibraryViewModel,
                               enqueue: (String) -> Unit, addToPlaylist: () -> Unit) {
    when (action) {
        "Add to Up next" -> if (row.importState == "ready") enqueue(row.id) else vm.retryImport(row.id)
        "Play now" -> if (row.importState == "ready") vm.startNow(row.id) else vm.retryImport(row.id)
        "Add to playlist" -> addToPlaylist()
        "Archive" -> vm.archiveReadingItem(row.id)
    }
}

private val librarySortOptions = listOf("Manual", "Newest", "Oldest", "Title A–Z", "Shortest", "Longest")

internal fun sortLibraryRows(rows: List<ReadingListRow>, mode: String, queueMode: Boolean): List<ReadingListRow> = when (mode) {
    "Title A–Z" -> rows.sortedBy { it.title.lowercase() }
    "Newest" -> rows.sortedByDescending { it.importedAt }
    "Oldest" -> rows.sortedBy { it.importedAt }
    "Shortest" -> rows.sortedWith(compareBy<ReadingListRow> { it.estimatedSecondsAtOneX }.thenByDescending { it.importedAt })
    "Longest" -> rows.sortedWith(compareByDescending<ReadingListRow> { it.estimatedSecondsAtOneX }.thenByDescending { it.importedAt })
    else -> if (queueMode) rows else rows.sortedWith(compareByDescending<ReadingListRow> { it.manualOrder }.thenByDescending { it.importedAt })
}

@Composable
private fun ProgressTrail(heard: Boolean, completed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.width(12.dp).height(48.dp)) {
        Box(Modifier.width(2.dp).weight(1f).background(if (heard || completed) MaterialTheme.colorScheme.primary.copy(alpha = .65f) else MaterialTheme.colorScheme.outline.copy(alpha = .45f)))
        Box(Modifier.size(if (heard || completed) 10.dp else 8.dp).clip(CircleShape).background(if (heard || completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outline.copy(alpha = .45f)))
    }
}

@Composable
private fun PlaylistList(items: List<PlaylistEntity>, vm: LibraryViewModel, onCreate: () -> Unit, onSelect: (PlaylistEntity) -> Unit, modifier: Modifier = Modifier) {
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keep longer reads together", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New") }
        }
        if (items.isEmpty()) EmptyState("No playlists yet", "Create a playlist for things you want to get to someday.", modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(items, key = { it.id }) { playlist ->
                Card(onClick = { onSelect(playlist) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).frostedGlass(), colors = frostedCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmarks, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(playlist.name, style = MaterialTheme.typography.titleMedium); Text("${memberships.count { it.playlistId == playlist.id }} items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Column {
                            TextButton(enabled = playlist.id != items.first().id, onClick = { vm.movePlaylist(playlist.id, -1) }) { Text("Move up") }
                            TextButton(enabled = playlist.id != items.last().id, onClick = { vm.movePlaylist(playlist.id, 1) }) { Text("Move down") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(playlist: PlaylistEntity, rows: List<ReadingListRow>, vm: LibraryViewModel, enqueue: (String) -> Unit, itemSpacingDp: Int, modifier: Modifier = Modifier) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val current = playlists.find { it.id == playlist.id }
    var rename by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    if (rename) NameDialog("Rename playlist", "Save", { rename = false }, { vm.renamePlaylist(playlist.id, it); rename = false })
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Delete playlist?") }, text = { Text("Saved articles stay in your library. Only this playlist and its memberships are removed.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlist.id); remove = false }) { Text("Delete playlist") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } })
    Column(modifier.fillMaxSize()) {
        Text(current?.name ?: "Playlist deleted", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        if (current != null) Row { TextButton(onClick = { rename = true }) { Text("Rename") }; TextButton(onClick = { remove = true }) { Text("Delete playlist") } }
        LibraryList(rows, "This playlist is empty", "Add items from To read using the item menu.", onTap = { vm.startNow(it.id) }, onPlayNow = { vm.startNow(it.id) }, onRemoveQueue = { vm.removeFromPlaylist(playlist.id, it.id) }, onSelectPlaylist = {}, modifier = Modifier.weight(1f), queueMode = true, removeLabel = "Remove from playlist", collectionLabel = "${rows.size} items", allowReorder = true, itemSpacingDp = itemSpacingDp,
            onMoveItemUp = { vm.movePlaylistItem(playlist.id, it.id, -1) }, onMoveItemDown = { vm.movePlaylistItem(playlist.id, it.id, 1) }, vm = vm)
    }
}

@Composable
private fun HistoryList(rows: List<HistoryRow>, onRemove: (String) -> Unit, onMarkUnread: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Finished listening", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rows.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        if (rows.isEmpty()) EmptyState("No read history yet", "Finished items will be kept here. You can remove entries or clear history any time.", Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            items(rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).frostedGlass(), colors = frostedCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressTrail(true, true); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text("${formatDuration(row.estimatedSecondsAtOneX)} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(row.completedAt))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onMarkUnread(row.itemId) }) { Text("Unread") }
                        IconButton(onClick = { onRemove(row.id) }) { Icon(Icons.Default.DeleteOutline, "Remove from history") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveScreen(
    rows: List<ReadingListRow>,
    onPlay: (ReadingListRow) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (ReadingListRow) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkUnread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text("Saved out of the active reading list. Restore an item to return it to To read.",
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isEmpty()) EmptyState("Archive is empty", "Move an item here when you want to keep it without seeing it in To read.", Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            items(rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).frostedGlass(), colors = frostedCardColors()) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("${row.sourceName ?: row.sourceType.uppercase()} · ${formatDuration(row.estimatedSecondsAtOneX)} · ${if (row.completed) "Read" else "Unread"}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { onPlay(row) }) { Text("Play") }
                            Button(onClick = { onRestore(row.id) }) { Text("Restore") }
                            TextButton(onClick = { if (row.completed) onMarkUnread(row.id) else onMarkRead(row.id) }) {
                                Text(if (row.completed) "Mark unread" else "Mark read")
                            }
                        }
                        TextButton(onClick = { onDelete(row) }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(themeMode: String, setThemeMode: (String) -> Unit, selectedAccent: Int, setAccent: (Int) -> Unit,
                           hex: String, setHex: (String) -> Unit, applyHex: () -> Unit,
                           readerTextScale: Float, setReaderTextScale: (Float) -> Unit,
                            readerSpacing: String, setReaderSpacing: (String) -> Unit,
                            readerFont: String, setReaderFont: (String) -> Unit,
                            appFont: String, setAppFont: (String) -> Unit,
                            readerHighlight: String, setReaderHighlight: (String) -> Unit,
                           durationDisplay: String, setDurationDisplay: (String) -> Unit,
                           listSpacing: String, setListSpacing: (String) -> Unit,
                           offlineVoiceReady: Boolean, modelProgress: dev.yomu.app.speech.ModelInstallProgress?, installOffline: () -> Unit,
                           azureKeySaved: Boolean, savedAzureRegion: String, azureVoices: List<dev.yomu.app.speech.SpeechVoice>, previewPath: String?,
                           saveAzure: (String, String) -> Unit, loadAzureVoices: () -> Unit, auditionVoice: (String) -> Unit, clearAzure: () -> Unit,
                           selectedAzureVoice: String, selectAzureVoice: (String) -> Unit,
                           selectedOfflineVoice: Int, selectOfflineVoice: (Int) -> Unit, auditionOfflineVoice: (Int) -> Unit,
                           stopVoicePreview: () -> Unit,
                           retryOnlineAfterFailover: Boolean, setRetryOnlineAfterFailover: (Boolean) -> Unit,
                           onlineProvider: String, setOnlineProvider: (String) -> Unit,
                           onlineFallbackEnabled: Boolean, setOnlineFallbackEnabled: (Boolean) -> Unit,
                           allowPaidElevenLabs: Boolean, setAllowPaidElevenLabs: (Boolean) -> Unit,
                           elevenLabsKeySaved: Boolean, elevenLabsVoices: List<dev.yomu.app.speech.SpeechVoice>,
                           elevenLabsUsage: dev.yomu.app.speech.ElevenLabsUsage?, saveElevenLabsKey: (String) -> Unit,
                           loadElevenLabsCatalog: () -> Unit, clearElevenLabsKey: () -> Unit,
                           auditionElevenLabsVoice: (String) -> Unit, selectedElevenLabsVoice: String,
                           selectedElevenLabsModel: String?,
                           selectElevenLabsVoice: (String) -> Unit,
                           callRecoveryEnabled: Boolean, setCallRecoveryEnabled: (Boolean) -> Unit,
                           exportFolderDisplayName: String?, chooseExportFolder: () -> Unit,
                           createBackup: () -> Unit, chooseBackupToRestore: () -> Unit,
                           exportOpml: () -> Unit, importOpml: () -> Unit,
                           readFootnotes: Boolean, setReadFootnotes: (Boolean) -> Unit,
                           offlineOnly: Boolean, setOfflineOnly: (Boolean) -> Unit,
                           swipeRightAction: String, setSwipeRightAction: (String) -> Unit,
                           swipeLeftAction: String, setSwipeLeftAction: (String) -> Unit,
                           playbackSpeed: Float, setPlaybackSpeed: (Float) -> Unit,
                           diagnosticEvents: List<AppDiagnostics.Entry>, clearDiagnosticEvents: () -> Unit,
                           mobileTools: @Composable () -> Unit = {},
                           onOpenAbout: () -> Unit = {},
                           modifier: Modifier = Modifier) {
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var voicesExpanded by rememberSaveable { mutableStateOf(false) }
    var defaultsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var azureKey by rememberSaveable { mutableStateOf("") }
    var elevenLabsKey by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(savedAzureRegion) }
    var voiceFilter by rememberSaveable { mutableStateOf("") }
    var azureLocaleFilter by rememberSaveable { mutableStateOf("English · all accents") }
    var azureGenderFilter by rememberSaveable { mutableStateOf("Any gender") }
    var azureQualityFilter by rememberSaveable { mutableStateOf("Any quality") }
    var azureStyleFilter by rememberSaveable { mutableStateOf("Any style") }
    var elevenLabsVoiceFilter by rememberSaveable { mutableStateOf("") }
    var elevenAccentFilter by rememberSaveable { mutableStateOf("Any accent") }
    var elevenLocaleFilter by rememberSaveable { mutableStateOf("English · all accents") }
    var elevenGenderFilter by rememberSaveable { mutableStateOf("Any gender") }
    var elevenAgeFilter by rememberSaveable { mutableStateOf("Any age") }
    var elevenTypeFilter by rememberSaveable { mutableStateOf("Any voice type") }
    var elevenStyleFilter by rememberSaveable { mutableStateOf("Any style") }
    var offlineVoicesExpanded by rememberSaveable { mutableStateOf(false) }
    var azureVoicesExpanded by rememberSaveable { mutableStateOf(false) }
    var elevenLabsVoicesExpanded by rememberSaveable { mutableStateOf(false) }
    var activePreviewPath by remember { mutableStateOf<String?>(null) }
    DisposableEffect(previewPath) {
        val player = previewPath?.let { path ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    setDataSource(path)
                    setOnPreparedListener { it.start(); activePreviewPath = path }
                    setOnCompletionListener { activePreviewPath = null; stopVoicePreview() }
                    setOnErrorListener { _, _, _ -> activePreviewPath = null; stopVoicePreview(); true }
                    prepareAsync()
                }
            }.getOrNull()
        }
        onDispose {
            if (activePreviewPath == previewPath) activePreviewPath = null
            runCatching { player?.release() }
        }
    }
    val azureLocales = remember(azureVoices) {
        azureVoices.map { it.localeName?.let { name -> "$name (${it.locale})" } ?: it.locale }
            .filter(String::isNotBlank).distinct().sorted()
    }
    val azureStyles = remember(azureVoices) { azureVoices.flatMap { it.styles }.distinct().sorted() }
    val azureQualities = listOf("Any quality", "Neural HD", "Neural", "Standard")
    val azureLocaleOptions = listOf("English · all accents", "All languages") + azureLocales
    val azureGenderOptions = listOf("Any gender") + azureVoices.mapNotNull { it.gender }.distinct().sorted()
    val azureStyleOptions = listOf("Any style") + azureStyles
    val filteredVoices = remember(azureVoices, voiceFilter, azureLocaleFilter, azureGenderFilter, azureQualityFilter, azureStyleFilter) {
        azureVoices.filter { voice ->
            val localeText = voice.localeName?.let { "$it (${voice.locale})" } ?: voice.locale
            val matchesSearch = voiceFilter.isBlank() || listOf(voice.name, voice.id, voice.locale, voice.localeName.orEmpty(), voice.gender.orEmpty(), voice.styles.joinToString(" "))
                .any { it.contains(voiceFilter, true) }
            val matchesLocale = when (azureLocaleFilter) {
                "English · all accents" -> voice.locale.startsWith("en-", true)
                "All languages" -> true
                else -> localeText == azureLocaleFilter
            }
            val matchesGender = azureGenderFilter == "Any gender" || voice.gender.equals(azureGenderFilter, true)
            val matchesQuality = azureQualityFilter == "Any quality" || voice.azureQualityCategory() == azureQualityFilter
            val matchesStyle = azureStyleFilter == "Any style" || voice.styles.any { it.equals(azureStyleFilter, true) }
            matchesSearch && matchesLocale && matchesGender && matchesQuality && matchesStyle
        }
    }
    val elevenAccentOptions = listOf("Any accent") + elevenLabsVoices.mapNotNull { it.accent }.distinct().sorted()
    val elevenLocaleOptions = listOf("English · all accents", "All languages") + elevenLabsVoices.mapNotNull { it.localeName?.let { name -> "$name (${it.locale})" } ?: it.locale }
        .filter(String::isNotBlank).distinct().sorted()
    val elevenGenderOptions = listOf("Any gender") + elevenLabsVoices.mapNotNull { it.gender }.distinct().sorted()
    val elevenAgeOptions = listOf("Any age") + elevenLabsVoices.mapNotNull { it.age }.distinct().sorted()
    val elevenTypeOptions = listOf("Any voice type") + elevenLabsVoices.mapNotNull { it.voiceType }.distinct().sorted()
    val elevenStyleOptions = listOf("Any style") + elevenLabsVoices.flatMap { it.styles }.distinct().sorted()
    val filteredElevenLabsVoices = remember(elevenLabsVoices, elevenLabsVoiceFilter, elevenAccentFilter, elevenLocaleFilter, elevenGenderFilter, elevenAgeFilter, elevenTypeFilter, elevenStyleFilter) {
        elevenLabsVoices.filter { voice ->
            val localeText = voice.localeName?.let { "$it (${voice.locale})" } ?: voice.locale
            val searchMatches = elevenLabsVoiceFilter.isBlank() || listOf(voice.name, voice.id, voice.locale, localeText,
                voice.gender.orEmpty(), voice.accent.orEmpty(), voice.age.orEmpty(), voice.voiceType.orEmpty(), voice.styles.joinToString(" "))
                .any { it.contains(elevenLabsVoiceFilter, true) }
            val localeMatches = when (elevenLocaleFilter) {
                "English · all accents" -> voice.locale.startsWith("en", true)
                "All languages" -> true
                else -> localeText == elevenLocaleFilter
            }
            searchMatches && localeMatches && (elevenAccentFilter == "Any accent" || voice.accent.equals(elevenAccentFilter, true)) &&
                (elevenGenderFilter == "Any gender" || voice.gender.equals(elevenGenderFilter, true)) &&
                (elevenAgeFilter == "Any age" || voice.age.equals(elevenAgeFilter, true)) &&
                (elevenTypeFilter == "Any voice type" || voice.voiceType.equals(elevenTypeFilter, true)) &&
                (elevenStyleFilter == "Any style" || voice.styles.any { it.equals(elevenStyleFilter, true) })
        }
    }
    val previewFileName = activePreviewPath?.substringAfterLast('/')
    val activeOfflinePreviewId = previewFileName?.takeIf { it.startsWith("kokoro-") }
        ?.removePrefix("kokoro-")?.substringBefore('-')?.toIntOrNull()
    val activeAzurePreviewId = azureVoices.firstOrNull { voice ->
        previewFileName?.startsWith("${voice.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}-") == true
    }?.id
    val activeElevenLabsPreviewId = elevenLabsVoices.firstOrNull { voice ->
        previewFileName?.startsWith("eleven-${voice.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}-") == true
    }?.id
    val selectedOfflineVoiceName = kokoroVoices.firstOrNull { it.id == selectedOfflineVoice }
        ?.let { "${it.name} · ${it.accent}" } ?: "Voice ${selectedOfflineVoice + 1}"
    val selectedAzureVoiceInfo = azureVoices.firstOrNull { it.id == selectedAzureVoice }
    val selectedAzureVoiceName = selectedAzureVoiceInfo?.let {
        "${it.name} · ${it.locale} · ${it.azureQualityCategory()}"
    } ?: selectedAzureVoice.takeIf(String::isNotBlank)?.let {
        val quality = if (it.contains("Neural", true)) "Neural" else "Standard"
        "$it · $quality"
    } ?: "Not selected"
    val selectedElevenLabsVoiceInfo = elevenLabsVoices.firstOrNull { it.id == selectedElevenLabsVoice }
    val selectedElevenLabsVoiceName = selectedElevenLabsVoiceInfo?.let { voice ->
        listOfNotNull(voice.name, voice.recommendedModelId ?: selectedElevenLabsModel)
            .distinct().joinToString(" · ")
    } ?: selectedElevenLabsVoice.takeIf(String::isNotBlank)?.let { voiceId ->
        listOfNotNull(voiceId, selectedElevenLabsModel).joinToString(" · ")
    } ?: "Not selected"
    val voicesSectionSummary = buildList {
        add("Mode · ${if (offlineOnly) "Offline only" else "Online primary · ${if (onlineProvider == "azure") "Azure Speech" else "ElevenLabs"}"}")
        add("Azure · $selectedAzureVoiceName")
        add("ElevenLabs · $selectedElevenLabsVoiceName")
        add("Offline · Kokoro · $selectedOfflineVoiceName")
    }.joinToString("\n")
    LazyColumn(modifier.fillMaxSize().testTag("settings-list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { mobileTools()
        SettingsSectionHeader("Appearance", "Theme, reading and app fonts, and layout", appearanceExpanded) { appearanceExpanded = !appearanceExpanded } }
            if (appearanceExpanded) {
            item { SettingsGroupHeading("Theme") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { setThemeMode(value) },
                            label = { Text(label, maxLines = 1) },
                            leadingIcon = if (themeMode == value) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { SettingsGroupHeading("Reading view") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${(readerTextScale * 100).toInt()}%", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = readerTextScale, onValueChange = setReaderTextScale, valueRange = .8f..1.6f, steps = 7)
                }
            }
            item { MenuChoiceDropdown("Reading font", readerFont, readerFontOptions, setReaderFont) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(readerFontDescription(readerFont), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("The quick brown fox jumps over the lazy dog.", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = readerFontFamily(readerFont)))
                    Text("System fonts follow Android; Google fonts are bundled and work offline.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { MenuChoiceDropdown("Application font", appFont, readerFontOptions, setAppFont) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Changes Yomu's menus, controls, and settings. Reading font is independent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Yomu application font sample", style = MaterialTheme.typography.bodyLarge)
                    Text(readerFontDescription(appFont), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                MenuChoiceDropdown("Line spacing", readerSpacing, listOf("Compact", "Comfortable", "Relaxed"), setReaderSpacing)
            }
            item {
                MenuChoiceDropdown("Sentence highlight", readerHighlight, listOf("Background", "Accent text"), setReaderHighlight)
            }
            item {
                MenuChoiceDropdown("Duration display", durationDisplay, listOf("Elapsed + remaining", "Remaining only", "Total duration", "Hidden"), setDurationDisplay)
            }
            item { MenuChoiceDropdown("Library list spacing", listSpacing, listOf("Compact", "Comfortable", "Relaxed"), setListSpacing) }
            item { Text("Controls the space between items in To read, Up next, and playlists.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Read footnotes", style = MaterialTheme.typography.titleMedium)
                        Text("When off, article notes and Word endnotes are skipped. PDFs crop the bottom 14% of each page, which may also omit regular body text.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = readFootnotes, onCheckedChange = setReadFootnotes)
                }
            }
            item { SettingsGroupHeading("Accent color") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    accentOptions.forEachIndexed { index, entry ->
                        val selected = selectedAccent == index && hex.isBlank()
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(entry.second)
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                                .combinedClickable(onClick = { setAccent(index) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected ${entry.first} accent", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Custom color", style = MaterialTheme.typography.titleSmall)
                    Text("Drag in the color field, then adjust the hue. You can also enter a hex value.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AccentSpectrumPicker(hex, selectedAccent, setHex)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hex, onValueChange = setHex, modifier = Modifier.weight(1f), singleLine = true, label = { Text("#3E8BFF") })
                    Button(onClick = applyHex) { Text("Apply") }
                }
            }
            }
            item { SettingsSectionHeader("Voices", voicesSectionSummary, voicesExpanded, summaryMaxLines = 4) { voicesExpanded = !voicesExpanded } }
            if (voicesExpanded) {
            item { SettingsGroupHeading("Speech mode") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !offlineOnly, onClick = { setOfflineOnly(false) })
                        Column {
                            Text("Automatic", style = MaterialTheme.typography.bodyLarge)
                            Text("Use online voices, with Kokoro as backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = offlineOnly, enabled = offlineVoiceReady, onClick = { setOfflineOnly(true) })
                        Column {
                            Text("Offline only", style = MaterialTheme.typography.bodyLarge,
                                color = if (offlineVoiceReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (offlineVoiceReady) "Use your selected Kokoro voice. No text is sent to a speech provider."
                                else "Install the offline voice below to enable offline-only playback.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (offlineOnly) Text("Playback and MP3 exports use Kokoro. Online voice sample buttons are disabled in this mode.",
                        Modifier.padding(start = 48.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SettingsGroupHeading("Primary online provider") }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = onlineProvider == "azure", onClick = { setOnlineProvider("azure") })
                        Text("Azure Speech F0", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = onlineProvider == "elevenlabs", onClick = { setOnlineProvider("elevenlabs") })
                        Text("ElevenLabs", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Try the other online provider", style = MaterialTheme.typography.titleSmall)
                        Text("If the primary service fails, try the other configured online voice before Kokoro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = onlineFallbackEnabled, onCheckedChange = setOnlineFallbackEnabled)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Try online voice again", style = MaterialTheme.typography.titleSmall)
                        Text("When off, stay with the offline voice until this item finishes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = retryOnlineAfterFailover, onCheckedChange = setRetryOnlineAfterFailover)
                }
            }
            item { SettingsGroupHeading("Call interruption recovery") }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Resume after phone calls", style = MaterialTheme.typography.titleSmall)
                        Text("Yomu observes call state only to pause and resume playback. Android permission is required.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = callRecoveryEnabled, onCheckedChange = setCallRecoveryEnabled)
                }
            }
            item { SettingsGroupHeading("Offline voice") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (offlineVoiceReady) "Kokoro English voice ready · selected voice: ${kokoroVoices.getOrNull(selectedOfflineVoice)?.name ?: "Unknown"}." else "Kokoro English backup voice · 320 MB download · about 350 MB installed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (modelProgress != null) {
                        LinearProgressIndicator(progress = { modelProgress.fraction }, modifier = Modifier.fillMaxWidth())
                        Text(modelProgress.stage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!offlineVoiceReady) {
                        OutlinedButton(onClick = installOffline) { Text("Install offline voice") }
                    }
                }
            }
            if (offlineVoiceReady) item {
                val selectedVoice = kokoroVoices.firstOrNull { it.id == selectedOfflineVoice }
                VoiceListToggle("Offline voices", "${selectedVoice?.name ?: "Choose a voice"} · ${kokoroVoices.size} voices", offlineVoicesExpanded) { offlineVoicesExpanded = !offlineVoicesExpanded }
            }
            if (offlineVoiceReady && offlineVoicesExpanded) items(kokoroVoices, key = { "kokoro-${it.id}" }) { voice ->
                Card(modifier = Modifier.frostedGlass(), colors = frostedCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleSmall)
                            Text("${voice.accent} · ${voice.gender} · offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (activeOfflinePreviewId == voice.id) Text("Playing sample", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { selectOfflineVoice(voice.id); offlineVoicesExpanded = false }) { Text(if (selectedOfflineVoice == voice.id) "Selected" else "Select") }
                        IconButton(onClick = { if (activeOfflinePreviewId == voice.id) stopVoicePreview() else auditionOfflineVoice(voice.id) }) {
                            Icon(if (activeOfflinePreviewId == voice.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (activeOfflinePreviewId == voice.id) "Stop ${voice.name} sample" else "Preview ${voice.name}")
                        }
                    }
                }
            }
            item { SettingsGroupHeading("Azure Speech") }
            item { Text("Use your own Azure Speech account. Service limits depend on its tier; Azure may charge for usage outside any free allowance. Yomu sends article text to the provider when you listen online.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (azureKeySaved) "API key saved securely · region: ${savedAzureRegion.ifBlank { "not set" }}" else "No API key saved", style = MaterialTheme.typography.labelMedium, color = if (azureKeySaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = region, onValueChange = { region = it }, singleLine = true, label = { Text("Azure region") }, supportingText = { Text("Example: eastus") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = azureKey, onValueChange = { value ->
                        azureKey = value
                    }, singleLine = true, visualTransformation = StarPasswordVisualTransformation,
                        label = if (azureKeySaved && azureKey.isEmpty()) null else ({ Text("Speech API key") }),
                        placeholder = { Text(if (azureKeySaved && azureKey.isEmpty()) "*****" else "Paste one key") },
                        supportingText = { Text("Use one Azure key only. Keep the second key separate; both are interchangeable.") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = (azureKey.isNotBlank() || azureKeySaved) && region.isNotBlank(), onClick = { saveAzure(azureKey, region); azureKey = "" }) { Text(if (azureKey.isNotBlank()) if (azureKeySaved) "Replace key" else "Save key" else "Save region") }
                        if (azureKeySaved) OutlinedButton(onClick = clearAzure, colors = ButtonDefaults.outlinedButtonColors(containerColor = frostedSurfaceColor())) { Text("Remove key") }
                    }
                    if (azureKeySaved) OutlinedButton(onClick = loadAzureVoices) { Text("Load available voices") }
                }
            }
            if (azureVoices.isNotEmpty()) item {
                val selectedVoice = azureVoices.firstOrNull { it.id == selectedAzureVoice }
                VoiceListToggle("Azure voices", "${selectedVoice?.name ?: "Choose a voice"} · ${azureVoices.size} available", azureVoicesExpanded) { azureVoicesExpanded = !azureVoicesExpanded }
            }
            if (azureVoices.isNotEmpty() && azureVoicesExpanded) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = voiceFilter, onValueChange = { voiceFilter = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Search voice names and styles") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceFilterDropdown("Language / accent", azureLocaleFilter, azureLocaleOptions, { azureLocaleFilter = it }, Modifier.weight(1f))
                        VoiceFilterDropdown("Gender", azureGenderFilter, azureGenderOptions, { azureGenderFilter = it }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceFilterDropdown("Quality", azureQualityFilter, azureQualities, { azureQualityFilter = it }, Modifier.weight(1f))
                        VoiceFilterDropdown("Speaking style", azureStyleFilter, azureStyleOptions, { azureStyleFilter = it }, Modifier.weight(1f))
                    }
                    Text("${filteredVoices.size} voices · Neural HD identifies Azure's HD voice family; available styles vary by voice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (azureVoicesExpanded) items(filteredVoices, key = { it.id }) { voice ->
                Card(modifier = Modifier.frostedGlass(), colors = frostedCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleSmall)
                            Text(voice.azureDescription(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (voice.styles.isNotEmpty()) Text("Styles: ${voice.styles.take(3).joinToString(", ")}${if (voice.styles.size > 3) " +${voice.styles.size - 3}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (activeAzurePreviewId == voice.id) Text("Playing sample", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { selectAzureVoice(voice.id) }) { Text(if (selectedAzureVoice == voice.id) "Selected" else "Select") }
                        IconButton(enabled = !offlineOnly, onClick = { if (activeAzurePreviewId == voice.id) stopVoicePreview() else auditionVoice(voice.id) }) {
                            Icon(if (activeAzurePreviewId == voice.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (activeAzurePreviewId == voice.id) "Stop ${voice.name} sample" else "Preview ${voice.name}")
                        }
                    }
                }
            }
            item { SettingsGroupHeading("ElevenLabs · optional online fallback") }
            item {
                Text(
                    "The free plan currently includes 10,000 shared credits per month (about 10 minutes of TTS at 1 credit per character). Free API users cannot access the community Voice Library; Yomu lists voices returned for your account. Account limits can change. Check your plan before enabling this provider.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (elevenLabsKeySaved) "API key saved securely" else "No API key saved", style = MaterialTheme.typography.labelMedium, color = if (elevenLabsKeySaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = elevenLabsKey,
                        onValueChange = { elevenLabsKey = it },
                        singleLine = true,
                        visualTransformation = StarPasswordVisualTransformation,
                        label = if (elevenLabsKeySaved && elevenLabsKey.isEmpty()) null else ({ Text("ElevenLabs API key") }),
                        placeholder = { Text(if (elevenLabsKeySaved && elevenLabsKey.isEmpty()) "*****" else "Paste your key") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = elevenLabsKey.isNotBlank(), onClick = { saveElevenLabsKey(elevenLabsKey); elevenLabsKey = ""; setAllowPaidElevenLabs(false) }) {
                            Text(if (elevenLabsKeySaved) "Replace key" else "Save key")
                        }
                        if (elevenLabsKeySaved) OutlinedButton(onClick = { clearElevenLabsKey(); setAllowPaidElevenLabs(false) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = frostedSurfaceColor())) { Text("Remove key") }
                    }
                    if (elevenLabsKeySaved) OutlinedButton(onClick = loadElevenLabsCatalog) { Text("Check account and load voices") }
                    elevenLabsUsage?.let { usage ->
                        Text(
                            "Account tier: ${usage.tier} · usage ${usage.characterCount} / ${usage.characterLimit} characters · ${usage.remainingCharacters} remaining, as reported by ElevenLabs.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!usage.isFree) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Allow paid ElevenLabs use", style = MaterialTheme.typography.titleSmall)
                                    Text("When enabled, Yomu may consume paid credits or incur overage charges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = allowPaidElevenLabs, onCheckedChange = setAllowPaidElevenLabs)
                            }
                        }
                    }
                }
            }
            if (elevenLabsVoices.isNotEmpty()) item {
                val selectedVoice = elevenLabsVoices.firstOrNull { it.id == selectedElevenLabsVoice }
                VoiceListToggle("ElevenLabs voices", "${selectedVoice?.name ?: "Choose a voice"} · ${elevenLabsVoices.size} available", elevenLabsVoicesExpanded) { elevenLabsVoicesExpanded = !elevenLabsVoicesExpanded }
            }
            if (elevenLabsVoices.isNotEmpty() && elevenLabsVoicesExpanded) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = elevenLabsVoiceFilter,
                        onValueChange = { elevenLabsVoiceFilter = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search voices") },
                        placeholder = { Text("Name, voice ID, or description") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceFilterDropdown("Language", elevenLocaleFilter, elevenLocaleOptions, { elevenLocaleFilter = it }, Modifier.weight(1f))
                        VoiceFilterDropdown("Accent", elevenAccentFilter, elevenAccentOptions, { elevenAccentFilter = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceFilterDropdown("Gender", elevenGenderFilter, elevenGenderOptions, { elevenGenderFilter = it }, Modifier.weight(1f))
                        VoiceFilterDropdown("Age", elevenAgeFilter, elevenAgeOptions, { elevenAgeFilter = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceFilterDropdown("Voice type", elevenTypeFilter, elevenTypeOptions, { elevenTypeFilter = it }, Modifier.weight(1f))
                        VoiceFilterDropdown("Use case", elevenStyleFilter, elevenStyleOptions, { elevenStyleFilter = it }, Modifier.weight(1f))
                    }
                    Text("${filteredElevenLabsVoices.size} voices · filters use the labels ElevenLabs provides for your account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (elevenLabsVoicesExpanded) items(filteredElevenLabsVoices, key = { "eleven-${it.id}" }) { voice ->
                val selectedTier = elevenLabsUsage?.tier
                val tierAllowed = voice.availableForTiers.isEmpty() || selectedTier == null ||
                    voice.availableForTiers.any { it.equals(selectedTier, ignoreCase = true) }
                Card(modifier = Modifier.frostedGlass(), colors = frostedCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleSmall)
                            Text(voice.displayDescription("ElevenLabs"), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (voice.recommendedModelId != null) Text("English voice model · ${voice.recommendedModelId}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!tierAllowed) Text("Not available on your ${selectedTier} tier · ${voice.availableForTiers.joinToString()}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            if (activeElevenLabsPreviewId == voice.id) Text("Playing sample", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(enabled = tierAllowed, onClick = { selectElevenLabsVoice(voice.id) }) { Text(if (selectedElevenLabsVoice == voice.id) "Selected" else "Select") }
                        IconButton(enabled = !offlineOnly && (voice.previewUrl != null || activeElevenLabsPreviewId == voice.id),
                            onClick = { if (activeElevenLabsPreviewId == voice.id) stopVoicePreview() else auditionElevenLabsVoice(voice.id) }) {
                            Icon(if (activeElevenLabsPreviewId == voice.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (activeElevenLabsPreviewId == voice.id) "Stop ${voice.name} sample" else "Preview ${voice.name}")
                        }
                    }
                }
            }
            }
            item { SettingsSectionHeader("Defaults", "Swipe actions, playback speed, and export folder", defaultsExpanded) { defaultsExpanded = !defaultsExpanded } }
            if (defaultsExpanded) {
            item { Divider() }
            item { SettingsGroupHeading("List swipe actions") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Choose what a horizontal swipe does on a reading item.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MenuChoiceDropdown("Swipe right", swipeRightAction, swipeActionOptions, setSwipeRightAction)
                    MenuChoiceDropdown("Swipe left", swipeLeftAction, swipeActionOptions, setSwipeLeftAction)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Playback speed", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${"%.1f".format(Locale.US, playbackSpeed)}×", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = playbackSpeed.coerceIn(.7f, 2f), onValueChange = setPlaybackSpeed, valueRange = .7f..2f, steps = 12)
                    Text("Default speed for listening and MP3 duration estimates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SettingsGroupHeading("MP3 export") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (exportFolderDisplayName != null) {
                        Text("Selected folder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(exportFolderDisplayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Exports will be saved here for Pocket Casts to scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text("Choose a folder once to send future MP3 exports directly there, such as a Pocket Casts import folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = chooseExportFolder) { Text(if (exportFolderDisplayName != null) "Change export folder" else "Choose export folder") }
                }
            }
            }
            item { dev.yomu.app.sync.SyncSettings() }
            item { SettingsSectionHeader("Advanced", "Backup, OPML, and diagnostic log", advancedExpanded) { advancedExpanded = !advancedExpanded } }
            if (advancedExpanded) {
            item { SettingsGroupHeading("Manual backup") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Back up your library database, saved documents, reading positions, playlists, and app settings. API keys and the large offline voice model are excluded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = createBackup) { Text("Create backup") }
                        OutlinedButton(onClick = chooseBackupToRestore) { Text("Restore backup") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OPML is a supplemental URL-list format. It does not include extracted text, documents, reading positions, or settings; use a Yomu backup for those.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = exportOpml) { Text("Export OPML") }
                        OutlinedButton(onClick = importOpml) { Text("Import OPML") }
                    }
                }
            }
            item { SettingsGroupHeading("Diagnostic log") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recent app errors are kept on this phone to help troubleshoot. Yomu does not include article text, audio, or API keys in these entries.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDiagnostics = true }) { Text("View log · ${diagnosticEvents.size}") }
                        if (diagnosticEvents.isNotEmpty()) TextButton(onClick = clearDiagnosticEvents) { Text("Clear log") }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = frostedSurfaceColor()),
                ) { Text("About Yomu") }
            }
            item { Text("Your library works offline. Optional web sync shares content with your private Yomu server. Manual backups also preserve appearance settings.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (showDiagnostics) AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            containerColor = frostedDialogContainerColor(),
            title = { Text("Diagnostic log") },
            text = {
                if (diagnosticEvents.isEmpty()) Text("No app errors have been recorded yet.")
                else Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    diagnosticEvents.asReversed().forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(DateFormat.getDateTimeInstance().format(Date(entry.timestampMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } },
            dismissButton = if (diagnosticEvents.isNotEmpty()) ({ TextButton(onClick = clearDiagnosticEvents) { Text("Clear log") } }) else null,
        )
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "Unknown"
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().frostedGlass(),
            colors = frostedCardColors(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.yomu_icon),
                    contentDescription = "Yomu book and headphones icon",
                    modifier = Modifier.size(132.dp),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    painter = painterResource(R.drawable.yomu_wordmark),
                    contentDescription = "Yomu",
                    modifier = Modifier.width(220.dp).height(74.dp),
                    contentScale = ContentScale.Fit,
                )
                Text("Your reading, ready to listen.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Published by " + context.getString(R.string.publisher_name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                Text(
                    "Yomu turns articles and documents into a listening library. Save something for later, choose a voice, and listen at your own pace with sentence-by-sentence reading progress.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Version $appVersion",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String, summary: String, expanded: Boolean, summaryMaxLines: Int = 2, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Card(onClick = onClick, modifier = Modifier.shadow(8.dp, shape).border(
        1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .52f), shape,
    ), shape = shape, colors = frostedCardColors()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(summary, maxLines = summaryMaxLines, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsGroupHeading(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Divider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = .24f))
    }
}

@Composable
private fun AccentSpectrumPicker(hex: String, selectedAccent: Int, setHex: (String) -> Unit) {
    val baseColor = remember(hex, selectedAccent) {
        if (hex.isBlank()) accentOptions[selectedAccent].second else parseHex(hex) ?: accentOptions[selectedAccent].second
    }
    val hsv = remember(baseColor) { FloatArray(3).also { PlatformColor.colorToHSV(baseColor.toArgb(), it) } }
    val spectrumShape = RoundedCornerShape(14.dp)
    Text("Saturation and brightness", style = MaterialTheme.typography.labelMedium)
    Canvas(
        Modifier.fillMaxWidth().height(156.dp).clip(spectrumShape)
            .pointerInput(hsv[0]) {
                fun select(point: Offset) {
                    val saturation = (point.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val brightness = (1f - point.y / size.height.toFloat()).coerceIn(0f, 1f)
                    setHex(Color.hsv(hsv[0], saturation, brightness).toHexRgb())
                }
                detectTapGestures(onTap = ::select)
            }
            .pointerInput(hsv[0]) {
                fun select(point: Offset) {
                    val saturation = (point.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val brightness = (1f - point.y / size.height.toFloat()).coerceIn(0f, 1f)
                    setHex(Color.hsv(hsv[0], saturation, brightness).toHexRgb())
                }
                detectDragGestures(onDragStart = ::select) { change, _ ->
                    select(change.position)
                    change.consume()
                }
            }
    ) {
        drawRect(Color.hsv(hsv[0], 1f, 1f))
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
        drawCircle(Color.Black.copy(alpha = .75f), radius = 14.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center)
        drawCircle(baseColor, radius = 8.dp.toPx(), center = center)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hue", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.size(26.dp).clip(CircleShape).background(baseColor).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(hex.ifBlank { baseColor.toHexRgb() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = (hsv[0] / 360f).coerceIn(0f, 1f),
        onValueChange = { hue -> setHex(Color.hsv(hue * 360f, hsv[1], hsv[2]).toHexRgb()) },
        valueRange = 0f..1f,
        modifier = Modifier.semantics { contentDescription = "Accent hue" },
    )
}

private fun Color.toHexRgb(): String = "#%02X%02X%02X".format(
    Locale.US, (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(),
)

@Composable
private fun VoiceListToggle(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.frostedGlass(), colors = frostedCardColors()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide $title" else "Show $title",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VoiceFilterDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = frostedSurfaceColor())) {
            Text("$label: $selected", Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Icon(Icons.Default.ExpandMore, contentDescription = "Show $label options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(20.dp),
            containerColor = menuSurfaceColor(), tonalElevation = 10.dp,
            modifier = Modifier.frostedGlass(RoundedCornerShape(20.dp))) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}


@Composable
private fun ReaderScreen(item: ReadingItemEntity, vm: LibraryViewModel, onClose: () -> Unit, onExportMp3: (String) -> Unit, exportStatus: String?, cancelExport: () -> Unit,
                        readerTextScale: Float, readerSpacing: String, readerHighlight: String, durationDisplay: String, readerFont: String,
                        modifier: Modifier = Modifier) {
    val quiet by vm.quietReading.collectAsStateWithLifecycle()
    val speed by vm.playbackSpeed.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    var timerMenu by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var speedDraft by remember { mutableFloatStateOf(speed) }
    val progress by vm.progressFor(item.id).collectAsStateWithLifecycle(initialValue = null)
    val sentences = remember(item.normalizedText) { readingSentences(item.normalizedText) }
    val readingListState = rememberLazyListState()
    val readerScope = rememberCoroutineScope()
    val readingCursor by vm.readingCursorFor(item.id).collectAsStateWithLifecycle(initialValue = null)
    val sharedDefaults by vm.sharedReaderDefaults.collectAsStateWithLifecycle()
    val resumeConflicts by vm.resumeConflicts.collectAsStateWithLifecycle()
    val cursorConflict = resumeConflicts["reading_cursors/${item.id}"]
    val defaultsConflict = resumeConflicts["reader_defaults/global"]
    val offsets = remember(item.normalizedText, sentences) { dev.yomu.app.data.ReadingAnchor.offsets(item.normalizedText, sentences) }
    var followNarration by rememberSaveable(item.id) { mutableStateOf(!quiet) }
    LaunchedEffect(quiet) { followNarration = !quiet }
    val readerSyncPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("reader_sync_settings", android.content.Context.MODE_PRIVATE)
    var localOverride by rememberSaveable { mutableStateOf(readerSyncPrefs.getBoolean("local_override", true)) }
    val sharedFont = when (sharedDefaults?.font) { "mono" -> "Monospace"; "sans" -> "Default app font"; else -> "Serif" }
    val effectiveFont = if (!localOverride && sharedDefaults != null) sharedFont else readerFont
    val effectiveScale = if (!localOverride && sharedDefaults != null) (sharedDefaults!!.size / 16f) else readerTextScale
    LaunchedEffect(item.id, followNarration, cursorConflict) {
        androidx.compose.runtime.snapshotFlow { Triple(readingListState.firstVisibleItemIndex, readingListState.firstVisibleItemScrollOffset, readingListState.canScrollForward) }
            .distinctUntilChanged().drop(1).debounce(800).collect { (index, _, canScrollForward) ->
                if (!followNarration && cursorConflict == null && index in offsets.indices) {
                    vm.saveReadingCursor(item, offsets[index], if (canScrollForward) offsets[index] else item.normalizedText.length)
                }
            }
    }
    var positionDraft by remember(item.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(progress?.sentenceIndex, sentences.size) {
        positionDraft = if (sentences.size > 1) (progress?.sentenceIndex ?: 0).toFloat() / (sentences.size - 1) else 0f
    }
    val current = if (playback?.itemId == item.id) playback!!.sentenceIndex.takeIf { it in sentences.indices } ?: -1 else -1
    val seekDisplayed: (Int) -> Unit = { index -> if (playback?.itemId == item.id) vm.seekSentence(index) else vm.startNowAt(item.id, index) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playback?.sleepUntil) { while (playback?.sleepUntil ?: 0L > 0L) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(current, sentences.size, followNarration) {
        if (followNarration && playback?.itemId == item.id && current in sentences.indices) {
            val offset = -(readingListState.layoutInfo.viewportSize.height / 3)
            readingListState.animateScrollToItem(current, scrollOffset = offset)
        }
    }
    val listened = current.coerceIn(0, sentences.size)
    val totalWords = remember(item.normalizedText) { wordCount(item.normalizedText).coerceAtLeast(1) }
    val heardWords = remember(sentences, listened) { wordCount(sentences.take(listened).joinToString(" ")) }
    val durationAtSpeed = (item.estimatedSecondsAtOneX / speed).toInt().coerceAtLeast(0)
    val elapsedEstimate = durationAtSpeed * heardWords / totalWords
    val remainingEstimate = (durationAtSpeed - elapsedEstimate).coerceAtLeast(0)
    Column(modifier.fillMaxSize().padding(horizontal = 21.dp)) {
        if (playback != null && playback?.itemId != item.id) Text("Playing: ${playback?.title}. Listen here starts this article.", style = MaterialTheme.typography.bodySmall)
        if (item.sourceType == "newsletter") Text(newsletterArrival(item.importedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { if (quiet) vm.startNow(item.id) else vm.readQuietly(item.id) }) { Text(if (quiet) "Listen here" else "Read quietly") }
        ReaderItemTools(item, vm, offsets.getOrElse(readingListState.firstVisibleItemIndex) { 0 }, playback?.sentenceIndex?.let { offsets.getOrElse(it) { 0 } }, { offset ->
            followNarration = false; readerScope.launch { readingListState.scrollToItem(offsets.indexOfLast { it <= offset }.coerceAtLeast(0)) }
        })
        var resumeOptions by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { resumeOptions = !resumeOptions }) { Text(if (resumeOptions) "Hide reading & resume options" else if (cursorConflict != null || defaultsConflict != null) "Review resume conflict" else "Reading & resume options") }
        if (resumeOptions) {
            val savedOffset = readingCursor?.let { dev.yomu.app.data.ReadingAnchor.resolve(item.normalizedText, it.anchor) }
            if (readingCursor != null) {
                Text(if (savedOffset == null) "Saved reading position could not be matched after the text changed." else "Reading position from ${readingCursor!!.deviceLabel}", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = savedOffset != null, onClick = {
                    followNarration = false
                    readerScope.launch { readingListState.scrollToItem(offsets.indexOfLast { it <= (savedOffset ?: 0) }.coerceAtLeast(0)) }
                }) { Text("Resume reading") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Follow narration", Modifier.weight(1f)); Switch(checked = followNarration, onCheckedChange = { followNarration = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Override library reading defaults", Modifier.weight(1f)); Switch(checked = localOverride, onCheckedChange = { localOverride = it; readerSyncPrefs.edit().putBoolean("local_override", it).apply() })
            }
            TextButton(onClick = {
                val font = when (readerFont) { "Serif", "Lora", "Literata", "Source Serif 4" -> "serif"; "Monospace" -> "mono"; else -> "sans" }
                val size = listOf(16,18,20,22,24,28,32).minBy { kotlin.math.abs(it - 16 * readerTextScale) }
                val spacing = when (readerSpacing) { "Compact" -> 1.4; "Relaxed" -> 2.0; else -> 1.7 }
                vm.shareReaderDefaults(font,size,spacing)
            }) { Text("Use phone settings as library defaults") }
            Text("Shared fonts use Serif, Sans serif, or Monospace. Theme stays on this phone.", style = MaterialTheme.typography.labelSmall)
            for ((conflictKey, conflictValue) in listOf("reading_cursors/${item.id}" to cursorConflict, "reader_defaults/global" to defaultsConflict)) if (conflictValue != null) {
                Text("Another device changed ${if (conflictKey.startsWith("reading_cursors")) "your reading position" else "reading defaults"}. Choose which to keep.", style = MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick = { vm.resolveResumeConflict(conflictKey, true) }) { Text("Keep phone version") }; TextButton(onClick = { vm.resolveResumeConflict(conflictKey, false) }) { Text("Use synced version") } }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = readingListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sentences.size) { index ->
                val active = index == current
                val baseStyle = MaterialTheme.typography.bodyLarge
                val style = baseStyle.copy(fontFamily = readerFontFamily(effectiveFont))
                val lineMultiplier = if (!localOverride && sharedDefaults != null) sharedDefaults!!.spacing.toFloat() else when (readerSpacing) { "Compact" -> 1.25f; "Relaxed" -> 1.75f; else -> 1.5f }
                val hasBackgroundHighlight = active && readerHighlight == "Background"
                Surface(
                    onClick = { if (!quiet) seekDisplayed(index) },
                    color = if (hasBackgroundHighlight) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    SelectionContainer { Text(sentences[index], Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        style = style.copy(
                            fontSize = style.fontSize * effectiveScale,
                            lineHeight = style.fontSize * effectiveScale * lineMultiplier,
                        ),
                        color = if (active && !quiet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(
                onClick = { onExportMp3(item.title.replace(Regex("[^A-Za-z0-9 _-]"), "").take(70).ifBlank { "Yomu reading" } + ".mp3") },
                enabled = !exportStatus.isMp3ExportProgress(),
                modifier = Modifier.semantics { contentDescription = "Export MP3 with chapters" },
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export MP3")
            }
        }
        if (!quiet) Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).frostedGlass(RoundedCornerShape(30.dp)),
            color = frostedSurfaceColor(),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (playback?.itemId == item.id) playback?.phase ?: "Paused" else "Ready to listen", style = MaterialTheme.typography.labelMedium)
                if (playback?.stopAtEnd == true) Text("Stopping at end of item", style = MaterialTheme.typography.labelMedium)
                if ((playback?.sleepUntil ?: 0) > 0) Text("Sleep timer: ${formatClockDuration(((playback!!.sleepUntil - now).coerceAtLeast(0) / 1000).toInt())}", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sentences.isEmpty()) "No readable text" else "${((progress?.sentenceIndex ?: 0) + 1).coerceAtMost(sentences.size)} of ${sentences.size} sentences",
                        Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                if (sentences.size > 1) {
                    Column {
                        Slider(value = positionDraft.coerceIn(0f, 1f), onValueChange = { positionDraft = it },
                            onValueChangeFinished = { seekDisplayed((positionDraft * (sentences.size - 1)).roundToInt()) },
                            valueRange = 0f..1f,
                            modifier = Modifier.semantics {
                                contentDescription = "Reading position"
                                stateDescription = "Sentence ${((progress?.sentenceIndex ?: 0) + 1).coerceAtMost(sentences.size)} of ${sentences.size}"
                            })
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (durationDisplay == "Remaining only") Spacer(Modifier.weight(1f))
                            else Text(formatClockDuration(elapsedEstimate), Modifier.weight(1f).semantics {
                                contentDescription = "Elapsed time ${formatDurationSpoken(elapsedEstimate)}"
                            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val rightTime = if (durationDisplay == "Total duration") durationAtSpeed else remainingEstimate
                            Text(
                                if (durationDisplay == "Total duration") formatClockDuration(rightTime) else "−${formatClockDuration(rightTime)}",
                                Modifier.semantics { contentDescription = if (durationDisplay == "Total duration") "Total duration ${formatDurationSpoken(rightTime)}" else "Remaining time ${formatDurationSpoken(rightTime)}" },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (exportStatus.isMp3ExportProgress()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(exportStatus.orEmpty(), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = cancelExport) { Text("Cancel") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(
                        onClick = { speedDraft = speed; speedDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 8.dp),
                        modifier = Modifier.semantics { contentDescription = "Playback speed ${"%.1f".format(Locale.US, speed)} times" },
                    ) {
                        Text("${"%.1f".format(Locale.US, speed)}×", maxLines = 1)
                    }
                    IconButton(onClick = { seekDisplayed(((progress?.sentenceIndex ?: 0) - 1).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous sentence", modifier = Modifier.size(30.dp))
                    }
                    FilledIconButton(onClick = { if (playback?.itemId == item.id) vm.togglePlayback() else vm.startNow(item.id) }, modifier = Modifier.size(56.dp)) {
                        Icon(if (playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playback?.isPlaying == true) "Pause reading" else "Play or pause", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { seekDisplayed(((progress?.sentenceIndex ?: 0) + 1).coerceAtMost(sentences.lastIndex.coerceAtLeast(0))) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, "Next sentence", modifier = Modifier.size(30.dp))
                    }
                    Box {
                        IconButton(onClick = { timerMenu = true }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.Timer, "Sleep timer", modifier = Modifier.size(26.dp))
                        }
                        DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }, shape = RoundedCornerShape(20.dp),
                            containerColor = menuSurfaceColor(), tonalElevation = 10.dp,
                            modifier = Modifier.frostedGlass(RoundedCornerShape(20.dp))) {
                            listOf(5, 10, 15, 30, 60).forEach { minutes ->
                                DropdownMenuItem(text = { Text("Stop in $minutes minutes") }, onClick = { vm.setSleepTimer(minutes); timerMenu = false })
                            }
                            DropdownMenuItem(text = { Text("Stop at end of item") }, onClick = { vm.setSleepTimer(-1); timerMenu = false })
                            DropdownMenuItem(text = { Text("Cancel sleep timer") }, onClick = { vm.setSleepTimer(0); timerMenu = false })
                        }
                    }
                }
            }
        }
    }
    if (speedDialog) AlertDialog(
        onDismissRequest = { speedDialog = false },
        containerColor = frostedDialogContainerColor(),
        title = { Text("Reading speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${"%.1f".format(Locale.US, speedDraft)}×", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                Slider(value = speedDraft, onValueChange = { speedDraft = it }, valueRange = .7f..2f, steps = 12)
                Text("Adjust playback from 0.7× to 2.0×.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { vm.setPlaybackSpeed(speedDraft); speedDialog = false }) { Text("Set speed") } },
        dismissButton = { TextButton(onClick = { speedDialog = false }) { Text("Cancel") } },
    )
}

private fun String?.isMp3ExportProgress(): Boolean = this?.startsWith("Creating chapter") == true || this?.startsWith("Writing Pocket") == true

@Composable
private fun Modifier.frostedGlass(shape: RoundedCornerShape = RoundedCornerShape(18.dp)): Modifier =
    shadow(6.dp, shape, clip = false)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .025f))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .06f))), shape)

@Composable
internal fun frostedCardColors() = CardDefaults.cardColors(
    containerColor = frostedSurfaceColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun frostedSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = .32f)

@Composable
private fun menuSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun frostedDialogContainerColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)

private fun dev.yomu.app.speech.SpeechVoice.displayDescription(provider: String): String {
    description?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val traits = listOfNotNull(
        accent?.takeIf(String::isNotBlank)?.let { "$it accent" },
        age?.takeIf(String::isNotBlank),
        gender?.takeIf(String::isNotBlank),
        styles.firstOrNull()?.takeIf(String::isNotBlank)?.replaceFirstChar(Char::uppercase),
    ).distinct()
    return traits.joinToString(" · ").ifBlank {
        "${localeName ?: locale} · No description provided by $provider; preview before selecting."
    }
}

private object StarPasswordVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        AnnotatedString("*".repeat(text.text.length)), OffsetMapping.Identity,
    )
}

@Composable
private fun MenuChoiceDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = frostedSurfaceColor())) {
            Text("$label · $selected", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ExpandMore, contentDescription = "Show $label options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(20.dp),
            containerColor = menuSurfaceColor(), tonalElevation = 10.dp,
            modifier = Modifier.frostedGlass(RoundedCornerShape(20.dp))) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
private fun CompactPlayer(
    state: dev.yomu.app.playback.PlaybackEvents.Snapshot,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(30.dp)
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().frostedGlass(pillShape),
        color = frostedSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = pillShape,
        tonalElevation = 5.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressTrail(state.sentenceIndex > 0, false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.sentenceCount > 0) "Sentence ${(state.sentenceIndex + 1).coerceAtMost(state.sentenceCount)} of ${state.sentenceCount}" else "Ready to resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onClick = onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause reading" else "Resume reading",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next sentence", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { if (state.sentenceCount > 0) state.sentenceIndex.toFloat() / state.sentenceCount else 0f },
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 6.dp).height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
        }
    }
}

private fun wordCount(text: String): Int = Regex("\\b[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*\\b").findAll(text).count()

private fun readingSentences(text: String): List<String> {
    val iterator = BreakIterator.getSentenceInstance(Locale.US).apply { setText(text) }
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(result::add)
        start = end
        end = iterator.next()
    }
    if (result.isEmpty() && text.isNotBlank()) result += text.trim()
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContentSheet(onDismiss: () -> Unit, onUrl: (String) -> Unit, onFiles: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add to To read", style = MaterialTheme.typography.headlineSmall)
            Text("Share a public page from your browser, paste a link, or pick a document.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Article URL") }, placeholder = { Text("https://…") })
            Button(onClick = { if (url.isNotBlank()) onUrl(url) }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save link") }
            OutlinedButton(onClick = onFiles, modifier = Modifier.fillMaxWidth()) { Text("Choose PDF or Word documents") }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = frostedDialogContainerColor(), title = { Text(title) }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}

internal fun formatClockDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = safeSeconds % 3_600 / 60
    val remainingSeconds = safeSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.US, hours, minutes, remainingSeconds)
    else "%02d:%02d".format(Locale.US, minutes, remainingSeconds)
}

private fun formatDurationSpoken(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = safeSeconds % 3_600 / 60
    val remainingSeconds = safeSeconds % 60
    return buildList {
        if (hours > 0) add("$hours ${if (hours == 1) "hour" else "hours"}")
        if (minutes > 0) add("$minutes ${if (minutes == 1) "minute" else "minutes"}")
        if (remainingSeconds > 0 || isEmpty()) add("$remainingSeconds ${if (remainingSeconds == 1) "second" else "seconds"}")
    }.joinToString(" ")
}

private fun parseHex(value: String): Color? = runCatching {
    val normalized = value.trim().removePrefix("#")
    require(normalized.length == 6)
    Color(android.graphics.Color.parseColor("#$normalized"))
}.getOrNull()
