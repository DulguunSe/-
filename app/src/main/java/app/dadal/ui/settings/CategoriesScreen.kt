package app.dadal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dadal.R
import app.dadal.data.Category
import app.dadal.data.Habit
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.ConfirmDialog
import app.dadal.ui.components.DadalTextField
import app.dadal.ui.components.EmptyState
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitIcons
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val categories by c.repo.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.categories), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                CircleButton(Icons.Rounded.Add, stringResource(R.string.new_category), { creating = true }, container = Dadal.Primary, tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        items(categories, key = { it.id }) { cat ->
            Row(Modifier.fillMaxWidth().surfaceCard(22.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HabitIcons.get(cat.icon), null, tint = Dadal.Text)
                Spacer(Modifier.width(14.dp))
                Text(cat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                CircleButton(Icons.Rounded.Edit, stringResource(R.string.edit), { editing = cat }, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.Delete, stringResource(R.string.delete), { deleting = cat }, size = 40.dp, tint = Dadal.Slip)
            }
        }
    }

    if (creating || editing != null) {
        CategoryDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, icon ->
                val e = editing
                scope.launch {
                    if (e == null) c.repo.addCategory(name, icon) else c.repo.updateCategory(e.copy(name = name.trim(), icon = icon))
                }
                creating = false; editing = null
            },
        )
    }
    deleting?.let { cat ->
        ConfirmDialog(
            title = stringResource(R.string.delete_category_title),
            text = stringResource(R.string.delete_category_text, cat.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = { scope.launch { c.repo.deleteCategory(cat.id) }; deleting = null },
        )
    }
}

@Composable
private fun CategoryDialog(initial: Category?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "label") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        title = { Text(stringResource(if (initial == null) R.string.new_category else R.string.edit_category)) },
        text = {
            Column {
                DadalTextField(
                    name, { name = it.take(24) },
                    placeholder = stringResource(R.string.category_name_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gridItems(HabitIcons.pickable, key = { it.first }) { (key, vector) ->
                        val sel = key == icon
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) Dadal.Primary.copy(alpha = 0.25f) else Dadal.Surface)
                                .border(1.dp, if (sel) Dadal.Primary else Dadal.OutlineSoft, RoundedCornerShape(14.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, key, tint = if (sel) Dadal.Primary else Dadal.Text, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, icon) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ArchivedScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val habits by c.repo.habits.collectAsStateWithLifecycle(initialValue = emptyList())
    val archived = habits.filter { it.archived }
    var deleting by remember { mutableStateOf<Habit?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.archived_habits), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (archived.isEmpty()) {
            item { EmptyState(Icons.Rounded.Restore, stringResource(R.string.archived_empty_title), stringResource(R.string.archived_empty_text)) }
        }
        items(archived, key = { it.id }) { habit ->
            Row(
                Modifier.fillMaxWidth().surfaceCard(22.dp).clickable { onOpenHabit(habit.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(habit.icon, habit.color, size = 40.dp, corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Text(habit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                CircleButton(Icons.Rounded.Restore, stringResource(R.string.restore), { scope.launch { c.repo.setArchived(habit.id, false) } }, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.Delete, stringResource(R.string.delete), { deleting = habit }, size = 40.dp, tint = Dadal.Slip)
            }
        }
    }
    deleting?.let { habit ->
        ConfirmDialog(
            title = stringResource(R.string.delete_habit_title),
            text = stringResource(R.string.delete_habit_text, habit.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch { c.scheduler.cancelHabit(habit.id); c.repo.deleteHabit(habit.id) }
                deleting = null
            },
        )
    }
}
