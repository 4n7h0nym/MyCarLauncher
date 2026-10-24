package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.ClockStyle
import com.openlauncher.app.model.WeatherState
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@Composable
fun ClockWeatherWidget(
    clockStyle: ClockStyle,
    weather: WeatherState?,
    accent: Color,
    modifier: Modifier = Modifier,
    isDayMode: Boolean = false
) {
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            calendar = Calendar.getInstance()
        }
    }

    val contentColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val subColor     = if (isDayMode) Color(0xFF888888) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Clock side
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            val hour   = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            LocalizedLabel(
                text          = "%02d:%02d".format(hour, minute),
                color         = contentColor,
                fontSize      = 42.sp,
                fontWeight    = FontWeight.Light,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            LocalizedLabel(
                text     = buildWidgetDateString(calendar),
                color    = subColor,
                fontSize = 11.sp
            )
        }

        // Weather side
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            if (weather != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalizedLabel(
                        text = "${weather.temperatureCelsius.toInt()}°",
                        color = contentColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light
                    )
                }
                Spacer(Modifier.height(2.dp))
                LocalizedLabel(
                    text = weather.conditionLabel.uppercase(Locale.getDefault()),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            } else {
                LocalizedLabel(
                    text = "WEATHER LOADING...",
                    color = subColor,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private fun buildWidgetDateString(cal: Calendar): String {
    val sdf = java.text.SimpleDateFormat("d MMMM EEEE", Locale.getDefault())
    return sdf.format(cal.time)
}
