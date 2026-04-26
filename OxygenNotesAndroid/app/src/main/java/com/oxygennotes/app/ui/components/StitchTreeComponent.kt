package com.oxygennotes.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxygennotes.app.data.Folder
import com.oxygennotes.app.data.Note
import com.oxygennotes.app.ui.theme.StitchGreen
import com.oxygennotes.app.ui.theme.StitchSurfaceDark
import com.oxygennotes.app.ui.theme.StitchSurfaceVariant
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val TreeLineColor = Color(0xFF23483C)
private val FolderIconColor = Color(0xFFEAB308)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF94A3B8)
private val NoteAmber = Color(0xFFF59E0B)   // note-drop target highlight
private val LightMint = Color(0xFFADEECF)   // accent for tree icons

private sealed class RootTreeItem {
    data class FolderRoot(val folder: Folder) : RootTreeItem()
    data class NoteRoot(val note: Note) : RootTreeItem()
    val sortKey: Long get() = when (this) {
        is FolderRoot -> folder.sortOrder.toLong()
        is NoteRoot -> note.sortOrder
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FolderPanel — data holder used by Assign Mode
// ─────────────────────────────────────────────────────────────────────────────

private data class FolderTreeNode(val folder: Folder, val children: List<FolderTreeNode>)

private data class FolderPanel(val root: Folder, val subtree: List<FolderTreeNode>)

private fun buildSubtree(parentId: Long, folders: List<Folder>): List<FolderTreeNode> =
    folders.filter { it.parentId == parentId }
        .sortedBy { it.sortOrder }
        .map { FolderTreeNode(it, buildSubtree(it.id, folders)) }

private fun flattenTree(nodes: List<FolderTreeNode>, depth: Int = 0): List<Pair<Folder, Int>> {
    val result = mutableListOf<Pair<Folder, Int>>()
    for (node in nodes) {
        result.add(Pair(node.folder, depth))
        result.addAll(flattenTree(node.children, depth + 1))
    }
    return result
}

private fun buildPanels(folders: List<Folder>): List<FolderPanel> {
    val roots = folders.filter { it.parentId == null }.sortedBy { it.sortOrder }
    return roots.map { root ->
        FolderPanel(root = root, subtree = buildSubtree(root.id, folders))
    }
}

// Apply nesting: folderId becomes a child of targetId (used for root-panel hover-to-nest)
private fun applyNestUnder(folderId: Long, targetId: Long, folders: List<Folder>): List<Folder> {
    if (folderId == targetId) return folders
    var cur = folders.firstOrNull { it.id == targetId }
    while (cur != null) {
        if (cur.id == folderId) return folders
        cur = folders.firstOrNull { it.id == cur!!.parentId }
    }
    return folders.map { if (it.id == folderId) it.copy(parentId = targetId) else it }
}

// Reorder root folders; move folderId to position before insertBeforeId (null = end)
private fun applyReorderRoots(folderId: Long, insertBeforeId: Long?, folders: List<Folder>): List<Folder> {
    val roots = folders.filter { it.parentId == null }.sortedBy { it.sortOrder }.toMutableList()
    val dragged = roots.firstOrNull { it.id == folderId }?.copy(parentId = null) ?: return folders
    roots.removeAll { it.id == folderId }
    val idx = if (insertBeforeId != null)
        roots.indexOfFirst { it.id == insertBeforeId }.takeIf { it >= 0 } ?: roots.size
    else roots.size
    roots.add(idx, dragged)
    val reordered = roots.mapIndexed { i, f -> f.copy(sortOrder = i) }
    return folders.map { f -> reordered.firstOrNull { it.id == f.id } ?: f }
}

// Move any folder to a new parent (null = root), inserting before insertBeforeId (null = end).
// Covers all child drag operations: reorder within parent, re-parent to any folder, un-nest to root.
private fun applyMoveChild(
    folderId: Long,
    newParentId: Long?,
    insertBeforeId: Long?,
    folders: List<Folder>
): List<Folder> {
    if (folderId == newParentId) return folders
    var cur = folders.firstOrNull { it.id == newParentId }
    while (cur != null) {
        if (cur.id == folderId) return folders
        cur = folders.firstOrNull { it.id == cur!!.parentId }
    }
    val oldParentId = folders.firstOrNull { it.id == folderId }?.parentId
    val movedFolder = folders.firstOrNull { it.id == folderId }?.copy(parentId = newParentId) ?: return folders

    val oldSiblings = folders.filter { it.parentId == oldParentId && it.id != folderId }
        .sortedBy { it.sortOrder }
        .mapIndexed { i, f -> f.copy(sortOrder = i) }

    val newSiblings = folders.filter { it.parentId == newParentId && it.id != folderId }
        .sortedBy { it.sortOrder }
        .toMutableList()
    val insertIdx = if (insertBeforeId != null)
        newSiblings.indexOfFirst { it.id == insertBeforeId }.takeIf { it >= 0 } ?: newSiblings.size
    else newSiblings.size
    newSiblings.add(insertIdx, movedFolder)
    val newSiblingsReordered = newSiblings.mapIndexed { i, f -> f.copy(sortOrder = i) }

    val updates = mutableMapOf<Long, Folder>()
    oldSiblings.forEach { updates[it.id] = it }
    newSiblingsReordered.forEach { updates[it.id] = it }
    return folders.map { updates[it.id] ?: it }
}

// Returns all descendant folder ids of rootId (not including rootId itself)
private fun getAllSubtreeIds(rootId: Long, folders: List<Folder>): Set<Long> {
    val result = mutableSetOf<Long>()
    val queue = ArrayDeque<Long>()
    queue.add(rootId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        folders.filter { it.parentId == current }.forEach {
            result.add(it.id)
            queue.add(it.id)
        }
    }
    return result
}

// Assigns sortOrder to root folders based on their position in rootOrder.
private fun syncFolderSortOrders(rootOrder: List<String>, folders: List<Folder>): List<Folder> {
    val orderMap = rootOrder.mapIndexedNotNull { idx, key ->
        key.removePrefix("f_").toLongOrNull()?.let { it to idx }
    }.toMap()
    return folders.map { f ->
        val newOrder = orderMap[f.id]
        if (newOrder != null && f.parentId == null) f.copy(sortOrder = newOrder) else f
    }
}

// Returns global-index sortOrders for all notes in rootOrder (e.g. ["f_1","n_5","f_2"] → {5L → 1L}).
// Using the global index (not a timestamp) keeps note and folder sortOrders on the same scale.
private fun rootOrderToNoteSortOrders(rootOrder: List<String>): Map<Long, Long> =
    rootOrder.mapIndexedNotNull { globalIdx, key ->
        if (key.startsWith("n_")) key.removePrefix("n_").toLongOrNull()?.let { id -> id to globalIdx.toLong() }
        else null
    }.toMap()

// ─────────────────────────────────────────────────────────────────────────────
// FolderTreeComponent — top-level entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderTreeComponent(
    folders: List<Folder>,
    notes: List<Note>,
    isAssignMode: Boolean,
    onEnterAssignMode: () -> Unit,
    onFolderDelete: (Folder) -> Unit,
    onFolderRename: (Folder, String) -> Unit,
    onFoldersChange: (List<Folder>) -> Unit,
    onFolderClick: (Folder?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onNoteFolderChange: (noteId: Long, folderId: Long?) -> Unit,
    onNoteDelete: (Note) -> Unit = {},
    onNoteRename: (Note, String) -> Unit = { _, _ -> },
    noteAssignments: Map<Long, Long?> = emptyMap(),
    onNoteAssign: (Long, Long?) -> Unit = { _, _ -> },
    onDraggingOverBar: (Boolean) -> Unit = {},
    onNoteSortOrdersChange: (Map<Long, Long>) -> Unit = {},
    onContextFolder: (folder: Folder, anchor: Offset, bounds: Rect) -> Unit = { _, _, _ -> },
    onContextNote: (note: Note, anchor: Offset, bounds: Rect) -> Unit = { _, _, _ -> }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAssignMode) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header row: title + Assign button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Branches",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onEnterAssignMode,
                        shape = RoundedCornerShape(10.dp),
                        color = StitchSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, null, tint = StitchGreen, modifier = Modifier.size(16.dp))
                            Text("Assign", color = StitchGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                FolderTreeView(
                    folders = folders,
                    notes = notes,
                    onFolderClick = onFolderClick,
                    onNoteClick = onNoteClick,
                    onLongPress = { folder, anchor, bounds ->
                        onContextFolder(folder, anchor, bounds)
                    },
                    onNoteLongPress = { note, anchor, bounds ->
                        onContextNote(note, anchor, bounds)
                    }
                )
            }
        } else {
            AssignModeList(
                folders = folders,
                notes = notes,
                noteAssignments = noteAssignments,
                onFoldersChange = onFoldersChange,
                onNoteAssign = onNoteAssign,
                onDraggingOverBar = onDraggingOverBar,
                onNoteSortOrdersChange = onNoteSortOrdersChange
            )
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FolderTreeView — scrollable recursive tree (normal mode)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderTreeView(
    folders: List<Folder>,
    notes: List<Note>,
    onFolderClick: (Folder?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onLongPress: (folder: Folder, anchor: Offset, bounds: Rect) -> Unit,
    onNoteLongPress: (note: Note, anchor: Offset, bounds: Rect) -> Unit = { _, _, _ -> }
) {
    val rootItems = remember(folders, notes) {
        val items = mutableListOf<RootTreeItem>()
        folders.filter { it.parentId == null }.forEach { items.add(RootTreeItem.FolderRoot(it)) }
        notes.filter { it.folderId == null }.forEach { items.add(RootTreeItem.NoteRoot(it)) }
        items.sortedBy { it.sortKey }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (rootItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No folders yet.\nTap + to create one.",
                    color = TextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            rootItems.forEach { item ->
                when (item) {
                    is RootTreeItem.FolderRoot -> FolderTreeItem(
                        folder = item.folder,
                        allFolders = folders,
                        allNotes = notes,
                        onFolderClick = onFolderClick,
                        onNoteClick = onNoteClick,
                        onLongPress = onLongPress,
                        onNoteLongPress = onNoteLongPress
                    )
                    is RootTreeItem.NoteRoot -> NoteTreeItem(
                        note = item.note,
                        onClick = { onNoteClick(item.note.id) },
                        onLongPress = onNoteLongPress
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FolderTreeItem — recursive, with animated expand/collapse, vertical lines,
// and notes listed under each folder when expanded
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTreeItem(
    folder: Folder,
    allFolders: List<Folder>,
    allNotes: List<Note>,
    onFolderClick: (Folder?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onLongPress: (folder: Folder, anchor: Offset, bounds: Rect) -> Unit,
    onNoteLongPress: (note: Note, anchor: Offset, bounds: Rect) -> Unit = { _, _, _ -> }
) {
    val children = remember(allFolders, folder.id) {
        allFolders.filter { it.parentId == folder.id }.sortedBy { it.sortOrder }
    }
    val folderNotes = remember(allNotes, folder.id) {
        allNotes.filter { it.folderId == folder.id }
    }
    val hasContent = children.isNotEmpty() || folderNotes.isNotEmpty()

    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200),
        label = "chevron"
    )
    var rowBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    rowBoundsInRoot = Rect(
                        offset = pos,
                        size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                    )
                }
                .combinedClickable(
                    onClick = { onFolderClick(folder) },
                    onLongClick = { onLongPress(folder, rowBoundsInRoot.center, rowBoundsInRoot) }
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { if (hasContent) expanded = !expanded }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasContent) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(30.dp).rotate(chevronAngle)
                    )
                }
            }
            Icon(
                imageVector = if (expanded && hasContent) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = StitchGreen,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = folder.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 28.dp)
                    .drawBehind {
                        drawLine(
                            color = TreeLineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(start = 8.dp)
            ) {
                Column {
                    children.forEach { child ->
                        FolderTreeItem(
                            folder = child,
                            allFolders = allFolders,
                            allNotes = allNotes,
                            onFolderClick = onFolderClick,
                            onNoteClick = onNoteClick,
                            onLongPress = onLongPress,
                            onNoteLongPress = onNoteLongPress
                        )
                    }
                    folderNotes.forEach { note ->
                        NoteTreeItem(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onLongPress = onNoteLongPress
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NoteTreeItem — leaf row in the tree (no expand)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteTreeItem(
    note: Note,
    onClick: () -> Unit,
    onLongPress: ((note: Note, anchor: Offset, bounds: Rect) -> Unit)? = null
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                rowBounds = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPress?.invoke(note, rowBounds.center, rowBounds) }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(66.dp)) // same width as folder chevron box, empty since notes have no children
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = StitchGreen,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = note.title.ifBlank { "(Untitled)" },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UncategorizedTreeSection — shows notes with no folder at the bottom of tree
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UncategorizedTreeSection(
    notes: List<Note>,
    onFolderClick: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onNoteLongPress: ((Note, Offset, Rect) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(true) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200),
        label = "uncategorized_chevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFolderClick() }
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { expanded = !expanded }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp).rotate(chevronAngle)
                )
            }
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("Default", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("(${notes.size})", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 28.dp)
                    .drawBehind {
                        drawLine(
                            color = TreeLineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(start = 8.dp)
            ) {
                Column {
                    notes.forEach { note ->
                        NoteTreeItem(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onLongPress = onNoteLongPress
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FolderRadialMenu — Rename (315°) + Delete (225°), same style as Dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderRadialMenu(
    anchor: Offset,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 90.dp.toPx() }
    val halfBtn = with(density) { 28.dp.toPx() }

    fun radialOffset(angleDeg: Double): IntOffset {
        val rad = Math.toRadians(angleDeg)
        return IntOffset(
            x = (anchor.x + radiusPx * cos(rad) - halfBtn).roundToInt(),
            y = (anchor.y + radiusPx * sin(rad) - halfBtn).roundToInt()
        )
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(160),
        label = "radialScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        RadialButton(Icons.Default.Delete, "Delete", Color(0xFFDC2626), Color.White, scale, radialOffset(225.0), onDelete)
        RadialButton(Icons.Default.Edit, "Rename", StitchGreen, Color.Black, scale, radialOffset(315.0), onRename)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AssignModeList — panel-based drag UI
//
// Root panels: long-press ⇱ in header → drag to nest (green dashed) or reorder.
// Child folders: long-press ⇱ in row → reorder siblings or drag outside → un-nest.
// Notes: long-press ⇱ in row → drag to a different panel to reassign folder
//        (amber dashed border on target panel), or drag to "Default" panel → uncategorize.
// Chevron ›: tap to expand/collapse panel content (subfolders + notes together).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AssignModeList(
    folders: List<Folder>,
    notes: List<Note>,
    noteAssignments: Map<Long, Long?> = emptyMap(),
    onFoldersChange: (List<Folder>) -> Unit,
    onNoteAssign: (Long, Long?) -> Unit = { _, _ -> },
    onDraggingOverBar: (Boolean) -> Unit = {},
    onNoteSortOrdersChange: (Map<Long, Long>) -> Unit = {}
) {
    fun effectiveFolderId(noteId: Long, originalFolderId: Long?): Long? =
        if (noteAssignments.containsKey(noteId)) noteAssignments[noteId] else originalFolderId

    val isLightTheme = MaterialTheme.colorScheme.surface == Color(0xFFFFFFFF)
    val panelSurfaceColor = if (isLightTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant

    var workingFolders by remember { mutableStateOf(folders) }
    val expandedPanels = remember { mutableStateMapOf<Long, Boolean>() }

    // ── Unified root ordering (folder panels + uncategorized note panels) ────
    val rootOrder = remember { mutableStateListOf<String>() }
    LaunchedEffect(workingFolders, notes, noteAssignments) {
        val rootFolderKeys = buildPanels(workingFolders).map { "f_${it.root.id}" }.toSet()
        val rootNoteKeys = notes.filter { note ->
            if (noteAssignments.containsKey(note.id)) noteAssignments[note.id] == null
            else note.folderId == null
        }.map { "n_${it.id}" }.toSet()
        val validKeys = rootFolderKeys + rootNoteKeys
        rootOrder.removeAll { it !in validKeys }
        val newKeys = validKeys - rootOrder.toSet()
        if (newKeys.isNotEmpty()) {
            val sortOrderOf = buildMap<String, Long> {
                workingFolders.forEach { put("f_${it.id}", it.sortOrder.toLong()) }
                notes.forEach { put("n_${it.id}", it.sortOrder) }
            }
            val newKeysOrdered = newKeys.sortedBy { sortOrderOf[it] ?: Long.MAX_VALUE }
            for (key in newKeysOrdered) {
                val so = sortOrderOf[key] ?: Long.MAX_VALUE
                val idx = rootOrder.indexOfFirst { (sortOrderOf[it] ?: Long.MAX_VALUE) > so }
                if (idx == -1) rootOrder.add(key) else rootOrder.add(idx, key)
            }
        }
    }

    // ── Root item drag (folder panels and root note panels) ──────────────────
    var draggingRootKey by remember { mutableStateOf<String?>(null) }
    var ghostRootY by remember { mutableStateOf(0f) }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    var insertBeforeRootKey by remember { mutableStateOf<String?>(null) }
    val panelBounds = remember { mutableStateMapOf<Long, Rect>() }
    val rootItemBounds = remember { mutableStateMapOf<String, Rect>() }

    // ── Child folder drag ────────────────────────────────────────────────────
    var draggingChildId by remember { mutableStateOf<Long?>(null) }
    var ghostChildY by remember { mutableStateOf(0f) }
    var childNestTargetId by remember { mutableStateOf<Long?>(null) }
    var childInsertParentId by remember { mutableStateOf<Long?>(null) }
    var childInsertBeforeId by remember { mutableStateOf<Long?>(null) }
    val childBounds = remember { mutableStateMapOf<Long, Rect>() }
    val rootHeaderBounds = remember { mutableStateMapOf<Long, Rect>() }

    // ── Note drag (notes inside folder panels → reassign folder) ─────────────
    var draggingNoteId by remember { mutableStateOf<Long?>(null) }
    var ghostNoteY by remember { mutableStateOf(0f) }
    var noteHoveredPanelId by remember { mutableStateOf<Long?>(null) }
    val noteBounds = remember { mutableStateMapOf<Long, Rect>() }

    var listTopY by remember { mutableStateOf(0f) }
    var listBoxHeight by remember { mutableStateOf(0f) }
    var noteHoveredDefault by remember { mutableStateOf(false) }
    val latestFolders = rememberUpdatedState(workingFolders)
    val latestOnNoteAssign = rememberUpdatedState(onNoteAssign)
    val latestOnDraggingOverBar = rememberUpdatedState(onDraggingOverBar)
    val latestOnNoteSortOrdersChange = rememberUpdatedState(onNoteSortOrdersChange)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.OpenWith, null, tint = StitchGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Assign Mode", color = StitchGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("Hold ⇱ to drag", color = TextMuted, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    listTopY = it.positionInRoot().y
                    listBoxHeight = it.size.height.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val panels = buildPanels(workingFolders)
                val uncategorizedNotes = notes.filter { note ->
                    if (noteAssignments.containsKey(note.id)) noteAssignments[note.id] == null
                    else note.folderId == null
                }

                rootOrder.forEach { itemKey ->
                    key(itemKey) {
                        if (itemKey.startsWith("f_")) {
                            val folderId = itemKey.removePrefix("f_").toLongOrNull()
                            val panel = if (folderId != null) panels.firstOrNull { it.root.id == folderId } else null
                            if (folderId != null && panel != null) {
                        val isExpanded = expandedPanels[panel.root.id] ?: true
                        val chevronAngle by animateFloatAsState(
                            targetValue = if (isExpanded) 90f else 0f,
                            animationSpec = tween(200),
                            label = "chevron"
                        )
                        val isDraggingRoot = draggingRootKey == itemKey
                        val isFolderDropTarget = dropTargetId == panel.root.id ||
                            (childNestTargetId == panel.root.id && draggingChildId != null)
                        val isNoteDropTarget = noteHoveredPanelId == panel.root.id &&
                            (draggingNoteId != null || draggingRootKey?.startsWith("n_") == true)
                        val panelNotes = notes.filter { note ->
                            if (noteAssignments.containsKey(note.id)) noteAssignments[note.id] == panel.root.id
                            else note.folderId == panel.root.id
                        }
                        val hasContent = panel.subtree.isNotEmpty() || panelNotes.isNotEmpty()

                        val showInsertAbove =
                            (insertBeforeRootKey == itemKey && draggingRootKey != null) ||
                            (childInsertParentId == null && childInsertBeforeId == panel.root.id && draggingChildId != null && childNestTargetId == null)
                        if (showInsertAbove) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(StitchGreen, RoundedCornerShape(1.dp)))
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val rect = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    panelBounds[panel.root.id] = rect
                                    rootItemBounds[itemKey] = rect
                                }
                                .graphicsLayer { alpha = if (isDraggingRoot) 0.15f else 1f }
                                .then(when {
                                    isFolderDropTarget -> Modifier.drawWithContent {
                                        drawContent()
                                        drawRoundRect(
                                            color = StitchGreen,
                                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 7f), 0f)),
                                            cornerRadius = CornerRadius(16.dp.toPx())
                                        )
                                    }
                                    isNoteDropTarget -> Modifier.drawWithContent {
                                        drawContent()
                                        drawRoundRect(
                                            color = StitchGreen,
                                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)),
                                            cornerRadius = CornerRadius(16.dp.toPx())
                                        )
                                    }
                                    else -> Modifier
                                }),
                            shape = RoundedCornerShape(16.dp),
                            color = panelSurfaceColor
                        ) {
                            Column {
                                // ── Panel header ─────────────────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            rootHeaderBounds[panel.root.id] = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                        }
                                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Chevron (expand/collapse)
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .then(
                                                if (hasContent) Modifier.clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    expandedPanels[panel.root.id] = !isExpanded
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hasContent) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(30.dp).rotate(chevronAngle)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isExpanded && hasContent) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = StitchGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        panel.root.name,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Root drag handle
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .pointerInput(itemKey) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { _ ->
                                                        if (draggingChildId == null && draggingNoteId == null) {
                                                            draggingRootKey = itemKey
                                                            ghostRootY = rootItemBounds[itemKey]?.top ?: panelBounds[panel.root.id]?.top ?: 0f
                                                        }
                                                    },
                                                    onDrag = { change, delta ->
                                                        if (draggingRootKey == itemKey) {
                                                            change.consume()
                                                            ghostRootY += delta.y
                                                            val itemH = rootItemBounds[itemKey]?.height ?: 0f
                                                            val ghostCenterY = ghostRootY + itemH / 2f

                                                            var nested: Long? = null
                                                            for ((fId, b) in panelBounds) {
                                                                if (fId == panel.root.id) continue
                                                                val nestTop = b.top + b.height * 0.25f
                                                                val nestBot = b.bottom - b.height * 0.25f
                                                                if (ghostCenterY in nestTop..nestBot) {
                                                                    nested = fId; break
                                                                }
                                                            }
                                                            if (nested != null) {
                                                                dropTargetId = nested
                                                                insertBeforeRootKey = null
                                                            } else {
                                                                dropTargetId = null
                                                                var foundBefore: String? = null
                                                                val sortedItems = rootOrder
                                                                    .filter { it != itemKey }
                                                                    .mapNotNull { k -> rootItemBounds[k]?.let { b -> k to b } }
                                                                    .sortedBy { (_, b) -> b.top }
                                                                for ((k, b) in sortedItems) {
                                                                    if (ghostCenterY < b.top + b.height / 2f) {
                                                                        foundBefore = k; break
                                                                    }
                                                                }
                                                                insertBeforeRootKey = foundBefore
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (draggingRootKey == itemKey) {
                                                            if (dropTargetId != null) {
                                                                val updated = applyNestUnder(panel.root.id, dropTargetId!!, latestFolders.value)
                                                                workingFolders = updated
                                                                onFoldersChange(updated)
                                                                rootOrder.remove(itemKey)
                                                            } else {
                                                                val fromIdx = rootOrder.indexOf(itemKey)
                                                                if (fromIdx >= 0) {
                                                                    rootOrder.removeAt(fromIdx)
                                                                    val toIdx = if (insertBeforeRootKey != null) {
                                                                        val idx = rootOrder.indexOf(insertBeforeRootKey)
                                                                        if (idx >= 0) idx else rootOrder.size
                                                                    } else rootOrder.size
                                                                    rootOrder.add(toIdx, itemKey)
                                                                }
                                                                val updated = syncFolderSortOrders(rootOrder, latestFolders.value)
                                                                workingFolders = updated
                                                                onFoldersChange(updated)
                                                                // notes may have shifted position relative to folders — save their global indices
                                                                latestOnNoteSortOrdersChange.value(rootOrderToNoteSortOrders(rootOrder))
                                                            }
                                                        }
                                                        draggingRootKey = null; dropTargetId = null; insertBeforeRootKey = null
                                                    },
                                                    onDragCancel = {
                                                        draggingRootKey = null; dropTargetId = null; insertBeforeRootKey = null
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.OpenWith, "Drag panel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                                    }
                                }

                                // ── Collapsible content (subfolders + notes) ──
                                AnimatedVisibility(
                                    visible = isExpanded && hasContent,
                                    enter = expandVertically(tween(200)),
                                    exit = shrinkVertically(tween(200))
                                ) {
                                    Column(modifier = Modifier.padding(bottom = 6.dp)) {

                                        // ── Subfolder rows (recursive, any depth) ─
                                        val flatChildren = flattenTree(panel.subtree)
                                        if (flatChildren.isNotEmpty()) {
                                            HorizontalDivider(
                                                color = TreeLineColor.copy(alpha = 0.5f),
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )

                                            // For each ancestor id, track the last flat index of any of its descendants
                                            val subtreeEndIndex = mutableMapOf<Long, Int>()
                                            flatChildren.forEachIndexed { i, (f, _) ->
                                                var anc: Long? = f.parentId
                                                while (anc != null) {
                                                    subtreeEndIndex[anc] = i
                                                    anc = latestFolders.value.firstOrNull { it.id == anc }?.parentId
                                                }
                                            }

                                            flatChildren.forEachIndexed { flatIdx, (child, depth) ->
                                                val isDraggingThisChild = draggingChildId == child.id
                                                val isNestTarget = childNestTargetId == child.id && draggingChildId != null
                                                val isNoteChildTarget = noteHoveredPanelId == child.id &&
                                                    (draggingNoteId != null || draggingRootKey?.startsWith("n_") == true)
                                                val showInsertAboveChild =
                                                    childNestTargetId == null && draggingChildId != null &&
                                                    childInsertParentId == child.parentId &&
                                                    childInsertBeforeId == child.id
                                                val startPadding = (16 + depth * 24).dp

                                                if (showInsertAboveChild) {
                                                    Box(
                                                        Modifier.fillMaxWidth()
                                                            .padding(start = startPadding)
                                                            .height(2.dp)
                                                            .background(StitchGreen, RoundedCornerShape(1.dp))
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .onGloballyPositioned { coords ->
                                                            val pos = coords.positionInRoot()
                                                            childBounds[child.id] = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                        }
                                                        .graphicsLayer { alpha = if (isDraggingThisChild) 0.15f else 1f }
                                                        .then(when {
                                                            isNestTarget -> Modifier.drawWithContent {
                                                                drawContent()
                                                                drawRoundRect(
                                                                    color = StitchGreen,
                                                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)),
                                                                    cornerRadius = CornerRadius(6.dp.toPx())
                                                                )
                                                            }
                                                            isNoteChildTarget -> Modifier.drawWithContent {
                                                                drawContent()
                                                                drawRoundRect(
                                                                    color = StitchGreen,
                                                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)),
                                                                    cornerRadius = CornerRadius(6.dp.toPx())
                                                                )
                                                            }
                                                            else -> Modifier
                                                        })
                                                        .padding(start = startPadding, end = 4.dp, top = 9.dp, bottom = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Folder, null,
                                                        tint = StitchGreen.copy(alpha = if (depth == 0) 0.65f else 0.45f),
                                                        modifier = Modifier.size(if (depth == 0) 24.dp else 21.dp)
                                                    )
                                                    Text(child.name, color = TextMuted, fontSize = 19.sp, modifier = Modifier.weight(1f))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(60.dp)
                                                            .pointerInput(child.id) {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = { _ ->
                                                                        if (draggingRootKey == null && draggingNoteId == null) {
                                                                            draggingChildId = child.id
                                                                            ghostChildY = childBounds[child.id]?.top ?: 0f
                                                                            childNestTargetId = null
                                                                            childInsertParentId = child.parentId
                                                                            childInsertBeforeId = null
                                                                        }
                                                                    },
                                                                    onDrag = { change, delta ->
                                                                        if (draggingChildId == child.id) {
                                                                            change.consume()
                                                                            ghostChildY += delta.y
                                                                            val childH = childBounds[child.id]?.height ?: 0f
                                                                            val ghostCenterY = ghostChildY + childH / 2f
                                                                            val currentFolders = latestFolders.value

                                                                            // Check for nest target: middle 50% of any root header or child row
                                                                            var nestTarget: Long? = null
                                                                            for ((rootId, b) in rootHeaderBounds) {
                                                                                if (rootId == child.id) continue
                                                                                if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                                    nestTarget = rootId; break
                                                                                }
                                                                            }
                                                                            if (nestTarget == null) {
                                                                                for ((rowId, b) in childBounds) {
                                                                                    if (rowId == child.id) continue
                                                                                    // Skip descendants of the dragged folder
                                                                                    var isDesc = false
                                                                                    var anc = currentFolders.firstOrNull { it.id == rowId }?.parentId
                                                                                    while (anc != null) {
                                                                                        if (anc == child.id) { isDesc = true; break }
                                                                                        anc = currentFolders.firstOrNull { it.id == anc }?.parentId
                                                                                    }
                                                                                    if (isDesc) continue
                                                                                    if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                                        nestTarget = rowId; break
                                                                                    }
                                                                                }
                                                                            }
                                                                            childNestTargetId = nestTarget

                                                                            if (nestTarget == null) {
                                                                                // Determine insert parent + before-id
                                                                                var foundPanel: Long? = null
                                                                                for ((rootId, b) in panelBounds) {
                                                                                    if (ghostCenterY in b.top..b.bottom) { foundPanel = rootId; break }
                                                                                }
                                                                                if (foundPanel == null) {
                                                                                    // Outside all panels → promote to root
                                                                                    childInsertParentId = null
                                                                                    val currentPanels = buildPanels(currentFolders)
                                                                                    var foundBefore: Long? = null
                                                                                    for (p in currentPanels) {
                                                                                        val b = panelBounds[p.root.id] ?: continue
                                                                                        if (ghostCenterY < b.top + b.height / 2f) { foundBefore = p.root.id; break }
                                                                                    }
                                                                                    childInsertBeforeId = foundBefore
                                                                                } else {
                                                                                    // Inside a panel: pick insert slot from visible rows
                                                                                    val subtreeIds = getAllSubtreeIds(foundPanel, currentFolders)
                                                                                    val rows = childBounds
                                                                                        .filter { (id, _) -> subtreeIds.contains(id) && id != child.id }
                                                                                        .entries.sortedBy { (_, b) -> b.top }
                                                                                    var tParent: Long? = foundPanel
                                                                                    var iBefore: Long? = null
                                                                                    for (ri in rows.indices) {
                                                                                        val (rowId, rowB) = rows[ri]
                                                                                        val rowFolder = currentFolders.firstOrNull { it.id == rowId } ?: continue
                                                                                        if (ghostCenterY < rowB.top + rowB.height / 2f) {
                                                                                            tParent = rowFolder.parentId; iBefore = rowId; break
                                                                                        }
                                                                                        if (ri == rows.lastIndex) {
                                                                                            tParent = rowFolder.parentId; iBefore = null
                                                                                        }
                                                                                    }
                                                                                    childInsertParentId = tParent
                                                                                    childInsertBeforeId = iBefore
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                    onDragEnd = {
                                                                        val draggedId = draggingChildId
                                                                        if (draggedId != null) {
                                                                            val updated = if (childNestTargetId != null)
                                                                                applyMoveChild(draggedId, childNestTargetId, null, latestFolders.value)
                                                                            else
                                                                                applyMoveChild(draggedId, childInsertParentId, childInsertBeforeId, latestFolders.value)
                                                                            workingFolders = updated
                                                                            onFoldersChange(updated)
                                                                        }
                                                                        draggingChildId = null; childNestTargetId = null
                                                                        childInsertParentId = null; childInsertBeforeId = null
                                                                    },
                                                                    onDragCancel = {
                                                                        draggingChildId = null; childNestTargetId = null
                                                                        childInsertParentId = null; childInsertBeforeId = null
                                                                    }
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.OpenWith, "Drag child", tint = TextMuted.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                                                    }
                                                }

                                                // Notes inside this subfolder
                                                val subNotes = notes.filter { note ->
                                                    if (noteAssignments.containsKey(note.id)) noteAssignments[note.id] == child.id
                                                    else note.folderId == child.id
                                                }
                                                subNotes.forEach { subNote ->
                                                    val isDraggingThisSubNote = draggingNoteId == subNote.id
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .onGloballyPositioned { coords ->
                                                                val pos = coords.positionInRoot()
                                                                noteBounds[subNote.id] = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                            }
                                                            .graphicsLayer { alpha = if (isDraggingThisSubNote) 0.15f else 1f }
                                                            .pointerInput(subNote.id) {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = { _ ->
                                                                        if (draggingRootKey == null && draggingChildId == null) {
                                                                            draggingNoteId = subNote.id
                                                                            ghostNoteY = noteBounds[subNote.id]?.top ?: 0f
                                                                            noteHoveredPanelId = null
                                                                        }
                                                                    },
                                                                    onDrag = { change, delta ->
                                                                        if (draggingNoteId == subNote.id) {
                                                                            change.consume()
                                                                            ghostNoteY += delta.y
                                                                            val noteH = noteBounds[subNote.id]?.height ?: 48f
                                                                            val ghostCenterY = ghostNoteY + noteH / 2f
                                                                            var found: Long? = null
                                                                            for ((panelId, b) in panelBounds) {
                                                                                if (ghostCenterY >= b.top && ghostCenterY <= b.bottom) {
                                                                                    found = panelId; break
                                                                                }
                                                                            }
                                                                            if (found == null) {
                                                                                for ((childFolderId, b) in childBounds) {
                                                                                    if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                                        found = childFolderId; break
                                                                                    }
                                                                                }
                                                                            }
                                                                            noteHoveredPanelId = found
                                                                            val inCurrentPanel = panelBounds[panel.root.id]?.let { b ->
                                                                                ghostCenterY >= b.top && ghostCenterY <= b.bottom
                                                                            } ?: false
                                                                            val newDefault = found == null && !inCurrentPanel
                                                                            if (newDefault != noteHoveredDefault) {
                                                                                noteHoveredDefault = newDefault
                                                                                latestOnDraggingOverBar.value(newDefault)
                                                                            }
                                                                        }
                                                                    },
                                                                    onDragEnd = {
                                                                        val nId = draggingNoteId
                                                                        val targetId = noteHoveredPanelId
                                                                        val toDefault = noteHoveredDefault
                                                                        if (nId != null && targetId != null) {
                                                                            latestOnNoteAssign.value(nId, targetId)
                                                                        } else if (nId != null && toDefault) {
                                                                            latestOnNoteAssign.value(nId, null)
                                                                            val nKey = "n_$nId"
                                                                            if (nKey !in rootOrder) rootOrder.add(nKey)
                                                                        }
                                                                        if (toDefault) latestOnDraggingOverBar.value(false)
                                                                        draggingNoteId = null; noteHoveredPanelId = null; noteHoveredDefault = false
                                                                    },
                                                                    onDragCancel = {
                                                                        if (noteHoveredDefault) latestOnDraggingOverBar.value(false)
                                                                        draggingNoteId = null; noteHoveredPanelId = null; noteHoveredDefault = false
                                                                    }
                                                                )
                                                            }
                                                            .padding(start = startPadding + 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Description, null, tint = StitchGreen.copy(alpha = 0.6f), modifier = Modifier.size(19.dp))
                                                        Text(
                                                            text = subNote.title.ifBlank { "(Untitled)" },
                                                            color = TextMuted,
                                                            fontSize = 18.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(Icons.Default.OpenWith, null, tint = StitchGreen.copy(alpha = 0.4f), modifier = Modifier.size(21.dp))
                                                    }
                                                }

                                                // End-of-group insert line: after the last descendant of childInsertParentId
                                                if (childNestTargetId == null && childInsertBeforeId == null &&
                                                    childInsertParentId != null && draggingChildId != null &&
                                                    subtreeEndIndex[childInsertParentId] == flatIdx) {
                                                    val lineDepth = if (childInsertParentId == panel.root.id) 0
                                                        else (flatChildren.firstOrNull { (f, _) -> f.id == childInsertParentId }?.second?.plus(1) ?: 0)
                                                    Box(
                                                        Modifier.fillMaxWidth()
                                                            .padding(start = (16 + lineDepth * 24).dp)
                                                            .height(2.dp)
                                                            .background(StitchGreen, RoundedCornerShape(1.dp))
                                                    )
                                                }
                                            }
                                        }

                                        // ── Note rows ─────────────────────────
                                        if (panelNotes.isNotEmpty()) {
                                            HorizontalDivider(
                                                color = TreeLineColor.copy(alpha = 0.3f),
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                            panelNotes.forEach { note ->
                                                val isDraggingThisNote = draggingNoteId == note.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .onGloballyPositioned { coords ->
                                                            val pos = coords.positionInRoot()
                                                            noteBounds[note.id] = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                                        }
                                                        .graphicsLayer { alpha = if (isDraggingThisNote) 0.15f else 1f }
                                                        .pointerInput(note.id) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { _ ->
                                                                    if (draggingRootKey == null && draggingChildId == null) {
                                                                        draggingNoteId = note.id
                                                                        ghostNoteY = noteBounds[note.id]?.top ?: 0f
                                                                        noteHoveredPanelId = null
                                                                    }
                                                                },
                                                                onDrag = { change, delta ->
                                                                    if (draggingNoteId == note.id) {
                                                                        change.consume()
                                                                        ghostNoteY += delta.y
                                                                        val noteH = noteBounds[note.id]?.height ?: 48f
                                                                        val ghostCenterY = ghostNoteY + noteH / 2f
                                                                        var found: Long? = null
                                                                        for ((panelId, b) in panelBounds) {
                                                                            if (panelId == panel.root.id) continue
                                                                            if (ghostCenterY >= b.top && ghostCenterY <= b.bottom) {
                                                                                found = panelId; break
                                                                            }
                                                                        }
                                                                        if (found == null) {
                                                                            for ((childId, b) in childBounds) {
                                                                                if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                                    found = childId; break
                                                                                }
                                                                            }
                                                                        }
                                                                        noteHoveredPanelId = found
                                                                        val inCurrentPanel = panelBounds[panel.root.id]?.let { b ->
                                                                            ghostCenterY >= b.top && ghostCenterY <= b.bottom
                                                                        } ?: false
                                                                        val newDefault = found == null && !inCurrentPanel
                                                                        if (newDefault != noteHoveredDefault) {
                                                                            noteHoveredDefault = newDefault
                                                                            latestOnDraggingOverBar.value(newDefault)
                                                                        }
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    val noteId = draggingNoteId
                                                                    val targetId = noteHoveredPanelId
                                                                    val toDefault = noteHoveredDefault
                                                                    if (noteId != null && targetId != null) {
                                                                        latestOnNoteAssign.value(noteId, targetId)
                                                                    } else if (noteId != null && toDefault) {
                                                                        latestOnNoteAssign.value(noteId, null)
                                                                        val nKey = "n_$noteId"
                                                                        if (nKey !in rootOrder) rootOrder.add(nKey)
                                                                    }
                                                                    if (toDefault) latestOnDraggingOverBar.value(false)
                                                                    draggingNoteId = null; noteHoveredPanelId = null; noteHoveredDefault = false
                                                                },
                                                                onDragCancel = {
                                                                    if (noteHoveredDefault) latestOnDraggingOverBar.value(false)
                                                                    draggingNoteId = null; noteHoveredPanelId = null; noteHoveredDefault = false
                                                                }
                                                            )
                                                        }
                                                        .padding(start = 60.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Description, null, tint = StitchGreen.copy(alpha = 0.6f), modifier = Modifier.size(21.dp))
                                                    Text(
                                                        text = note.title.ifBlank { "(Untitled)" },
                                                        color = TextMuted,
                                                        fontSize = 19.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(Icons.Default.OpenWith, null, tint = StitchGreen.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                            } // closes if (folderId != null && panel != null)
                        } else if (itemKey.startsWith("n_")) {
                            val noteId = itemKey.removePrefix("n_").toLongOrNull()
                            val note = if (noteId != null) uncategorizedNotes.firstOrNull { it.id == noteId } else null
                            if (noteId != null && note != null) {
                                val isDraggingThisNote = draggingRootKey == itemKey

                                if (insertBeforeRootKey == itemKey && draggingRootKey != null) {
                                    Box(Modifier.fillMaxWidth().height(2.dp).background(StitchGreen, RoundedCornerShape(1.dp)))
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            rootItemBounds[itemKey] = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                        }
                                        .graphicsLayer { alpha = if (isDraggingThisNote) 0.15f else 1f },
                                    shape = RoundedCornerShape(16.dp),
                                    color = panelSurfaceColor
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.size(60.dp))
                                        Icon(Icons.Default.Description, null, tint = StitchGreen, modifier = Modifier.size(30.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = note.title.ifBlank { "(Untitled)" },
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .pointerInput(itemKey) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { _ ->
                                                            if (draggingChildId == null && draggingNoteId == null) {
                                                                draggingRootKey = itemKey
                                                                ghostRootY = rootItemBounds[itemKey]?.top ?: 0f
                                                                noteHoveredPanelId = null
                                                            }
                                                        },
                                                        onDrag = { change, delta ->
                                                            if (draggingRootKey == itemKey) {
                                                                change.consume()
                                                                ghostRootY += delta.y
                                                                val noteH = rootItemBounds[itemKey]?.height ?: 48f
                                                                val ghostCenterY = ghostRootY + noteH / 2f

                                                                var assignTarget: Long? = null
                                                                for ((fId, b) in panelBounds) {
                                                                    if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                        assignTarget = fId; break
                                                                    }
                                                                }
                                                                if (assignTarget == null) {
                                                                    for ((childId, b) in childBounds) {
                                                                        if (ghostCenterY in (b.top + b.height * 0.25f)..(b.bottom - b.height * 0.25f)) {
                                                                            assignTarget = childId; break
                                                                        }
                                                                    }
                                                                }
                                                                if (assignTarget != null) {
                                                                    noteHoveredPanelId = assignTarget
                                                                    insertBeforeRootKey = null
                                                                    if (noteHoveredDefault) { noteHoveredDefault = false; latestOnDraggingOverBar.value(false) }
                                                                } else {
                                                                    noteHoveredPanelId = null
                                                                    val newDefault = listBoxHeight > 0f && ghostCenterY > listTopY + listBoxHeight
                                                                    if (newDefault != noteHoveredDefault) {
                                                                        noteHoveredDefault = newDefault
                                                                        latestOnDraggingOverBar.value(newDefault)
                                                                    }
                                                                    if (!newDefault) {
                                                                        var foundBefore: String? = null
                                                                        val sortedItems = rootOrder
                                                                            .filter { it != itemKey }
                                                                            .mapNotNull { k -> rootItemBounds[k]?.let { b -> k to b } }
                                                                            .sortedBy { (_, b) -> b.top }
                                                                        for ((k, b) in sortedItems) {
                                                                            if (ghostCenterY < b.top + b.height / 2f) {
                                                                                foundBefore = k; break
                                                                            }
                                                                        }
                                                                        insertBeforeRootKey = foundBefore
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            if (draggingRootKey == itemKey) {
                                                                val toDefault = noteHoveredDefault
                                                                if (noteHoveredPanelId != null) {
                                                                    latestOnNoteAssign.value(note.id, noteHoveredPanelId)
                                                                    rootOrder.remove(itemKey)
                                                                } else if (toDefault) {
                                                                    // already uncategorized — no assignment change needed but still remove from rootOrder reorder
                                                                    latestOnDraggingOverBar.value(false)
                                                                } else {
                                                                    val fromIdx = rootOrder.indexOf(itemKey)
                                                                    if (fromIdx >= 0) {
                                                                        rootOrder.removeAt(fromIdx)
                                                                        val toIdx = if (insertBeforeRootKey != null) {
                                                                            val idx = rootOrder.indexOf(insertBeforeRootKey)
                                                                            if (idx >= 0) idx else rootOrder.size
                                                                        } else rootOrder.size
                                                                        rootOrder.add(toIdx, itemKey)
                                                                    }
                                                                    // sync folder sort orders too — without this, folder indices stay
                                                                    // unchanged and collide with the note's new global index
                                                                    val updatedFolders = syncFolderSortOrders(rootOrder, latestFolders.value)
                                                                    workingFolders = updatedFolders
                                                                    onFoldersChange(updatedFolders)
                                                                    latestOnNoteSortOrdersChange.value(rootOrderToNoteSortOrders(rootOrder))
                                                                }
                                                            }
                                                            noteHoveredDefault = false
                                                            draggingRootKey = null; noteHoveredPanelId = null; insertBeforeRootKey = null
                                                        },
                                                        onDragCancel = {
                                                            if (noteHoveredDefault) latestOnDraggingOverBar.value(false)
                                                            noteHoveredDefault = false
                                                            draggingRootKey = null; noteHoveredPanelId = null; insertBeforeRootKey = null
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.OpenWith, "Drag note", tint = StitchGreen.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val showInsertEnd =
                    (insertBeforeRootKey == null && draggingRootKey != null) ||
                    (childInsertParentId == null && childInsertBeforeId == null && draggingChildId != null && childNestTargetId == null)
                if (showInsertEnd) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(StitchGreen, RoundedCornerShape(1.dp)))
                }
            }

            // ── Root folder panel ghost ──────────────────────────────────────
            if (draggingRootKey?.startsWith("f_") == true) {
                val ghostFolderId = draggingRootKey!!.removePrefix("f_").toLongOrNull()
                val ghostPanel = buildPanels(workingFolders).firstOrNull { it.root.id == ghostFolderId }
                if (ghostPanel != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (ghostRootY - listTopY).roundToInt()) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, StitchGreen),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, tint = StitchGreen, modifier = Modifier.size(24.dp))
                            Text(ghostPanel.root.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.OpenWith, null, tint = StitchGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── Root note panel ghost ────────────────────────────────────────
            if (draggingRootKey?.startsWith("n_") == true) {
                val ghostNoteId = draggingRootKey!!.removePrefix("n_").toLongOrNull()
                val ghostNote = notes.firstOrNull { it.id == ghostNoteId }
                if (ghostNote != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (ghostRootY - listTopY).roundToInt()) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, StitchGreen),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Description, null, tint = StitchGreen, modifier = Modifier.size(24.dp))
                            Text(ghostNote.title.ifBlank { "(Untitled)" }, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.OpenWith, null, tint = StitchGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── Child folder ghost ───────────────────────────────────────────
            if (draggingChildId != null) {
                val ghostChild = workingFolders.firstOrNull { it.id == draggingChildId }
                if (ghostChild != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .offset { IntOffset(0, (ghostChildY - listTopY).roundToInt()) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, StitchGreen),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, tint = StitchGreen, modifier = Modifier.size(19.dp))
                            Text(ghostChild.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                        }
                    }
                }
            }

            // ── Note ghost (notes inside panels being reassigned) ────────────
            if (draggingNoteId != null) {
                val ghostNote = notes.firstOrNull { it.id == draggingNoteId }
                if (ghostNote != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .offset { IntOffset(0, (ghostNoteY - listTopY).roundToInt()) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, StitchGreen),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Description, null, tint = StitchGreen, modifier = Modifier.size(17.dp))
                            Text(ghostNote.title.ifBlank { "(Untitled)" }, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RenameFolderDialog(folder: Folder, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StitchSurfaceDark,
        title = { Text("Rename Folder", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StitchGreen,
                    unfocusedBorderColor = TreeLineColor,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = StitchGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = StitchGreen, contentColor = Color.Black)
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
fun DeleteFolderDialog(folder: Folder, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StitchSurfaceDark,
        title = { Text("Delete Folder", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Delete \"${folder.name}\"? Subfolders will also be deleted. Notes inside will become uncategorized.",
                color = TextMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
fun RenameNoteDialog(note: Note, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StitchSurfaceDark,
        title = { Text("Rename Note", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StitchGreen,
                    unfocusedBorderColor = TreeLineColor,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = StitchGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = StitchGreen, contentColor = Color.Black)
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}
