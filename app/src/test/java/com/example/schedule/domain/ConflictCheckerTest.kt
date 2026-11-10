package com.example.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictCheckerTest {
    private val h = 3_600_000L
    private val existing = listOf(TimeSlot(1, "Лекция", 10 * h, 11 * h))

    @Test fun overlappingIsConflict() {
        assertEquals(1, ConflictChecker.findConflicts(10 * h + 1, 12 * h, existing).size)
    }

    @Test fun touchingIntervalsDoNotConflict() {
        assertTrue(ConflictChecker.findConflicts(11 * h, 12 * h, existing).isEmpty())
        assertTrue(ConflictChecker.findConflicts(9 * h, 10 * h, existing).isEmpty())
    }

    @Test fun editedEventIgnoresItself() {
        assertTrue(ConflictChecker.findConflicts(10 * h, 11 * h, existing, ignoreId = 1).isEmpty())
    }
}
