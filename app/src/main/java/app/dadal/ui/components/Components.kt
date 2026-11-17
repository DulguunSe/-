package app.dadal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dadal.data.Habit
import app.dadal.data.TrackingType
import app.dadal.domain.HabitStats
import app.dadal.domain.fraction
import app.dadal.domain.isQuit
import app.dadal.formatNum
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitIcons
import app.dadal.ui.theme.HabitPalette

@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = Dadal.SurfaceHigh,
    tint: Color = Dadal.Text,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Dadal.OutlineSoft, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun IconBadge(iconKey: String, colorIndex: Int, modifier: Modifier = Modifier, size: Dp = 56.dp, corner: Dp = 16.dp) {
    val color = HabitPalette.get(colorIndex)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.17f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(HabitIcons.get(iconKey), null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

/** Habit card background: a faint wash of the habit color, like the reference cards. */
fun Modifier.habitCard(colorIndex: Int, corner: Dp = 28.dp): Modifier {
    val color = HabitPalette.get(colorIndex)
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(Brush.linearGradient(listOf(color.copy(alpha = 0.10f), Dadal.Surface, Dadal.Surface)))
        .border(1.dp, Dadal.OutlineSoft, shape)
}

fun Modifier.surfaceCard(corner: Dp = 28.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this.clip(shape).background(Dadal.Surface).border(1.dp, Dadal.OutlineSoft, shape)
}

/** The square check button on habit cards. Shows a progress ring for count/measure habits. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCheckButton(
    habit: Habit,
    stats: HabitStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val color = HabitPalette.get(habit.color)
    val done = stats.doneToday
    val progress = if (habit.isQuit) 0f else habit.fraction(stats.todayValue)
    val scale = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(done) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        scale.snapTo(0.82f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val filled = done && !habit.isQuit
    Box(
        modifier
            .size(size)
            .scale(scale.value)
            .clip(shape)
            .background(
                when {
                    filled -> color
                    habit.isQuit && done -> color.copy(alpha = 0.16f)
                    habit.isQuit -> Dadal.Slip.copy(alpha = 0.16f)
                    else -> Dadal.SurfaceHigh.copy(alpha = 0.55f)
                }
            )
            .border(1.dp, if (filled) color else Dadal.OutlineSoft, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Checkbox),
        contentAlignment = Alignment.Center,
    ) {
        if (!done && progress > 0f) {
            Canvas(Modifier.fillMaxSize().padding(size * 0.12f)) {
                drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(width = 3.dp.toPx()))
                drawArc(color, -90f, 360f * progress, false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        when {
            habit.isQuit && done -> Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(size * 0.46f))
            habit.isQuit -> Icon(Icons.Rounded.Close, null, tint = Dadal.Slip, modifier = Modifier.size(size * 0.46f))
            filled -> Icon(Icons.Rounded.Check, null, tint = Color(0xE6000000), modifier = Modifier.size(size * 0.48f))
            habit.tracking != TrackingType.YES_NO && stats.todayValue > 0 -> Text(
                formatNum(stats.todayValue),
                style = TextStyle(fontFamily = Dadal.Mono, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Bold),
                color = color,
            )
            else -> Icon(Icons.Rounded.Check, null, tint = Dadal.TextMuted, modifier = Modifier.size(size * 0.46f))
        }
    }
}

@Composable
fun StreakBadge(count: Int, modifier: Modifier = Modifier, active: Boolean = count > 0, fontSize: Int = 14) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.LocalFireDepartment, null,
            tint = if (active) Dadal.Flame else Dadal.TextFaint,
            modifier = Modifier.size((fontSize + 4).dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            count.toString(),
            style = TextStyle(fontFamily = Dadal.Mono, fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
            color = if (active) Dadal.Flame else Dadal.TextFaint,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 12.dp, top = 22.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        color = Dadal.TextMuted,
    )
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    accent: Color = Dadal.Primary,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    trailingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else Dadal.SurfaceHigh)
            .border(1.dp, if (selected) accent.copy(alpha = 0.5f) else Dadal.OutlineSoft, shape)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) accent else Dadal.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = textStyle, color = if (selected) accent else Dadal.Text, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, null, tint = Dadal.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun DadalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minHeight: Dp = 60.dp,
) {
    val shape = RoundedCornerShape(24.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Dadal.Text),
        cursorBrush = SolidColor(Dadal.Primary),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dadal.Surface)
            .border(1.dp, Dadal.Outline, shape),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp).heightIn(min = (minHeight - 36.dp).coerceAtLeast(22.dp)), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Dadal.TextFaint)
                inner()
            }
        },
    )
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dadal.Background)
            .border(1.dp, Dadal.Outline, shape)
            .padding(5.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (isSel) Dadal.SurfaceHighest else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (isSel) Dadal.Text else Dadal.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SheetHeader(title: String, onBack: () -> Unit, backIcon: ImageVector, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        CircleButton(backIcon, null, onBack, Modifier.align(Alignment.CenterStart), size = 52.dp)
        Text(
            title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().surfaceCard(26.dp), content = content)
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { Icon(Icons.Rounded.ChevronRight, null, tint = Dadal.TextMuted) },
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Dadal.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
        }
        trailing?.invoke()
    }
}

/** Seven dots that sketch a repeat pattern. 2 = done, 1 = dim, 0 = free day. */
@Composable
fun DotsPreview(pattern: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        pattern.forEach { p ->
            when (p) {
                2 -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
                1 -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.3f)))
                else -> Box(Modifier.size(9.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Dadal.TextFaint))
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Dadal.Primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Dadal.Primary, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp)); action()
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Dadal.Primary) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(if (enabled) color else Dadal.PrimaryDim.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        )
    }
}
