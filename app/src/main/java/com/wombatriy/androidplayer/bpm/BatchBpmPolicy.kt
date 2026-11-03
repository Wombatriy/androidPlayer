package com.wombatriy.androidplayer.bpm

/** Pure, JVM-testable batch selection and progress arithmetic. */
object BatchBpmPolicy {
    data class Plan<T>(val pending: List<T>, val skipped: Int)

    fun <T> selectMissing(items: List<T>, alreadyDetected: (T) -> Boolean): Plan<T> {
        val pending = ArrayList<T>()
        var skipped = 0
        for (item in items) {
            if (alreadyDetected(item)) skipped++ else pending.add(item)
        }
        return Plan(pending, skipped)
    }

    fun progressThousand(processed: Int, total: Int): Int =
        if (total <= 0) 0 else
            (processed.coerceIn(0, total).toLong() * 1000L / total).toInt()
}
