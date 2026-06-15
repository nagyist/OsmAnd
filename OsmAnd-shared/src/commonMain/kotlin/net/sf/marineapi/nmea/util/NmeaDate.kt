/*
 * NmeaDate.kt
 * Copyright (C) 2010 Kimmo Tuukkanen
 *
 * This file is part of Java Marine API.
 * <http://ktuukkan.github.io/marine-api/>
 *
 * Java Marine API is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Java Marine API is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Marine API. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.marineapi.nmea.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a calendar date (day-month-year) transmitted in NMEA sentences.
 */
class NmeaDate {
    private var day = 0
    private var month = 0
    private var year = 0

    constructor() {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        year = now.year
        month = now.monthNumber
        day = now.dayOfMonth
    }

    constructor(date: String?) {
        setDay(date!!.substring(0, 2).toInt())
        setMonth(date.substring(2, 4).toInt())
        setYear(date.substring(4).toInt())
    }

    constructor(year: Int, month: Int, day: Int) {
        setYear(year)
        setMonth(month)
        setDay(day)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        return other is NmeaDate && other.getDay() == getDay() && other.getMonth() == getMonth() && other.getYear() == getYear()
    }

    fun getDay(): Int {
        return day
    }

    fun getMonth(): Int {
        return month
    }

    fun getYear(): Int {
        return year
    }

    override fun hashCode(): Int {
        return toISO8601().hashCode()
    }

    fun setDay(day: Int) {
        require(day in 1..31) { "Day out of bounds [1..31]" }
        this.day = day
    }

    fun setMonth(month: Int) {
        require(month in 1..12) { "Month value out of bounds [1..12]" }
        this.month = month
    }

    fun setYear(year: Int) {
        require((year in 0..99) || (year in 1000..9999)) { "Year must be two or four digit value" }
        when {
            year in (PIVOT_YEAR + 1)..99 -> this.year = 1900 + year
            year < 100 && year <= PIVOT_YEAR -> this.year = 2000 + year
            else -> this.year = year
        }
    }

    override fun toString(): String {
        val y = getYear().toString()
        val year = y.substring(2)
        val d = getDay().toString().padStart(2, '0')
        val m = getMonth().toString().padStart(2, '0')
        return "$d$m$year"
    }

    fun toISO8601(): String {
        val y = getYear().toString()
        val m = getMonth().toString().padStart(2, '0')
        val d = getDay().toString().padStart(2, '0')
        return "$y-$m-$d"
    }

    fun toISO8601(t: Time): String {
        return toISO8601() + "T" + t.toISO8601()
    }

    fun toLocalDate(): LocalDate {
        return LocalDate(getYear(), getMonth(), getDay())
    }

    companion object {
        const val PIVOT_YEAR = 50
    }
}
