package com.noom.interview.fullstack.sleep.exception

/** PostgreSQL constraint names from the `sleep_logs` table. */
object DbConstraints {
    const val WAKE_AFTER_BED = "wake_after_bed"
    const val NO_OVERLAPPING_SLEEP = "no_overlapping_sleep"

    private val CONSTRAINT_NAME_PATTERN = Regex("""constraint "(\w+)"""")

    /**
     * Extracts the constraint name from a [Throwable]'s root cause message by matching
     * the `constraint "name"` fragment that PostgreSQL includes in constraint violation errors
     * (e.g. `violates check constraint "wake_after_bed"`).
     */
    fun extractConstraintName(ex: Throwable): String? {
        val message = generateSequence(ex) { it.cause }.last().message ?: return null
        return CONSTRAINT_NAME_PATTERN.find(message)?.groupValues?.get(1)
    }
}
