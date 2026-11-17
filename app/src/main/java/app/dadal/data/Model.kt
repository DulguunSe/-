package app.dadal.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class HabitType { BUILD, QUIT }

@Serializable
enum class TrackingType { YES_NO, COUNT, MEASURE }

@Serializable
enum class ScheduleType { DAILY, SPECIFIC_DAYS, TIMES_PER_WEEK, TIMES_PER_MONTH, EVERY_N_DAYS }

@Serializable
enum class ReminderStyle { NOTIFICATION, ALARM }

/** Bit 0 = Monday … bit 6 = Sunday. */
const val ALL_DAYS = 0b1111111
const val WEEKDAYS = 0b0011111
const val WEEKENDS = 0b1100000
const val MAX_REMINDERS = 5

@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "pulse",
    val color: Int = 0,
    val type: HabitType = HabitType.BUILD,
    val tracking: TrackingType = TrackingType.YES_NO,
    /** Daily goal for build habits, daily limit for quit habits. */
    val target: Double = 1.0,
    val unit: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val scheduleDays: Int = ALL_DAYS,
    /** Times per week / month, or N for "every N days". */
    val scheduleCount: Int = 1,
    /** Minutes after midnight. */
    val reminderTimes: List<Int> = emptyList(),
    val reminderStyle: ReminderStyle = ReminderStyle.NOTIFICATION,
    val categoryIds: List<Long> = emptyList(),
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdDay: Long = LocalDate.now().toEpochDay(),
)

@Serializable
@Entity(
    tableName = "entries",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class Entry(val habitId: Long, val day: Long, val value: Double)

@Serializable
@Entity(
    tableName = "notes",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class Note(val habitId: Long, val day: Long, val text: String, val updatedAt: Long = System.currentTimeMillis())

/** A pause from [startDay] to [endDay] inclusive. A null [endDay] means "until resumed". */
@Serializable
@Entity(
    tableName = "pauses",
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class Pause(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val startDay: Long,
    val endDay: Long? = null,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "label",
    val sortOrder: Int = 0,
)
