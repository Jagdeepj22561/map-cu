package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure KMP Date & Time Picker Bottom Sheet.
 * Works seamlessly on Android and iOS without relying on platform-specific UI fragments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureDateTimePickerBottomSheet(
    title: String = "Select Date & Time",
    initialDateText: String = "",
    mode: DateTimePickerMode = DateTimePickerMode.DATE_AND_TIME,
    onDismiss: () -> Unit,
    onConfirmed: (formattedResult: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Calendar state
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val dayNames = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    var selectedYear by remember { mutableIntStateOf(2026) }
    var selectedMonth by remember { mutableIntStateOf(9) } // 1-indexed (Sept)
    var selectedDay by remember { mutableIntStateOf(11) }

    // Time state
    var selectedHour by remember { mutableIntStateOf(10) } // 1..12
    var selectedMinute by remember { mutableIntStateOf(0) } // 0..59
    var isAm by remember { mutableStateOf(false) } // true for AM, false for PM

    // Quick presets
    val quickTimePresets = listOf("09:00 AM", "10:30 AM", "02:00 PM", "04:30 PM", "06:00 PM", "11:59 PM")

    fun daysInMonth(month: Int, year: Int): Int {
        return when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    fun startDayOfWeek(month: Int, year: Int): Int {
        val m = if (month < 3) month + 12 else month
        val y = if (month < 3) year - 1 else year
        val k = y % 100
        val j = y / 100
        val h = (1 + (13 * (m + 1)) / 5 + k + (k / 4) + (j / 4) + (5 * j)) % 7
        return ((h + 6) % 7) // 0 = Sunday, 1 = Monday...
    }

    val totalDays = daysInMonth(selectedMonth, selectedYear)
    val startOffset = startDayOfWeek(selectedMonth, selectedYear)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            if (mode != DateTimePickerMode.TIME_ONLY) {
                                append("$selectedDay ${monthNames[selectedMonth - 1]} $selectedYear")
                            }
                            if (mode == DateTimePickerMode.DATE_AND_TIME) {
                                append(" • ")
                            }
                            if (mode != DateTimePickerMode.DATE_ONLY) {
                                val minStr = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"
                                append("$selectedHour:$minStr ${if (isAm) "AM" else "PM"}")
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calendar section
            if (mode != DateTimePickerMode.TIME_ONLY) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Month & Year header with Prev/Next
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedMonth == 1) {
                                        selectedMonth = 12
                                        selectedYear--
                                    } else {
                                        selectedMonth--
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                            }

                            Text(
                                text = "${monthNames[selectedMonth - 1]} $selectedYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    if (selectedMonth == 12) {
                                        selectedMonth = 1
                                        selectedYear++
                                    } else {
                                        selectedMonth++
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                            }
                        }

                        // Day of week headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            dayNames.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Days grid
                        val gridItems = mutableListOf<Int?>()
                        for (i in 0 until startOffset) {
                            gridItems.add(null)
                        }
                        for (day in 1..totalDays) {
                            gridItems.add(day)
                        }

                        // Chunk into rows of 7
                        gridItems.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                week.forEach { dayNumber ->
                                    if (dayNumber != null) {
                                        val isSelected = dayNumber == selectedDay
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent
                                                )
                                                .clickable { selectedDay = dayNumber },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(36.dp))
                                    }
                                }
                                for (i in week.size until 7) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (mode == DateTimePickerMode.DATE_AND_TIME) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Time Picker section
            if (mode != DateTimePickerMode.DATE_ONLY) {
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hour, Minute, AM/PM selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Hour selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hour", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                selectedHour = if (selectedHour <= 1) 12 else selectedHour - 1
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = if (selectedHour < 10) "0$selectedHour" else "$selectedHour",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedHour = if (selectedHour >= 12) 1 else selectedHour + 1
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text(
                                text = ":",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )

                            // Minute selector
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minute", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                selectedMinute = (selectedMinute - 5 + 60) % 60
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        val minStr = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"
                                        Text(
                                            text = minStr,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedMinute = (selectedMinute + 5) % 60
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // AM/PM Toggle
                            Column {
                                FilterChip(
                                    selected = isAm,
                                    onClick = { isAm = true },
                                    label = { Text("AM", fontWeight = FontWeight.Bold) }
                                )
                                FilterChip(
                                    selected = !isAm,
                                    onClick = { isAm = false },
                                    label = { Text("PM", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick presets chips
                        Text("Quick Times", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            quickTimePresets.take(3).forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        parseQuickTime(preset) { h, m, am ->
                                            selectedHour = h
                                            selectedMinute = m
                                            isAm = am
                                        }
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            quickTimePresets.drop(3).forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        parseQuickTime(preset) { h, m, am ->
                                            selectedHour = h
                                            selectedMinute = m
                                            isAm = am
                                        }
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons (Cancel / Confirm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val minStr = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"
                        val formatted = when (mode) {
                            DateTimePickerMode.DATE_ONLY -> "$selectedDay ${monthNames[selectedMonth - 1]} $selectedYear"
                            DateTimePickerMode.TIME_ONLY -> "$selectedHour:$minStr ${if (isAm) "AM" else "PM"}"
                            DateTimePickerMode.DATE_AND_TIME -> "$selectedDay ${monthNames[selectedMonth - 1]} $selectedYear, $selectedHour:$minStr ${if (isAm) "AM" else "PM"}"
                        }
                        onConfirmed(formatted)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun parseQuickTime(preset: String, onParsed: (Int, Int, Boolean) -> Unit) {
    runCatching {
        val parts = preset.split(" ")
        val timeParts = parts[0].split(":")
        val h = timeParts[0].toInt()
        val m = timeParts[1].toInt()
        val am = parts[1].equals("AM", ignoreCase = true)
        onParsed(h, m, am)
    }
}

enum class DateTimePickerMode {
    DATE_ONLY,
    TIME_ONLY,
    DATE_AND_TIME
}
