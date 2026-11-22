package iq.ruven.app

import java.time.Instant

/** Domain rules for RUVEN StudySpace. Persistence and UI are intentionally separate. */
enum class StudyDuration(val minutes: Int) {
    MINUTES_30(30), HOUR_1(60), HOURS_2(120), HOURS_3(180), HOURS_4(240), UNLIMITED(0)
}

enum class StudyInterval(val focusMinutes: Int, val breakMinutes: Int) {
    POMODORO_25_5(25, 5),
    LONG_50_10(50, 10),
    NO_BREAKS(0, 0)
}

enum class StudyPhase { FOCUS, BREAK, NO_BREAK }

data class StudyCycleState(
    val phase: StudyPhase,
    val remainingSeconds: Long
)

/** Computes the current focus/rest phase from elapsed active session time. */
/** Returns accumulated focused seconds, excluding scheduled breaks. */
fun studyFocusSeconds(elapsedSeconds: Long, interval: StudyInterval): Long {
    require(elapsedSeconds >= 0L)
    if (interval == StudyInterval.NO_BREAKS) return elapsedSeconds
    val focus = interval.focusMinutes * 60L
    val cycle = (interval.focusMinutes + interval.breakMinutes) * 60L
    val fullCycles = elapsedSeconds / cycle
    val remainder = elapsedSeconds % cycle
    return fullCycles * focus + minOf(remainder, focus)
}

fun studyCycleState(elapsedSeconds: Long, interval: StudyInterval): StudyCycleState {
    require(elapsedSeconds >= 0L)
    if (interval == StudyInterval.NO_BREAKS) {
        return StudyCycleState(StudyPhase.NO_BREAK, 0L)
    }
    val focusSeconds = interval.focusMinutes * 60L
    val cycleSeconds = (interval.focusMinutes + interval.breakMinutes) * 60L
    val cyclePosition = elapsedSeconds % cycleSeconds
    return if (cyclePosition < focusSeconds) {
        StudyCycleState(StudyPhase.FOCUS, focusSeconds - cyclePosition)
    } else {
        StudyCycleState(StudyPhase.BREAK, cycleSeconds - cyclePosition)
    }
}

data class StudySessionConfig(
    val subject: String,
    val chapter: String? = null,
    val lesson: String? = null,
    val duration: StudyDuration = StudyDuration.HOUR_1,
    val interval: StudyInterval = StudyInterval.POMODORO_25_5
) {
    init {
        require(subject.isNotBlank()) { "يجب اختيار المادة." }
        require(chapter == null || chapter.isNotBlank())
        require(lesson == null || lesson.isNotBlank())
    }

    val hasTimerLimit: Boolean get() = duration != StudyDuration.UNLIMITED
}

enum class HomeworkProgress(val arabicLabel: String) {
    WRITTEN("كُتب"),
    COMPLETED("اكتمل"),
    PARTIALLY_COMPLETED("شبه منجز"),
    NOT_COMPLETED("لم ينجز")
}

enum class LearningProgress {
    NOT_STARTED, IN_PROGRESS, COMPLETED
}

enum class ExamKind(val arabicLabel: String) {
    MONTHLY("شهري"),
    MIDYEAR("نصف السنة"),
    FINAL("نهائي"),
    MOCK("تجريبي"),
    OTHER("أخرى")
}

data class ExamScopeMarker(
    val kind: ExamKind,
    val label: String,
    val chapter: String,
    val lesson: String? = null
) {
    init {
        require(label.isNotBlank())
        require(chapter.isNotBlank())
        require(lesson == null || lesson.isNotBlank())
    }
}

/** Page tracking is opt-in; null values mean the student did not enable it. */
data class OptionalPageProgress(
    val enabled: Boolean = false,
    val lastPage: Int? = null,
    val totalPages: Int? = null
) {
    init {
        if (!enabled) {
            require(lastPage == null && totalPages == null) {
                "لا تُحفظ الصفحات إذا كان تتبعها معطلاً."
            }
        } else {
            require(lastPage == null || lastPage >= 1)
            require(totalPages == null || totalPages >= 1)
            require(lastPage == null || totalPages == null || lastPage <= totalPages)
        }
    }
}

data class TextbookEdition(
    val subject: String,
    val title: String,
    val edition: String,
    val sourceName: String,
    val sourceUrl: String?,
    val publishedAt: String?,
    val verifiedBySchool: Boolean
) {
    init {
        require(subject.isNotBlank() && title.isNotBlank() && edition.isNotBlank())
        require(sourceName.isNotBlank())
        require(sourceUrl == null || sourceUrl.startsWith("https://"))
    }
}

data class StudyActivityRecord(
    val studentId: String,
    val subject: String,
    val lesson: String?,
    val textbookTitle: String?,
    val sessionSeconds: Long,
    val lessonProgress: LearningProgress,
    val pageProgress: OptionalPageProgress = OptionalPageProgress(),
    val recordedAt: Instant
) {
    init {
        require(studentId.isNotBlank() && subject.isNotBlank())
        require(sessionSeconds >= 0)
    }
}

data class HomeworkStatusRecord(
    val studentId: String,
    val subject: String,
    val title: String,
    val progress: HomeworkProgress,
    val teacherConfirmed: Boolean = false
) {
    init {
        require(studentId.isNotBlank() && subject.isNotBlank() && title.isNotBlank())
    }
}

data class StudentStudyReport(
    val lessonsCompleted: Int,
    val booksStudied: Int,
    val studySeconds: Long,
    val sessions: Int,
    val homeworkWritten: Int,
    val homeworkCompleted: Int,
    val homeworkPartiallyCompleted: Int,
    val homeworkNotCompleted: Int
) {
    companion object {
        fun build(
            activities: List<StudyActivityRecord>,
            homework: List<HomeworkStatusRecord>
        ): StudentStudyReport {
            val homeworkCounts = homework.groupingBy { it.progress }.eachCount()
            return StudentStudyReport(
                lessonsCompleted = activities.count { it.lessonProgress == LearningProgress.COMPLETED },
                booksStudied = activities.mapNotNull { it.textbookTitle?.trim()?.takeIf(String::isNotEmpty) }.distinct().size,
                studySeconds = activities.sumOf { it.sessionSeconds },
                sessions = activities.size,
                homeworkWritten = homeworkCounts[HomeworkProgress.WRITTEN] ?: 0,
                homeworkCompleted = homeworkCounts[HomeworkProgress.COMPLETED] ?: 0,
                homeworkPartiallyCompleted = homeworkCounts[HomeworkProgress.PARTIALLY_COMPLETED] ?: 0,
                homeworkNotCompleted = homeworkCounts[HomeworkProgress.NOT_COMPLETED] ?: 0
            )
        }
    }
}
