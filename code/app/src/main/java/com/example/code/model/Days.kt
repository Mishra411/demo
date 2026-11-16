/*
 * Days.kt
 * Calendar-day helpers shared by availability, reservations and the calendar
 * view. A "day" is the number of days since 1970-01-01, so it does not
 * depend on the time of day.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

import java.util.Calendar
import java.util.TimeZone

/** Converts between times, day numbers and "YYYY-MM-DD" text. */
object Days {
    /** Milliseconds in one day. */
    const val MILLIS = 24L * 60 * 60 * 1000

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun utcCalendar(day: Long): Calendar =
        Calendar.getInstance(utc).apply { timeInMillis = day * MILLIS }

    /** Day number of the local date of [millis] in [timeZone]. */
    fun dayOf(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return of(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }

    /** Day number of [year], zero-based [month] and [dayOfMonth]. */
    fun of(year: Int, month: Int, dayOfMonth: Int): Long {
        val calendar = Calendar.getInstance(utc).apply {
            clear()
            set(year, month, dayOfMonth)
        }
        return Math.floorDiv(calendar.timeInMillis, MILLIS)
    }

    /** Local midnight in [timeZone] at the start of [day]. */
    fun startOfDay(day: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val date = utcCalendar(day)
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Last millisecond of [day] in [timeZone]. */
    fun endOfDay(day: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        startOfDay(day + 1, timeZone) - 1

    /** Day of the week of [day]: 1 = Sunday to 7 = Saturday, as in [Calendar]. */
    fun weekday(day: Long): Int = (Math.floorMod(day + 4, 7L)).toInt() + 1

    /** Short name of weekday [weekday] (1 = "Sun" to 7 = "Sat"). */
    fun weekdayName(weekday: Int): String = weekdays[(weekday - 1).coerceIn(0, 6)]

    /** Year and zero-based month of [day]. */
    fun monthOf(day: Long): Pair<Int, Int> =
        utcCalendar(day).let { it.get(Calendar.YEAR) to it.get(Calendar.MONTH) }

    /** Day of the month (1 to 31) of [day]. */
    fun dayOfMonth(day: Long): Int = utcCalendar(day).get(Calendar.DAY_OF_MONTH)

    /** Number of days in zero-based [month] of [year]. */
    fun daysInMonth(year: Int, month: Int): Int =
        utcCalendar(of(year, month, 1)).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Month title, e.g. "November 2026" is shown as "Nov 2026". */
    fun monthLabel(year: Int, month: Int): String = "${months[month]} $year"

    /** Formats [day] for people, e.g. "Nov 14, 2026". */
    fun label(day: Long): String {
        val (year, month) = monthOf(day)
        return "${months[month]} ${dayOfMonth(day)}, $year"
    }

    /** Formats [day] as "YYYY-MM-DD". */
    fun format(day: Long): String {
        val (year, month) = monthOf(day)
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth(day))
    }

    /** Parses "YYYY-MM-DD" into a day number, or null if it is not a real date. */
    fun parse(text: String): Long? {
        val match = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})").matchEntire(text.trim()) ?: return null
        val (year, month, date) = match.destructured.toList().map { it.toInt() }
        if (month !in 1..12 || date !in 1..daysInMonth(year, month - 1)) return null
        return of(year, month - 1, date)
    }

    /**
     * Cells of the month grid for zero-based [month] of [year], Sunday first:
     * null for the blanks before day 1, then each day number of the month.
     */
    fun monthGrid(year: Int, month: Int): List<Long?> {
        val first = of(year, month, 1)
        val blanks = weekday(first) - 1
        return List<Long?>(blanks) { null } + (0 until daysInMonth(year, month)).map { first + it }
    }
}
