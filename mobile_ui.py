from pathlib import Path
p=Path('android/app/src/main/java/dev/yomu/app/MainActivity.kt');s=p.read_text(encoding='utf-8')
def edit(old,new):
 global s
 assert old in s,old[:100]
 s=s.replace(old,new)
edit('import androidx.compose.foundation.ExperimentalFoundationApi', 'import androidx.compose.foundation.text.selection.SelectionContainer\nimport androidx.compose.material3.Checkbox\nimport androidx.compose.foundation.ExperimentalFoundationApi')
edit('''                    onOpenAbout = { showSettings = false; showAbout = true },''','''                    mobileTools = { MobileHealthTools(vm) },
                    onOpenAbout = { showSettings = false; showAbout = true },''')
edit('''                           onOpenAbout: () -> Unit = {},''','''                           mobileTools: @Composable () -> Unit = {},
                           onOpenAbout: () -> Unit = {},''')
# Insert health controls into the existing vertically scrollable settings content.
idx=s.index('private fun SettingsScreen(');target=s.index('SettingsSectionHeader(',idx)
# Find first actual group header and insert preceding composable call.
s=s[:target]+'mobileTools()\n        '+s[target:]
edit('''    longPressLabel: String = "Play now",
) {''','''    longPressLabel: String = "Play now",
    vm: LibraryViewModel? = null,
) {''')
edit('''    val shownRows = remember(rows, effectiveSortMode, queueMode) { sortLibraryRows(rows, effectiveSortMode, queueMode) }''','''    val allItems = if (vm != null) vm.allItems.collectAsStateWithLifecycle().value else emptyList()
    val memberships = if (vm != null) vm.memberships.collectAsStateWithLifecycle().value else emptyList()
    val playlists = if (vm != null) vm.playlists.collectAsStateWithLifecycle().value else emptyList()
    var query by rememberSaveable { mutableStateOf("") }
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
        confirmButton = { TextButton(onClick = { bulkPlaylist = false }) { Text("Close") } })''')
edit('''            if (showSortPicker) MenuChoiceDropdown''','''            if (vm != null) {
                OutlinedTextField(query, { query = it }, label = { Text("Search title, text, source or tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuChoiceDropdown("Type", typeFilter, listOf("All types", "url", "pdf", "docx", "doc", "text")) { typeFilter = it }
                    MenuChoiceDropdown("State", stateFilter, listOf("All states", "Ready", "Needs attention", "Partly heard", "Read", "Unread")) { stateFilter = it }
                }
                TextButton(onClick = { selectionMode = !selectionMode; selected = emptySet() }) { Text(if (selectionMode) "Done selecting" else "Select items") }
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
            if (showSortPicker) MenuChoiceDropdown''')
edit('''                    ReadingItemCard(row, queueMode''','''                    if (selectionMode) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + row.id else selected - row.id })
                        Text(row.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else ReadingItemCard(row, queueMode''')
edit('''onMenuPlayNow = { onMenuPlay(row) }, longPressLabel = longPressLabel)''','''onMenuPlayNow = { onMenuPlay(row) }, longPressLabel = longPressLabel,
                        onReadQuietly = { vm?.readQuietly(row.id) }, quietAvailable = vm != null,
                        membershipLabel = memberships.filter { it.itemId == row.id }.mapNotNull { membership -> playlists.find { it.id == membership.playlistId }?.name }.joinToString(", "))''')
edit('''onMenuPlayNow: () -> Unit = onPlayNow, longPressLabel: String = "Play now")''','''onMenuPlayNow: () -> Unit = onPlayNow, longPressLabel: String = "Play now",
                            onReadQuietly: () -> Unit = {}, quietAvailable: Boolean = false, membershipLabel: String = "")''')
edit('''                        DropdownMenuItem(text = { Text("Play now") }''','''                        if (quietAvailable) DropdownMenuItem(text = { Text("Read quietly") }, enabled = ready, onClick = { menu = false; onReadQuietly() })
                        DropdownMenuItem(text = { Text("Play now") }''')
edit('''                } else if (row.sentenceIndex > 0) {''','''                }
                if (membershipLabel.isNotBlank()) Text("Playlists: $membershipLabel", style = MaterialTheme.typography.labelSmall)
                if (row.importState == "ready" && row.sentenceIndex > 0) {''')
edit('''private val librarySortOptions = listOf("Manual", "Newest", "Oldest", "Shortest", "Longest")''','''private val librarySortOptions = listOf("Manual", "Newest", "Oldest", "Title A–Z", "Shortest", "Longest")''')
edit('''    "Newest" ->''','''    "Title A–Z" -> rows.sortedBy { it.title.lowercase() }
    "Newest" ->''')
edit('''listOf("Manual", "Newest", "Oldest"))''','''listOf("Manual", "Newest", "Oldest", "Title A–Z"))''')
# Connect list-level features to every library invocation with an available view model.
edit('''showSortPicker = true, allowReorder''','''vm = vm, showSortPicker = true, allowReorder''')
edit('''PlaylistList(playlists, onCreate''','''PlaylistList(playlists, vm = vm, onCreate''')
edit('''private fun PlaylistList(items: List<PlaylistEntity>, onCreate''','''private fun PlaylistList(items: List<PlaylistEntity>, vm: LibraryViewModel, onCreate''')
edit('''    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)''','''    val memberships by vm.memberships.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)''')
edit('''Text("Playlist", style = MaterialTheme.typography.labelMedium''','''Text("${memberships.count { it.playlistId == playlist.id }} items", style = MaterialTheme.typography.labelMedium''')
start=s.index('private fun PlaylistDetail(');end=s.index('\n@Composable',start)
s=s[:start]+'''private fun PlaylistDetail(playlist: PlaylistEntity, rows: List<ReadingListRow>, vm: LibraryViewModel, enqueue: (String) -> Unit, itemSpacingDp: Int, modifier: Modifier = Modifier) {
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
''' + s[end:]
edit('''The saved document copy will also be deleted.''','''You can restore it from Deleted items in Settings until you permanently remove it.''')
# Keep card reader selection distinct from playback, and never seek another item.
edit('''    val speed by vm.playbackSpeed.collectAsStateWithLifecycle()''','''    val quiet by vm.quietReading.collectAsStateWithLifecycle()
    val speed by vm.playbackSpeed.collectAsStateWithLifecycle()''')
edit('''    val current = progress?.sentenceIndex ?: -1''','''    val current = if (playback?.itemId == item.id) progress?.sentenceIndex ?: -1 else -1
    val seekDisplayed: (Int) -> Unit = { index -> if (playback?.itemId == item.id) vm.seekSentence(index) else vm.startNow(item.id, index) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playback?.sleepUntil) { while (playback?.sleepUntil ?: 0L > 0L) { now = System.currentTimeMillis(); delay(1000) } }''')
edit('''        var resumeOptions by rememberSaveable''','''        if (playback != null && playback?.itemId != item.id) Text("Playing: ${playback?.title}. Listen here starts this article.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { if (quiet) vm.startNow(item.id) else vm.readQuietly(item.id) }) { Text(if (quiet) "Listen here" else "Read quietly") }
        ReaderItemTools(item, vm, offsets.getOrElse(readingListState.firstVisibleItemIndex) { 0 }, playback?.sentenceIndex?.let { offsets.getOrElse(it) { 0 } }, { offset ->
            followNarration = false; readerScope.launch { readingListState.scrollToItem(offsets.indexOfLast { it <= offset }.coerceAtLeast(0)) }
        })
        var resumeOptions by rememberSaveable''')
edit('''                    onClick = { vm.seekSentence(index) },''','''                    onClick = { if (!quiet) seekDisplayed(index) },''')
edit('''                    Text(sentences[index], Modifier.fillMaxWidth()''','''                    SelectionContainer { Text(sentences[index], Modifier.fillMaxWidth()''')
edit('''color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }''','''color = if (active && !quiet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                }''')
edit('''        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).frostedGlass''','''        if (!quiet) Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).frostedGlass''')
edit('''            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {''','''            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (playback?.itemId == item.id) playback?.phase ?: "Paused" else "Ready to listen", style = MaterialTheme.typography.labelMedium)
                if (playback?.stopAtEnd == true) Text("Stopping at end of item", style = MaterialTheme.typography.labelMedium)
                if ((playback?.sleepUntil ?: 0) > 0) Text("Sleep timer: ${formatClockDuration(((playback!!.sleepUntil - now).coerceAtLeast(0) / 1000).toInt())}", style = MaterialTheme.typography.labelMedium)''')
edit('''onValueChangeFinished = { vm.seekSentence((positionDraft * (sentences.size - 1)).roundToInt()) }''','''onValueChangeFinished = { seekDisplayed((positionDraft * (sentences.size - 1)).roundToInt()) }''')
edit('''IconButton(onClick = vm::previousSentence, modifier = Modifier.size(56.dp))''','''IconButton(onClick = { seekDisplayed(((progress?.sentenceIndex ?: 0) - 1).coerceAtLeast(0)) }, modifier = Modifier.size(48.dp))''')
edit('''FilledIconButton(onClick = vm::togglePlayback, modifier = Modifier.size(68.dp))''','''FilledIconButton(onClick = { if (playback?.itemId == item.id) vm.togglePlayback() else vm.startNow(item.id) }, modifier = Modifier.size(56.dp))''')
edit('''IconButton(onClick = vm::nextSentence, modifier = Modifier.size(56.dp))''','''IconButton(onClick = { seekDisplayed(((progress?.sentenceIndex ?: 0) + 1).coerceAtMost(sentences.lastIndex.coerceAtLeast(0))) }, modifier = Modifier.size(48.dp))''')
edit('''                            DropdownMenuItem(text = { Text("Cancel sleep timer") }''','''                            DropdownMenuItem(text = { Text("Stop at end of item") }, onClick = { vm.setSleepTimer(-1); timerMenu = false })
                            DropdownMenuItem(text = { Text("Cancel sleep timer") }''')
p.write_text(s,encoding='utf-8')
print('Mobile list, playlist and reader controls connected')
