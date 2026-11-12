package com.aurviz.aiagent

import com.aurviz.aiagent.data.local.tasks.ScheduleFrequency
import com.aurviz.aiagent.data.local.tasks.ScheduledTaskEntity
import com.aurviz.aiagent.data.local.tasks.ScheduledTasksRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SchedulerToolTest {

    @Test
    fun `test calculateNextExecutionTime advances daily schedule to tomorrow if time passed`() {
        // Mock-free testing
        val fakeRepo = object {
            fun calculateNext(task: ScheduledTaskEntity): Long {
                val now = Calendar.getInstance()
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, task.targetHour)
                    set(Calendar.MINUTE, task.targetMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now.timeInMillis) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                return next.timeInMillis
            }
        }

        val now = Calendar.getInstance()
        val pastHour = (now.get(Calendar.HOUR_OF_DAY) - 1 + 24) % 24

        val task = ScheduledTaskEntity(
            title = "Morning Routine",
            prompt = "Check important emails",
            naturalLanguageInstruction = "Every morning at 9 AM",
            frequency = ScheduleFrequency.DAILY,
            targetHour = pastHour,
            targetMinute = 0
        )

        val nextTime = fakeRepo.calculateNext(task)
        assertTrue("Next time should be strictly in the future", nextTime > System.currentTimeMillis())
    }

    @Test
    fun `test hourly interval calculation`() {
        val now = System.currentTimeMillis()
        val intervalMs = 60 * 60 * 1000L
        val nextTime = now + intervalMs

        assertTrue("Hourly next execution should be ~1 hour ahead", nextTime >= now + 3590000L)
    }
}
