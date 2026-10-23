package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerTaskFilterTest {
    private val tasks = listOf(
        PlannerTask("1", "Review chemistry", false),
        PlannerTask("2", "Finish Arabic homework", true),
        PlannerTask("3", "Review physics", false)
    )

    @Test
    fun statusFilterShowsOnlyUnfinishedTasks() {
        assertEquals(
            listOf("Review chemistry", "Review physics"),
            filterPlannerTasks(tasks, focusMode = false, taskFilter = "open", searchQuery = "")
                .map { it.title }
        )
    }

    @Test
    fun completedFilterShowsOnlyCompletedTasks() {
        assertEquals(
            listOf("Finish Arabic homework"),
            filterPlannerTasks(tasks, focusMode = false, taskFilter = "done", searchQuery = "")
                .map { it.title }
        )
    }

    @Test
    fun focusModeHidesCompletedTasks() {
        assertEquals(
            listOf("Review chemistry", "Review physics"),
            filterPlannerTasks(tasks, focusMode = true, taskFilter = "all", searchQuery = "")
                .map { it.title }
        )
    }

    @Test
    fun searchTrimsWhitespaceAndIgnoresCase() {
        assertEquals(
            listOf("Review chemistry"),
            filterPlannerTasks(tasks, focusMode = false, taskFilter = "all", searchQuery = "  CHEMISTRY ")
                .map { it.title }
        )
    }
}
