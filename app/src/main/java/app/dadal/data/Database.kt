package app.dadal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun intsToString(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun stringToInts(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun longsToString(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun stringToLongs(value: String): List<Long> =
        if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.trim().toLongOrNull() }
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    suspend fun getAll(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: Long): Flow<Habit?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): Habit?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM habits")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<Habit>)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries")
    suspend fun getAll(): List<Entry>

    @Query("SELECT * FROM entries WHERE habitId = :habitId")
    fun observeFor(habitId: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE habitId = :habitId AND day = :day")
    suspend fun get(habitId: Long, day: Long): Entry?

    @Upsert
    suspend fun upsert(entry: Entry)

    @Query("DELETE FROM entries WHERE habitId = :habitId AND day = :day")
    suspend fun delete(habitId: Long, day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<Entry>)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY day DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM notes WHERE habitId = :habitId ORDER BY day DESC")
    fun observeFor(habitId: Long): Flow<List<Note>>

    @Upsert
    suspend fun upsert(note: Note)

    @Query("DELETE FROM notes WHERE habitId = :habitId AND day = :day")
    suspend fun delete(habitId: Long, day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)
}

@Dao
interface PauseDao {
    @Query("SELECT * FROM pauses")
    fun observeAll(): Flow<List<Pause>>

    @Query("SELECT * FROM pauses")
    suspend fun getAll(): List<Pause>

    @Query("SELECT * FROM pauses WHERE habitId = :habitId")
    fun observeFor(habitId: Long): Flow<List<Pause>>

    @Query("SELECT * FROM pauses WHERE habitId = :habitId")
    suspend fun getFor(habitId: Long): List<Pause>

    @Insert
    suspend fun insert(pause: Pause): Long

    @Update
    suspend fun update(pause: Pause)

    @Query("DELETE FROM pauses WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pauses: List<Pause>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Database(
    entities = [Habit::class, Entry::class, Note::class, Pause::class, Category::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun entryDao(): EntryDao
    abstract fun noteDao(): NoteDao
    abstract fun pauseDao(): PauseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dadal.db").build()
    }
}
