package com.rasyid.focuslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyLogSelector(
    loggedDates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val availableMonths = remember(loggedDates) {
        loggedDates.map { YearMonth.from(it) }.distinct().sorted()
    }

    var currentMonthView by remember {
        mutableStateOf(
            if (availableMonths.isNotEmpty()) availableMonths.last()
            else YearMonth.now()
        )
    }

    val currentIndex = availableMonths.indexOf(currentMonthView)
    val hasPreviousMonth = currentIndex > 0
    val hasNextMonth = currentIndex < availableMonths.lastIndex && currentIndex != -1

    val daysInMonth = remember(currentMonthView, loggedDates) {
        loggedDates.filter { YearMonth.from(it) == currentMonthView }.sortedDescending()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (hasPreviousMonth) currentMonthView = availableMonths[currentIndex - 1] },
                        enabled = hasPreviousMonth,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            tint = if (hasPreviousMonth) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray
                        )
                    }

                    Text(
                        text = currentMonthView.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(150.dp)
                    )

                    IconButton(
                        onClick = { if (hasNextMonth) currentMonthView = availableMonths[currentIndex + 1] },
                        enabled = hasNextMonth,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Month",
                            tint = if (hasNextMonth) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            reverseLayout = true
        ) {
            items(daysInMonth) { date ->
                val isSelected = date == selectedDate
                DayItem(
                    date = date,
                    isSelected = isSelected,
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
fun DayItem(
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dayOfMonthFormatter = DateTimeFormatter.ofPattern("dd")

    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .width(56.dp)
            .height(64.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.format(dayOfWeekFormatter).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) contentColor else contentColor.copy(alpha = 0.7f)
            )
            Text(
                text = date.format(dayOfMonthFormatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) contentColor else androidx.compose.ui.graphics.Color.White
            )
        }
    }
}