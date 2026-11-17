package app.dadal.domain

enum class BadgeId(val icon: String, val goal: Int) {
    FIRST_STEP("flag", 1),
    WEEK_STREAK("whatshot", 7),
    MONTH_STREAK("fire", 30),
    CENTURY("trophy", 100),
    YEAR("star", 365),
    PERFECT_DAY("task_alt", 1),
    PERFECT_WEEK("medal", 7),
    CHECKINS_100("trending_up", 100),
    CHECKINS_1000("rocket", 1000),
    COLLECTOR("category", 5),
    JOURNAL("edit_note", 10),
}

data class Badge(val id: BadgeId, val progress: Int) {
    val unlocked: Boolean get() = progress >= id.goal
    val shownProgress: Int get() = progress.coerceAtMost(id.goal)
}

object BadgeCalculator {
    fun compute(datas: List<HabitData>, statsList: List<HabitStats>, notesCount: Int, today: Long): List<Badge> {
        val buildChecks = datas.indices.filter { !datas[it].habit.isQuit }.sumOf { statsList[it].total }
        val bestStreak = statsList.maxOfOrNull { it.best } ?: 0
        val perfect = HabitCalculator.perfectSummary(datas, today)
        val active = datas.count { !it.habit.archived }
        return BadgeId.entries.map { id ->
            val progress = when (id) {
                BadgeId.FIRST_STEP -> buildChecks + statsList.count { it.best > 0 }
                BadgeId.WEEK_STREAK, BadgeId.MONTH_STREAK, BadgeId.CENTURY, BadgeId.YEAR -> bestStreak
                BadgeId.PERFECT_DAY -> perfect.perfectDays
                BadgeId.PERFECT_WEEK -> perfect.best
                BadgeId.CHECKINS_100, BadgeId.CHECKINS_1000 -> buildChecks
                BadgeId.COLLECTOR -> active
                BadgeId.JOURNAL -> notesCount
            }
            Badge(id, progress)
        }
    }
}
