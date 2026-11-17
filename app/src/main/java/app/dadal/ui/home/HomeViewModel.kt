package app.dadal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dadal.AppContainer
import app.dadal.data.Category
import app.dadal.data.SortMode
import app.dadal.domain.HabitCalculator
import app.dadal.domain.HabitData
import app.dadal.domain.HabitStats
import app.dadal.domain.PerfectSummary
import app.dadal.domain.isQuit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HabitRow(val data: HabitData, val stats: HabitStats) {
    val habit get() = data.habit
}

data class HomeState(
    val loaded: Boolean = false,
    val rows: List<HabitRow> = emptyList(),
    val activeCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val selectedCategory: Long? = null,
    val view: Int = 0,
    val sort: SortMode = SortMode.DEFAULT,
    val hideDone: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val perfect: PerfectSummary = PerfectSummary(),
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HomeState> = combine(
        c.repo.snapshotFlow,
        c.repo.categories,
        c.settings.settings,
        c.today,
        combine(selected, month) { s, m -> s to m },
    ) { snap, cats, st, today, (sel, m) ->
        val t = today.toEpochDay()
        val rows = snap.active.map { h -> val d = snap.data(h); HabitRow(d, HabitCalculator.stats(d, t)) }
        val validSel = sel?.takeIf { id -> cats.any { it.id == id } }
        val filtered = rows
            .filter { validSel == null || validSel in it.habit.categoryIds }
            .filter { !st.hideDone || it.habit.isQuit || !it.stats.doneToday }
        val sorted = when (st.sortMode) {
            SortMode.DEFAULT -> filtered
            SortMode.NAME -> filtered.sortedBy { it.habit.name.lowercase() }
            SortMode.STREAK -> filtered.sortedByDescending { it.stats.current }
        }.sortedByDescending { it.habit.favorite }
        HomeState(
            loaded = true,
            rows = sorted,
            activeCount = rows.size,
            categories = cats,
            selectedCategory = validSel,
            view = st.homeView,
            sort = st.sortMode,
            hideDone = st.hideDone,
            today = today,
            month = m,
            perfect = HabitCalculator.perfectSummary(rows.map { it.data }, t),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun selectCategory(id: Long?) { selected.value = id }
    fun setMonth(m: YearMonth) { month.value = m }
    fun setView(v: Int) = viewModelScope.launch { c.settings.setHomeView(v) }
    fun setSort(s: SortMode) = viewModelScope.launch { c.settings.setSort(s) }
    fun setHideDone(v: Boolean) = viewModelScope.launch { c.settings.setHideDone(v) }
    fun quickToggle(habitId: Long, day: Long) = viewModelScope.launch { c.actions.quickToggle(habitId, day) }
    fun setValue(habitId: Long, day: Long, value: Double) = viewModelScope.launch { c.actions.setValue(habitId, day, value) }
}
