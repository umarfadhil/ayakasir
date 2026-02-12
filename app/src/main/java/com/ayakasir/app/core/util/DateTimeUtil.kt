package com.ayakasir.app.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtil {
    private val wib = TimeZone.getTimeZone("Asia/Jakarta")

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).apply {
        timeZone = wib
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).apply {
        timeZone = wib
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID")).apply {
        timeZone = wib
    }

    fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(Date(epochMillis))

    fun formatDate(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

    fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))

    fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance(wib)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis

        return start to end
    }

    fun now(): Long = System.currentTimeMillis()
}
