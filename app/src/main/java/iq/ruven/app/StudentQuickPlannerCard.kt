package iq.ruven.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private data class PlannerTask(val id: String, val title: String, val done: Boolean)

private fun readPlannerTasks(context: Context): List<PlannerTask> = runCatching {
    val raw = context.getSharedPreferences("ruven_student_planner", Context.MODE_PRIVATE)
        .getString("tasks", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        val title = item.optString("title").trim().take(100)
        if (id.isBlank() || title.isBlank()) null else PlannerTask(id, title, item.optBoolean("done"))
    }.take(20)
}.getOrDefault(emptyList())

private fun savePlannerTasks(context: Context, tasks: List<PlannerTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(JSONObject().put("id", task.id).put("title", task.title).put("done", task.done))
    }
    context.getSharedPreferences("ruven_student_planner", Context.MODE_PRIVATE)
        .edit().putString("tasks", array.toString()).apply()
}

@Composable
internal fun StudentQuickPlannerCard(isEnglish: Boolean = false) {
    val context = LocalContext.current
    val tasks = remember {
        mutableStateListOf<PlannerTask>().apply { addAll(readPlannerTasks(context)) }
    }
    var draft by remember { mutableStateOf("") }
    val doneCount = tasks.count { it.done }
    val title = if (isEnglish) "My quick study plan" else "خطتي الدراسية السريعة"
    val subtitle = if (isEnglish) "Small steps, saved on this device" else "قسّم يومك لخطوات صغيرة — تنحفظ على جهازك"
    val placeholder = if (isEnglish) "Add a task (e.g. review chemistry)" else "أضف مهمة (مثلاً: أراجع الكيمياء)"
    val addLabel = if (isEnglish) "Add" else "إضافة"
    val emptyLabel = if (isEnglish) "Add your first small task above." else "أضف أول مهمة صغيرة من الحقل أعلاه."
    val progressLabel = if (isEnglish) doneCount.toString() + " of " + tasks.size + " completed" else "أنجزت " + doneCount + " من " + tasks.size + " مهام"
    val resetLabel = if (isEnglish) "Clear completed" else "مسح المنجز"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.background(Color(0xFFE5F1FF), RoundedCornerShape(14.dp)).padding(10.dp)
                ) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, tint = Color(0xFF2867D8))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(doneCount.toString() + "/" + tasks.size, color = Color(0xFF2867D8), fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(100) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty() && tasks.size < 20) {
                            tasks.add(PlannerTask(UUID.randomUUID().toString(), value, false))
                            savePlannerTasks(context, tasks.toList())
                            draft = ""
                        }
                    },
                    enabled = draft.trim().isNotEmpty() && tasks.size < 20,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(addLabel) }
            }
            if (tasks.size >= 20) {
                Text(
                    if (isEnglish) "Maximum 20 tasks. Delete a task to add another."
                    else "وصلت للحد الأقصى (20 مهمة). احذف مهمة حتى تضيف غيرها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (tasks.isEmpty()) {
                Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                Text(progressLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / tasks.size.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2867D8),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { checked ->
                                val index = tasks.indexOfFirst { it.id == task.id }
                                if (index >= 0) {
                                    tasks[index] = tasks[index].copy(done = checked)
                                    savePlannerTasks(context, tasks.toList())
                                }
                            }
                        )
                        Text(
                            task.title,
                            modifier = Modifier.weight(1f),
                            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        androidx.compose.material3.IconButton(
                            onClick = {
                                tasks.removeAll { it.id == task.id }
                                savePlannerTasks(context, tasks.toList())
                            }
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = if (isEnglish) "Delete task" else "حذف المهمة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            tasks.removeAll { it.done }
                            savePlannerTasks(context, tasks.toList())
                        },
                        enabled = tasks.any { it.done }
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Text(resetLabel)
                    }
                }
            }
        }
    }
}
