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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    var searchQuery by remember { mutableStateOf("") }
    var taskFilter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showCompletedOnly by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val doneCount = tasks.count { it.done }
    val progressPercent = if (tasks.isEmpty()) 0 else doneCount * 100 / tasks.size
    var focusMode by remember { mutableStateOf(false) }
    val visibleTasks = tasks.filter { task ->
        (!focusMode || !task.done) && (!showCompletedOnly || task.done) &&
            task.title.contains(searchQuery.trim(), ignoreCase = true)
    }
    val title = if (isEnglish) "My quick study plan" else "خطتي الدراسية السريعة"
    val subtitle = if (isEnglish) "Small steps, saved on this device" else "قسّم يومك لخطوات صغيرة — تنحفظ على جهازك"
    val placeholder = if (isEnglish) "Add a task (e.g. review chemistry)" else "أضف مهمة (مثلاً: أراجع الكيمياء)"
    val addLabel = if (isEnglish) "Add" else "إضافة"
    val emptyLabel = if (isEnglish) "Add your first small task above." else "أضف أول مهمة صغيرة من الحقل أعلاه."
    val progressLabel = if (isEnglish) doneCount.toString() + " of " + tasks.size + " completed (" + progressPercent + "%)" else "أنجزت " + doneCount + " من " + tasks.size + " مهام (" + progressPercent + "%)"
    val resetLabel = if (isEnglish) "Clear completed" else "مسح المنجز"
    val focusLabel = if (focusMode) { if (isEnglish) "Show all tasks" else "عرض كل المهام" } else { if (isEnglish) "Focus on unfinished" else "التركيز على غير المنجز" }
    val allDoneLabel = if (isEnglish) "All tasks are completed. Good work!" else "كل المهام منجزة، أحسنت!"
    val searchLabel = if (isEnglish) "Search tasks" else "ابحث في المهام"
    val filterAll = if (isEnglish) "All" else "الكل"
    val filterOpen = if (isEnglish) "To do" else "المتبقية"
    val filterDone = if (isEnglish) "Done" else "المنجزة"
    val filteredEmpty = if (isEnglish) "No tasks match this view." else "ماكو مهام تطابق هذا العرض."
    val searchLabel = if (isEnglish) "Search tasks" else "ابحث بالمهام"
    val completedFilterLabel = if (showCompletedOnly) { if (isEnglish) "Show all status" else "عرض كل الحالات" } else { if (isEnglish) "Completed only" else "المنجزة فقط" }
    val noMatchesLabel = if (isEnglish) "No tasks match this filter." else "ماكو مهام تطابق البحث أو التصفية."
    val addTask = {
        val value = draft.trim()
        if (value.isNotEmpty() && tasks.size < 20) {
            tasks.add(PlannerTask(UUID.randomUUID().toString(), value, false))
            savePlannerTasks(context, tasks.toList())
            draft = ""
        }
    }
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTask() }),
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = { addTask() },
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
            if (tasks.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(searchLabel, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { taskFilter = "all" }) { Text(if (taskFilter == "all") "• " + filterAll else filterAll) }
                    TextButton(onClick = { taskFilter = "open" }) { Text(if (taskFilter == "open") "• " + filterOpen else filterOpen) }
                    TextButton(onClick = { taskFilter = "done" }) { Text(if (taskFilter == "done") "• " + filterDone else filterDone) }
                }
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(searchLabel) },
                    shape = RoundedCornerShape(14.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { focusMode = !focusMode }) { Text(focusLabel) }
                    TextButton(onClick = { showCompletedOnly = !showCompletedOnly }) { Text(completedFilterLabel) }
                }
                if (visibleTasks.isEmpty()) {
                    Text(
                        if (tasks.isNotEmpty() && tasks.all { it.done } && !showCompletedOnly && searchQuery.isBlank()) allDoneLabel else noMatchesLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                visibleTasks.forEach { task ->
                    if (editingTaskId == task.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = editingDraft,
                                onValueChange = { editingDraft = it.take(100) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(if (isEnglish) "Edit task" else "تعديل المهمة") },
                                shape = RoundedCornerShape(14.dp)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingTaskId = null; editingDraft = "" }) {
                                    Text(if (isEnglish) "Cancel" else "إلغاء")
                                }
                                TextButton(
                                    enabled = editingDraft.trim().isNotEmpty(),
                                    onClick = {
                                        val index = tasks.indexOfFirst { it.id == task.id }
                                        if (index >= 0) {
                                            tasks[index] = tasks[index].copy(title = editingDraft.trim())
                                            savePlannerTasks(context, tasks.toList())
                                        }
                                        editingTaskId = null
                                        editingDraft = ""
                                    }
                                ) { Text(if (isEnglish) "Save" else "حفظ") }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val taskIndex = tasks.indexOfFirst { it.id == task.id }
                                val visibleIndex = visibleTasks.indexOfFirst { it.id == task.id }
                                androidx.compose.material3.IconButton(
                                    enabled = visibleIndex > 0,
                                    onClick = {
                                        if (visibleIndex > 0) {
                                            val previousId = visibleTasks[visibleIndex - 1].id
                                            val previousIndex = tasks.indexOfFirst { it.id == previousId }
                                            if (taskIndex >= 0 && previousIndex >= 0) {
                                                val current = tasks[taskIndex]
                                                tasks[taskIndex] = tasks[previousIndex]
                                                tasks[previousIndex] = current
                                                savePlannerTasks(context, tasks.toList())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = if (isEnglish) "Move task up" else "نقل المهمة للأعلى",
                                        tint = if (visibleIndex > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    enabled = visibleIndex >= 0 && visibleIndex < visibleTasks.lastIndex,
                                    onClick = {
                                        if (visibleIndex >= 0 && visibleIndex < visibleTasks.lastIndex) {
                                            val nextId = visibleTasks[visibleIndex + 1].id
                                            val nextIndex = tasks.indexOfFirst { it.id == nextId }
                                            if (taskIndex >= 0 && nextIndex >= 0) {
                                                val current = tasks[taskIndex]
                                                tasks[taskIndex] = tasks[nextIndex]
                                                tasks[nextIndex] = current
                                                savePlannerTasks(context, tasks.toList())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = if (isEnglish) "Move task down" else "نقل المهمة للأسفل",
                                        tint = if (visibleIndex >= 0 && visibleIndex < visibleTasks.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { editingTaskId = task.id; editingDraft = task.title }
                                ) {
                                    Icon(
                                        Icons.Rounded.EditNote,
                                        contentDescription = if (isEnglish) "Edit task" else "تعديل المهمة",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        tasks.removeAll { it.id == task.id }
                                        if (editingTaskId == task.id) { editingTaskId = null; editingDraft = "" }
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
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = tasks.any { it.done }
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Text(resetLabel)
                    }
                }
            }
        }
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(if (isEnglish) "Clear completed tasks?" else "مسح المهام المنجزة؟") },
            text = {
                Text(
                    if (isEnglish) "This removes " + doneCount + " completed task(s) from this device. This cannot be undone."
                    else "سيتم حذف " + doneCount + " من المهام المنجزة من هذا الجهاز، ولا يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tasks.removeAll { it.done }
                        savePlannerTasks(context, tasks.toList())
                        showClearConfirmation = false
                    }
                ) { Text(if (isEnglish) "Clear" else "مسح") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}
