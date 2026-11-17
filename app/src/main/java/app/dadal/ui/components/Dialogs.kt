package app.dadal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import app.dadal.R
import app.dadal.data.Habit
import app.dadal.data.TrackingType
import app.dadal.formatNum
import app.dadal.goalText
import app.dadal.shortDate
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import java.time.LocalDate
import kotlin.math.max

/** Enter an amount for count/measure habits (or slips for quit habits). */
@Composable
fun ValueDialog(habit: Habit, date: LocalDate, initial: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(if (initial > 0) formatNum(initial) else "") }
    val step = if (habit.tracking == TrackingType.MEASURE && habit.target < 5) 0.5 else 1.0
    fun current() = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    val color = HabitPalette.get(habit.color)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        title = {
            Column {
                Text(habit.name, style = MaterialTheme.typography.titleLarge)
                Text(shortDate(context, date), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleButton(Icons.Rounded.Remove, null, { text = formatNum(max(0.0, current() - step)) }, size = 52.dp)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8) },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                        textStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = color),
                        keyboardOptions = KeyboardOptions(keyboardType = if (habit.tracking == TrackingType.MEASURE) KeyboardType.Decimal else KeyboardType.Number),
                        placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = TextStyle(fontSize = 26.sp)) },
                    )
                    CircleButton(Icons.Rounded.Add, null, { text = formatNum(current() + step) }, size = 52.dp)
                }
                Spacer(Modifier.height(10.dp))
                if (habit.unit.isNotBlank()) Text(habit.unit, color = Dadal.TextMuted)
                goalText(context, habit)?.let { Text(it, color = Dadal.TextMuted, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (initial > 0) TextButton(onClick = { onSave(0.0) }) { Text(stringResource(R.string.clear), color = Dadal.Slip) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun NoteDialog(date: LocalDate, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        title = { Text(shortDate(context, date), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = Dadal.Slip) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        title = { Text(title) },
        text = { Text(text, color = Dadal.TextMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) Dadal.Slip else Dadal.Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Dadal.Surface,
                    timeSelectorSelectedContainerColor = Dadal.Primary.copy(alpha = 0.25f),
                    timeSelectorUnselectedContainerColor = Dadal.Surface,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dadal.SurfaceHigh,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
