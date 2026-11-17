package app.dadal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dadal.Celebration
import app.dadal.R
import app.dadal.streakText
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private class Particle(val x: Float, val speed: Float, val drift: Float, val spin: Float, val size: Float, val color: Color, val delay: Float)

@Composable
fun CelebrationOverlay(celebration: Celebration, onDone: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0.6f) }
    val particles = remember(celebration) {
        val r = Random(System.nanoTime())
        List(140) {
            Particle(
                x = r.nextFloat(),
                speed = 0.55f + r.nextFloat() * 0.8f,
                drift = (r.nextFloat() - 0.5f) * 0.25f,
                spin = (r.nextFloat() - 0.5f) * 1440f,
                size = 6f + r.nextFloat() * 10f,
                color = HabitPalette.colors[r.nextInt(HabitPalette.colors.size)],
                delay = r.nextFloat() * 0.35f,
            )
        }
    }
    LaunchedEffect(celebration) {
        launch { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f)) }
        launch { progress.animateTo(1f, tween(3800, easing = LinearEasing)) }
        delay(4200)
        onDone()
    }
    val (accent, big, title, text) = when (celebration) {
        is Celebration.Milestone -> {
            val color = HabitPalette.get(celebration.color)
            Quad(
                color,
                celebration.count.toString(),
                stringResource(R.string.celebrate_streak_title, streakText(context, celebration.count, celebration.unit)),
                stringResource(R.string.celebrate_streak_text, celebration.habitName),
            )
        }
        is Celebration.PerfectDay -> Quad(
            Dadal.Flame,
            celebration.streak.toString(),
            stringResource(R.string.celebrate_perfect_title),
            stringResource(R.string.celebrate_perfect_text, celebration.streak),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            particles.forEach { p ->
                val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val y = -40f + local * p.speed * size.height * 1.3f
                val x = (p.x + p.drift * local) * size.width
                rotate(p.spin * local, pivot = Offset(x, y)) {
                    drawRect(p.color.copy(alpha = 1f - local * 0.6f), topLeft = Offset(x, y), size = Size(p.size, p.size * 0.55f))
                }
            }
        }
        Column(
            Modifier.scale(pop.value).padding(32.dp).clip(RoundedCornerShape(32.dp)).background(Dadal.SurfaceHigh).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(
                    if (celebration is Celebration.PerfectDay) Icons.Rounded.EmojiEvents else Icons.Rounded.LocalFireDepartment,
                    null, tint = accent, modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(big, fontSize = 56.sp, fontWeight = FontWeight.Black, color = accent)
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Dadal.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

private data class Quad(val accent: Color, val big: String, val title: String, val text: String)
