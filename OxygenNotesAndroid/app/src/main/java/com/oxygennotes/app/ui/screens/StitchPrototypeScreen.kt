package com.oxygennotes.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxygennotes.app.data.Folder
import com.oxygennotes.app.data.Note
import com.oxygennotes.app.ui.OxygenNotesViewModel
import com.oxygennotes.app.ui.components.DeleteFolderDialog
import com.oxygennotes.app.ui.components.DeleteNoteDialog
import com.oxygennotes.app.ui.components.FolderRadialMenu
import com.oxygennotes.app.ui.components.FolderTreeComponent
import com.oxygennotes.app.ui.components.NoteGrid
import com.oxygennotes.app.ui.components.NoteRadialMenu
import com.oxygennotes.app.ui.components.NoteResizePicker
import com.oxygennotes.app.ui.components.RenameFolderDialog
import com.oxygennotes.app.ui.components.RenameNoteDialog
import com.oxygennotes.app.ui.components.SpotlightOverlay
import com.oxygennotes.app.ui.theme.StitchGreen

// Context menu target — determines which overlay/menu renders at screen level
private sealed class ContextTarget {
    abstract val anchor: Offset
    abstract val bounds: Rect

    data class DashNote(val note: Note, override val anchor: Offset, override val bounds: Rect) : ContextTarget()
    data class TreeFolder(val folder: Folder, override val anchor: Offset, override val bounds: Rect) : ContextTarget()
    data class TreeNote(val note: Note, override val anchor: Offset, override val bounds: Rect) : ContextTarget()
}

@Composable
fun StitchPrototypeScreen(
    viewModel: OxygenNotesViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddBranchDialog by remember { mutableStateOf(false) }

    var isAssignMode by remember { mutableStateOf(false) }
    var workingFolders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    val workingNoteAssignments = remember { mutableStateMapOf<Long, Long?>() }
    val workingNoteSortOrders = remember { mutableStateMapOf<Long, Long>() }
    var noteDraggingOverBar by remember { mutableStateOf(false) }
    val allFolders by viewModel.allFolders.collectAsState()

    // ── Full-screen context menu state ──────────────────────────────────────────
    var contextTarget by remember { mutableStateOf<ContextTarget?>(null) }
    var showResizePicker by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<Note?>(null) }
    var pendingDeleteFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingRenameFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingRenameNote by remember { mutableStateOf<Note?>(null) }

    if (showAddBranchDialog) {
        AddBranchDialog(
            onConfirm = { name ->
                viewModel.createFolder(name, parentId = null)
                showAddBranchDialog = false
            },
            onDismiss = { showAddBranchDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val uiState by viewModel.uiState.collectAsState()
                StitchTopBar(
                    folders = allFolders,
                    selectedFolder = uiState.selectedFolder,
                    onFolderSelect = { viewModel.selectFolder(it) },
                    searchQuery = uiState.searchQuery,
                    isSearchActive = uiState.isSearchActive,
                    onSearchToggle = { viewModel.toggleSearch() },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    selectedTab = selectedTab,
                    onSettingsClick = onSettingsClick
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (selectedTab) {
                    0 -> StitchDashboardScreen(
                        viewModel = viewModel,
                        onNoteClick = onNoteClick,
                        onContextMenu = { note, anchor, bounds ->
                            contextTarget = ContextTarget.DashNote(note, anchor, bounds)
                            showResizePicker = false
                        }
                    )
                    1 -> StitchTreeScreen(
                        folders = if (isAssignMode) workingFolders else allFolders,
                        viewModel = viewModel,
                        isAssignMode = isAssignMode,
                        onEnterAssignMode = {
                            workingFolders = allFolders.toList()
                            workingNoteAssignments.clear()
                            workingNoteSortOrders.clear()
                            isAssignMode = true
                        },
                        onWorkingFoldersChange = { workingFolders = it },
                        onFolderClick = { folder ->
                            if (folder != null) viewModel.selectFolder(folder)
                            else viewModel.selectUncategorized()
                            selectedTab = 0
                        },
                        onNoteClick = onNoteClick,
                        noteAssignments = workingNoteAssignments,
                        onNoteAssign = { noteId, folderId -> workingNoteAssignments[noteId] = folderId },
                        onDraggingOverBar = { noteDraggingOverBar = it },
                        onNoteSortOrdersChange = { workingNoteSortOrders.putAll(it) },
                        onContextFolder = { folder, anchor, bounds ->
                            contextTarget = ContextTarget.TreeFolder(folder, anchor, bounds)
                        },
                        onContextNote = { note, anchor, bounds ->
                            contextTarget = ContextTarget.TreeNote(note, anchor, bounds)
                        }
                    )
                }
            }
        }

        // Floating island bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isAssignMode) {
                AssignModeBottomBar(
                    onAccept = {
                        val filteredNoteSortOrders = workingNoteSortOrders
                            .filter { (noteId, _) -> !workingNoteAssignments.containsKey(noteId) }
                        viewModel.saveAssignModeResult(
                            workingFolders,
                            filteredNoteSortOrders,
                            workingNoteAssignments.toMap()
                        )
                        workingNoteAssignments.clear()
                        workingNoteSortOrders.clear()
                        noteDraggingOverBar = false
                        isAssignMode = false
                    },
                    onCancel = {
                        workingNoteAssignments.clear()
                        workingNoteSortOrders.clear()
                        noteDraggingOverBar = false
                        isAssignMode = false
                    }
                )
            } else {
                IslandBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onAddClick = if (selectedTab == 1) {
                        { showAddBranchDialog = true }
                    } else {
                        onAddNoteClick
                    }
                )
            }
        }

        // ── Full-screen spotlight + radial menu ─────────────────────────────────
        // Rendered here so the dim covers the bottom bar and top bar too.
        contextTarget?.let { target ->
            val dismiss = { contextTarget = null; showResizePicker = false }
            SpotlightOverlay(cardBounds = target.bounds, onDismiss = dismiss)

            when (target) {
                is ContextTarget.DashNote -> {
                    if (!showResizePicker) {
                        NoteRadialMenu(
                            anchor      = target.anchor,
                            isPinned    = target.note.isPinned,
                            isFullWidth = target.note.isFullWidth,
                            onDelete    = { pendingDeleteNote = target.note; dismiss() },
                            onFolder    = { dismiss() },
                            onPin       = { viewModel.togglePin(target.note.id); dismiss() },
                            onResize    = { showResizePicker = true },
                            onDismiss   = dismiss
                        )
                    } else {
                        NoteResizePicker(
                            isFullWidth  = target.note.isFullWidth,
                            cardBounds   = target.bounds,
                            onSelectSize = { isFullWidth ->
                                viewModel.setNoteFullWidth(target.note.id, isFullWidth)
                                dismiss()
                            },
                            onDismiss = dismiss
                        )
                    }
                }
                is ContextTarget.TreeFolder -> {
                    FolderRadialMenu(
                        anchor   = target.anchor,
                        onRename = { pendingRenameFolder = target.folder; dismiss() },
                        onDelete = { pendingDeleteFolder = target.folder; dismiss() },
                        onDismiss = dismiss
                    )
                }
                is ContextTarget.TreeNote -> {
                    FolderRadialMenu(
                        anchor   = target.anchor,
                        onRename = { pendingRenameNote = target.note; dismiss() },
                        onDelete = { pendingDeleteNote = target.note; dismiss() },
                        onDismiss = dismiss
                    )
                }
            }
        }

        // ── Confirmation dialogs ────────────────────────────────────────────────
        pendingDeleteNote?.let { note ->
            DeleteNoteDialog(
                note      = note,
                onConfirm = { viewModel.deleteNote(note); pendingDeleteNote = null },
                onDismiss = { pendingDeleteNote = null }
            )
        }
        pendingDeleteFolder?.let { folder ->
            DeleteFolderDialog(
                folder    = folder,
                onConfirm = { viewModel.deleteFolder(folder); pendingDeleteFolder = null },
                onDismiss = { pendingDeleteFolder = null }
            )
        }
        pendingRenameFolder?.let { folder ->
            RenameFolderDialog(
                folder    = folder,
                onConfirm = { name -> viewModel.renameFolder(folder, name); pendingRenameFolder = null },
                onDismiss = { pendingRenameFolder = null }
            )
        }
        pendingRenameNote?.let { note ->
            RenameNoteDialog(
                note      = note,
                onConfirm = { name -> viewModel.renameNote(note, name); pendingRenameNote = null },
                onDismiss = { pendingRenameNote = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchTopBar(
    folders: List<Folder>,
    selectedFolder: Folder?,
    onFolderSelect: (Folder?) -> Unit,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    selectedTab: Int,
    onSettingsClick: () -> Unit = {}
) {
    val currentLevelFolders = remember(folders, selectedFolder) {
        folders.filter { it.parentId == selectedFolder?.parentId }.sortedBy { it.sortOrder }
    }
    val childFolders = remember(folders, selectedFolder) {
        if (selectedFolder != null) folders.filter { it.parentId == selectedFolder.id }.sortedBy { it.sortOrder }
        else emptyList()
    }
    val parentFolder = remember(folders, selectedFolder) {
        folders.firstOrNull { it.id == selectedFolder?.parentId }
    }
    val showAllChip = selectedFolder?.parentId == null

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onSearchToggle(); onSearchQueryChanged("") },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search notes...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = StitchGreen) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor  = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onSearchToggle,
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Search, null, tint = StitchGreen, modifier = Modifier.size(20.dp))
                }
                if (selectedTab == 0 && selectedFolder != null) {
                    IconButton(
                        onClick = { onFolderSelect(parentFolder) },
                        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = StitchGreen, modifier = Modifier.size(20.dp))
                    }
                }

                if (selectedTab == 0) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        if (showAllChip) {
                            item {
                                FolderChip(
                                    label = "All",
                                    selected = selectedFolder == null,
                                    onClick = { onFolderSelect(null) }
                                )
                            }
                        }
                        items(currentLevelFolders, key = { it.id }) { folder ->
                            FolderChip(
                                label = folder.name,
                                selected = selectedFolder?.id == folder.id,
                                onClick = { onFolderSelect(folder) }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, null, tint = StitchGreen, modifier = Modifier.size(20.dp))
                }
            }

            if (selectedTab == 0 && childFolders.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(childFolders, key = { it.id }) { child ->
                        FolderChip(label = child.name, selected = false, onClick = { onFolderSelect(child) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor   = if (selected) StitchGreen else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun AssignModeBottomBar(
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.height(90.dp).width(350.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StitchGreen, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Done, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IslandBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.height(90.dp).width(350.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { onTabSelected(0) }) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Main",
                    tint = if (selectedTab == 0) StitchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }

            FloatingActionButton(
                onClick = onAddClick,
                containerColor = StitchGreen,
                contentColor = Color.Black,
                modifier = Modifier.size(70.dp)
            ) {
                if (selectedTab == 1) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Folder", modifier = Modifier.size(35.dp))
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(35.dp))
                }
            }

            IconButton(onClick = { onTabSelected(1) }) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = "Tree",
                    tint = if (selectedTab == 1) StitchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
fun StitchDashboardScreen(
    viewModel: OxygenNotesViewModel,
    onNoteClick: (Long) -> Unit,
    onContextMenu: (Note, Offset, Rect) -> Unit = { _, _, _ -> }
) {
    val notes by viewModel.notes.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        NoteGrid(
            notes = notes,
            onNoteClick = onNoteClick,
            onContextMenu = onContextMenu
        )
    }
}

@Composable
fun StitchTreeScreen(
    folders: List<Folder>,
    viewModel: OxygenNotesViewModel,
    isAssignMode: Boolean,
    onEnterAssignMode: () -> Unit,
    onWorkingFoldersChange: (List<Folder>) -> Unit,
    onFolderClick: (Folder?) -> Unit,
    onNoteClick: (Long) -> Unit,
    noteAssignments: Map<Long, Long?> = emptyMap(),
    onNoteAssign: (Long, Long?) -> Unit = { _, _ -> },
    onDraggingOverBar: (Boolean) -> Unit = {},
    onNoteSortOrdersChange: (Map<Long, Long>) -> Unit = {},
    onContextFolder: (Folder, Offset, Rect) -> Unit = { _, _, _ -> },
    onContextNote: (Note, Offset, Rect) -> Unit = { _, _, _ -> }
) {
    val allNotes by viewModel.allNotes.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        FolderTreeComponent(
            folders = folders,
            notes = allNotes,
            isAssignMode = isAssignMode,
            onEnterAssignMode = onEnterAssignMode,
            onFolderDelete = { viewModel.deleteFolder(it) },
            onFolderRename = { folder, name -> viewModel.renameFolder(folder, name) },
            onFoldersChange = onWorkingFoldersChange,
            onFolderClick = onFolderClick,
            onNoteClick = onNoteClick,
            onNoteFolderChange = { noteId, folderId -> viewModel.setNoteFolder(noteId, folderId) },
            onNoteDelete = { viewModel.deleteNote(it) },
            onNoteRename = { note, title -> viewModel.renameNote(note, title) },
            noteAssignments = noteAssignments,
            onNoteAssign = onNoteAssign,
            onDraggingOverBar = onDraggingOverBar,
            onNoteSortOrdersChange = onNoteSortOrdersChange,
            onContextFolder = onContextFolder,
            onContextNote = onContextNote
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Branch Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddBranchDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("New Folder", color = StitchGreen, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Folder name…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = StitchGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                    cursorColor          = StitchGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            val isEmpty = name.isBlank()
            Button(
                onClick = { if (!isEmpty) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEmpty) MaterialTheme.colorScheme.surfaceVariant else StitchGreen,
                    contentColor   = if (isEmpty) StitchGreen else Color.Black
                ),
                border = if (isEmpty) BorderStroke(2.dp, StitchGreen) else null
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
