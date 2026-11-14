package my.sado.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.sado.app.data.MealTime
import my.sado.app.data.MealWhen

/** Which day (today, yesterday, the day before) and what time a meal was eaten. Defaults to right now. */
@Composable
fun MealWhenPicker(value: MealWhen, onChange: (MealWhen) -> Unit) {
    val context = LocalContext.current
    val now = MealTime.nowMinute()
    val slotMinutes = MealTime.SLOTS.map { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealTime.DAYS.forEachIndexed { i, d ->
                WhenChip(d, value.daysAgo == i) {
                    // a past day starts on the evening meal; today goes back to "now"
                    onChange(if (i == 0) MealWhen() else MealWhen(i, value.minute ?: 20 * 60))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value.daysAgo == 0) WhenChip("Sekarang · ${MealTime.clock(now)}", value.minute == null) { onChange(MealWhen()) }
            MealTime.SLOTS.forEach { (name, m) ->
                if (MealTime.allowed(value.daysAgo, m, now)) WhenChip("$name ${MealTime.clock(m)}", value.minute == m) { onChange(value.copy(minute = m)) }
            }
            val custom = value.minute != null && value.minute !in slotMinutes
            WhenChip(if (custom) "Jam ${MealTime.clock(value.minute!!)}" else "Jam lain…", custom) {
                val start = value.minute ?: now
                TimePickerDialog(context, { _, h, mi ->
                    val m = h * 60 + mi
                    onChange(value.copy(minute = if (value.daysAgo == 0) minOf(m, now) else m))
                }, start / 60, start % 60, true).show()
            }
        }
    }
}

@Composable
internal fun WhenChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) SadoColors.OnAccent else SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        modifier = Modifier.clip(shape).background(if (selected) SadoColors.Accent else SadoColors.Surface2)
            .border(1.dp, if (selected) SadoColors.Accent else SadoColors.Outline, shape)
            .clickable(role = Role.RadioButton) { Feedback.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** The picker in a dialog: where quick adds go, or moving a meal already in the log. */
@Composable
fun MealWhenDialog(title: String, sub: String, initial: MealWhen, confirm: String, onConfirm: (MealWhen) -> Unit, onDismiss: () -> Unit) {
    var w by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
                .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
            MealWhenPicker(w) { w = it }
            PrimaryButton(confirm) { onConfirm(w) }
            SecondaryButton("Batal", Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}
