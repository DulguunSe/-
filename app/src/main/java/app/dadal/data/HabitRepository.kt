package app.dadal.data

import androidx.room.withTransaction
import app.dadal.domain.HabitData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime

/** Everything needed to compute streaks for all habits at once. */
class Snapshot(val habits: List<Habit>, entries: List<Entry>, pauses: List<Pause>) {
    private val valuesByHabit: Map<Long, Map<Long, Double>> =
        entries.groupBy { it.habitId }.mapValues { (_, list) -> list.associate { it.day to it.value } }
    private val pausesByHabit: Map<Long, List<Pause>> = pauses.groupBy { it.habitId }

    fun data(habit: Habit) = HabitData(habit, valuesByHabit[habit.id].orEmpty(), pausesByHabit[habit.id].orEmpty())
    val active: List<Habit> get() = habits.filter { !it.archived }
}

@Serializable
data class Backup(
    val app: String = "dadal",
    val version: Int = 1,
    val exportedAt: String = LocalDateTime.now().toString(),
    val habits: List<Habit> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val notes: List<Note> = emptyList(),
    val pauses: List<Pause> = emptyList(),
    val categories: List<Category> = emptyList(),
)

class HabitRepository(private val db: AppDatabase) {
    private val habitDao = db.habitDao()
    private val entryDao = db.entryDao()
    private val noteDao = db.noteDao()
    private val pauseDao = db.pauseDao()
    private val categoryDao = db.categoryDao()

    val habits: Flow<List<Habit>> = habitDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val notes: Flow<List<Note>> = noteDao.observeAll()
    val snapshotFlow: Flow<Snapshot> =
        combine(habitDao.observeAll(), entryDao.observeAll(), pauseDao.observeAll()) { h, e, p -> Snapshot(h, e, p) }

    fun habit(id: Long): Flow<Habit?> = habitDao.observe(id)
    fun entriesFor(id: Long): Flow<List<Entry>> = entryDao.observeFor(id)
    fun pausesFor(id: Long): Flow<List<Pause>> = pauseDao.observeFor(id)
    fun notesFor(id: Long): Flow<List<Note>> = noteDao.observeFor(id)

    suspend fun snapshot(): Snapshot = Snapshot(habitDao.getAll(), entryDao.getAll(), pauseDao.getAll())
    suspend fun getHabit(id: Long): Habit? = habitDao.get(id)
    suspend fun allHabits(): List<Habit> = habitDao.getAll()

    suspend fun saveHabit(habit: Habit): Long =
        if (habit.id == 0L) {
            habitDao.insert(habit.copy(sortOrder = habitDao.maxSortOrder() + 1))
        } else {
            habitDao.update(habit); habit.id
        }

    suspend fun deleteHabit(id: Long) = habitDao.delete(id)

    suspend fun setArchived(id: Long, archived: Boolean) {
        habitDao.get(id)?.let { habitDao.update(it.copy(archived = archived)) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        habitDao.get(id)?.let { habitDao.update(it.copy(favorite = favorite)) }
    }

    suspend fun valueOf(habitId: Long, day: Long): Double = entryDao.get(habitId, day)?.value ?: 0.0

    suspend fun setValue(habitId: Long, day: Long, value: Double) {
        if (value <= 0.0) entryDao.delete(habitId, day) else entryDao.upsert(Entry(habitId, day, value))
    }

    suspend fun saveNote(habitId: Long, day: Long, text: String) {
        if (text.isBlank()) noteDao.delete(habitId, day) else noteDao.upsert(Note(habitId, day, text.trim()))
    }

    suspend fun deleteNote(habitId: Long, day: Long) = noteDao.delete(habitId, day)

    /** Pause from [start] for [days] days, or until resumed when [days] is null. */
    suspend fun pause(habitId: Long, start: Long, days: Int?) {
        resume(habitId, start)
        pauseDao.insert(Pause(habitId = habitId, startDay = start, endDay = days?.let { start + it - 1 }))
    }

    /** End any pause that covers [today] or later, so today counts again. */
    suspend fun resume(habitId: Long, today: Long) {
        pauseDao.getFor(habitId).forEach { p ->
            val end = p.endDay
            when {
                p.startDay >= today -> pauseDao.delete(p.id)
                end == null || end >= today -> pauseDao.update(p.copy(endDay = today - 1))
            }
        }
    }

    suspend fun addCategory(name: String, icon: String): Long {
        val order = (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        return categoryDao.insert(Category(name = name.trim(), icon = icon, sortOrder = order))
    }

    suspend fun updateCategory(category: Category) = categoryDao.update(category)

    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            categoryDao.delete(id)
            habitDao.getAll().filter { id in it.categoryIds }.forEach { habitDao.update(it.copy(categoryIds = it.categoryIds - id)) }
        }
    }

    suspend fun categoryCount(): Int = categoryDao.getAll().size

    // ---- Backup ----

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun exportTo(out: OutputStream) {
        val backup = Backup(
            habits = habitDao.getAll(),
            entries = entryDao.getAll(),
            notes = noteDao.getAll(),
            pauses = pauseDao.getAll(),
            categories = categoryDao.getAll(),
        )
        out.bufferedWriter().use { it.write(json.encodeToString(backup)) }
    }

    /** Replaces all data with the backup. Returns how many habits were restored. */
    suspend fun importFrom(input: InputStream): Int {
        val text = input.bufferedReader().use { it.readText() }
        val backup = json.decodeFromString<Backup>(text)
        require(backup.app == "dadal") { "Not a Dadal backup" }
        db.withTransaction {
            habitDao.deleteAll() // entries, notes and pauses go with their habits
            categoryDao.deleteAll()
            categoryDao.insertAll(backup.categories)
            habitDao.insertAll(backup.habits)
            val ids = backup.habits.map { it.id }.toSet()
            entryDao.insertAll(backup.entries.filter { it.habitId in ids })
            noteDao.insertAll(backup.notes.filter { it.habitId in ids })
            pauseDao.insertAll(backup.pauses.filter { it.habitId in ids })
        }
        return backup.habits.size
    }
}
