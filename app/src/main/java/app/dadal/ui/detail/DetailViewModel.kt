package app.dadal.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dadal.AppContainer
import app.dadal.data.Note
import app.dadal.domain.HabitCalculator
import app.dadal.domain.HabitData
import app.dadal.domain.HabitStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DetailState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val data: HabitData? = null,
    val stats: HabitStats = HabitStats(),
    val notes: List<Note> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

class DetailViewModel(private val c: AppContainer, private val habitId: Long) : ViewModel() {
    val state: StateFlow<DetailState> = combine(
        c.repo.habit(habitId),
        c.repo.entriesFor(habitId),
        c.repo.pausesFor(habitId),
        c.repo.notesFor(habitId),
        c.today,
    ) { habit, entries, pauses, notes, today ->
        if (habit == null) {
            DetailState(loaded = true, missing = true)
        } else {
            val data = HabitData(habit, entries.associate { it.day to it.value }, pauses)
            DetailState(true, false, data, HabitCalculator.stats(data, today.toEpochDay()), notes, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState())

    fun toggle(day: Long) = viewModelScope.launch { c.actions.quickToggle(habitId, day) }
    fun setValue(day: Long, value: Double) = viewModelScope.launch { c.actions.setValue(habitId, day, value) }
    fun saveNote(day: Long, text: String) = viewModelScope.launch { c.repo.saveNote(habitId, day, text) }
    fun deleteNote(day: Long) = viewModelScope.launch { c.repo.deleteNote(habitId, day) }
    fun setFavorite(value: Boolean) = viewModelScope.launch { c.repo.setFavorite(habitId, value) }
    fun archive(value: Boolean) = viewModelScope.launch { c.repo.setArchived(habitId, value) }

    fun pause(days: Int?) = viewModelScope.launch {
        c.repo.pause(habitId, LocalDate.now().toEpochDay(), days)
        c.outputs.refresh()
    }

    fun resume() = viewModelScope.launch {
        c.repo.resume(habitId, LocalDate.now().toEpochDay())
        c.outputs.refresh()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.scheduler.cancelHabit(habitId)
        c.repo.deleteHabit(habitId)
        onDone()
    }
}
