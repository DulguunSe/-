package app.dadal.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.dadal.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
