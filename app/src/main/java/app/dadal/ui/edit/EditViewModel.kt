package app.dadal.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dadal.AppContainer
import app.dadal.data.ALL_DAYS
import app.dadal.data.Category
import app.dadal.data.Habit
import app.dadal.data.HabitType
import app.dadal.data.ScheduleType
import app.dadal.data.TrackingType
import app.dadal.formatNum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditViewModel(private val c: AppContainer, private val habitId: Long?) : ViewModel() {
    private val _draft = MutableStateFlow(Habit(name = "", icon = "pulse", color = 0))
    val draft: StateFlow<Habit> = _draft.asStateFlow()

    private val _target = MutableStateFlow("8")
    val target: StateFlow<String> = _target.asStateFlow()

    private val _loaded = MutableStateFlow(habitId == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val isNew = habitId == null

    val categories: StateFlow<List<Category>> =
        c.repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (habitId != null) {
            viewModelScope.launch {
                c.repo.getHabit(habitId)?.let {
                    _draft.value = it
                    _target.value = formatNum(it.target)
                }
                _loaded.value = true
            }
        }
    }

    fun update(transform: (Habit) -> Habit) {
        _draft.value = transform(_draft.value)
    }

    fun setTarget(text: String) {
        _target.value = text.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
    }

    fun setTracking(tracking: TrackingType) {
        val d = _draft.value
        if (d.tracking == tracking) return
        _target.value = when {
            tracking == TrackingType.COUNT && d.type == HabitType.QUIT -> "3"
            tracking == TrackingType.COUNT -> "8"
            tracking == TrackingType.MEASURE && d.type == HabitType.QUIT -> "60"
            tracking == TrackingType.MEASURE -> "30"
            else -> _target.value
        }
        val unit = when {
            d.unit.isNotBlank() -> d.unit
            tracking == TrackingType.MEASURE -> "min"
            else -> ""
        }
        _draft.value = d.copy(tracking = tracking, unit = if (tracking == TrackingType.YES_NO) "" else unit)
    }

    fun addCategory(name: String, icon: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = c.repo.addCategory(name, icon)
        update { it.copy(categoryIds = it.categoryIds + id) }
    }

    val canSave: Boolean get() = _draft.value.name.isNotBlank()

    fun save(onDone: (Long) -> Unit) = viewModelScope.launch {
        val d = _draft.value
        if (d.name.isBlank()) return@launch
        val parsed = _target.value.replace(',', '.').toDoubleOrNull()
        val quit = d.type == HabitType.QUIT
        val target = when {
            d.tracking == TrackingType.YES_NO -> if (quit) 0.0 else 1.0
            quit -> (parsed ?: 0.0).coerceAtLeast(0.0)
            else -> (parsed ?: 1.0).coerceAtLeast(0.01)
        }
        val fixed = d.copy(
            name = d.name.trim(),
            description = d.description.trim(),
            unit = d.unit.trim(),
            target = target,
            scheduleType = if (quit) ScheduleType.DAILY else d.scheduleType,
            scheduleDays = if (d.scheduleDays == 0) ALL_DAYS else d.scheduleDays,
            reminderTimes = d.reminderTimes.distinct().sorted(),
        )
        onDone(c.repo.saveHabit(fixed))
    }
}
