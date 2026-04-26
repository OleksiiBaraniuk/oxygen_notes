package com.oxygennotes.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxygennotes.app.data.Note
import com.oxygennotes.app.ui.theme.StitchGreen
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// NoteGrid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NoteGrid(
    notes: List<Note>,
    onNoteClick: (Long) -> Unit,
    onContextMenu: (note: Note, anchor: Offset, bounds: Rect) -> Unit = { _, _, _ -> },
) {
    val pinned = remember(notes) { notes.filter { it.isPinned } }
    val others = remember(notes) { notes.filter { !it.isPinned } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description, contentDescription = null,
                        modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        "No notes yet", fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp
        ) {
            // ─── PINNED SECTION ───────────────────────────────────────────
            if (pinned.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SectionHeader(
                        label = "PINNED",
                        icon = {
                            Icon(Icons.Default.PushPin, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(
                    items = pinned,
                    key = { it.id },
                    span = { note -> if (note.isFullWidth) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }
                ) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onLongPress = { anchor, bounds -> onContextMenu(note, anchor, bounds) }
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(8.dp)) }
            }

            // ─── OTHERS SECTION ───────────────────────────────────────────
            if (others.isNotEmpty()) {
                if (pinned.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SectionHeader(label = "OTHERS")
                        Spacer(Modifier.height(4.dp))
                    }
                }
                items(
                    items = others,
                    key = { it.id },
                    span = { note -> if (note.isFullWidth) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }
                ) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onLongPress = { anchor, bounds -> onContextMenu(note, anchor, bounds) }
                    )
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(120.dp)) }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Size Picker — appears below the note card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NoteResizePicker(
    isFullWidth: Boolean,
    cardBounds: Rect,
    onSelectSize: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val yPx = with(density) { (cardBounds.bottom + 14.dp.toPx()).roundToInt() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150)
    )

    Row(
        modifier = Modifier
            .offset { IntOffset(0, yPx) }
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .scale(scale),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        // Size 1 — Normal (two per row, default)
        SizeOptionPanel(
            icon       = Icons.Default.ViewModule,
            label      = "Normal",
            isSelected = !isFullWidth,
            onClick    = { onSelectSize(false) },
            modifier   = Modifier.weight(1f)
        )
        // Size 2 — Full (single note fills whole row)
        SizeOptionPanel(
            icon       = Icons.Default.ViewStream,
            label      = "Full",
            isSelected = isFullWidth,
            onClick    = { onSelectSize(true) },
            modifier   = Modifier.weight(1f)
        )
    }
}

@Composable
fun SizeOptionPanel(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor  = if (isSelected) StitchGreen else MaterialTheme.colorScheme.surface
    val fgColor  = if (isSelected) Color.Black  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val border   = if (isSelected) BorderStroke(0.dp, Color.Transparent)
                   else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))

    Card(
        modifier  = modifier
            .height(72.dp)
            .clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        border    = border,
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = fgColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = fgColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spotlight overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpotlightOverlay(cardBounds: Rect, onDismiss: () -> Unit) {
    val density  = LocalDensity.current
    val cornerPx = with(density) { 12.dp.toPx() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.72f else 0f,
        animationSpec = tween(200)
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        drawRect(Color.Black.copy(alpha = alpha))
        if (cardBounds != Rect.Zero) {
            drawRoundRect(
                color        = Color.Transparent,
                topLeft      = cardBounds.topLeft,
                size         = cardBounds.size,
                cornerRadius = CornerRadius(cornerPx),
                blendMode    = BlendMode.Clear
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Radial Menu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NoteRadialMenu(
    anchor: Offset,
    isPinned: Boolean,
    isFullWidth: Boolean,
    onDelete: () -> Unit,
    onFolder: () -> Unit,
    onPin: () -> Unit,
    onResize: () -> Unit,
    onDismiss: () -> Unit
) {
    val density  = LocalDensity.current
    val radiusPx = with(density) { 90.dp.toPx() }
    val halfBtn  = with(density) { 28.dp.toPx() }

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
        animationSpec = tween(160)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        RadialButton(Icons.Default.Delete, "Delete", Color(0xFFDC2626), Color.White, scale, radialOffset(225.0), onDelete)
        RadialButton(
            icon     = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
            label    = if (isPinned) "Unpin" else "Pin",
            bgColor  = Color(0xFFF59E0B), tint = Color.White,
            scale    = scale, offsetPx = radialOffset(315.0), onClick = onPin
        )
        RadialButton(Icons.Default.Folder, "Folder", Color(0xFF162E26), StitchGreen, scale, radialOffset(135.0), onFolder)
        RadialButton(Icons.Default.AspectRatio, "Resize", Color(0xFF162E26), Color.White, scale, radialOffset(45.0), onResize)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Radial button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RadialButton(
    icon: ImageVector, label: String,
    bgColor: Color, tint: Color,
    scale: Float, offsetPx: IntOffset,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset { offsetPx }.scale(scale)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(60.dp).background(bgColor, CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete Note confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeleteNoteDialog(note: Note, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val displayTitle = note.title.ifBlank { "Untitled" }.let {
        if (it.length > 40) it.take(40) + "…" else it
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        title = {
            Text("Delete Note", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text("Are you sure you want to delete \"$displayTitle\"?", color = MaterialTheme.colorScheme.onSurface)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(label: String?, icon: (@Composable () -> Unit)? = null) {
    if (label == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        icon?.invoke()
        if (icon != null) Spacer(modifier = Modifier.width(6.dp))
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Note Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongPress: (touchInRoot: Offset, cardBoundsInRoot: Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface
    var cardBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                cardBoundsInRoot = Rect(offset = pos, size = Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
            }
            .pointerInput(note.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { localOffset ->
                        onLongPress(cardBoundsInRoot.topLeft + localOffset, cardBoundsInRoot)
                    }
                )
            },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        border    = if (note.isPinned) BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (note.title.isNotEmpty()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (note.isPinned) {
                    Icon(Icons.Default.PushPin, "Pinned", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                }
            }
            if (note.title.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
            if (note.content.isNotEmpty()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 15, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
