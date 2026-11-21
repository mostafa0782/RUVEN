package iq.ruven.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val studySubjects = listOf("الرياضيات", "اللغة العربية", "اللغة الإنجليزية", "العلوم", "الاجتماعيات", "الإسلامية", "اللغة الفرنسية")
private fun studyPrefs(context: Context) = context.getSharedPreferences("ruven_study_space_v1", Context.MODE_PRIVATE)

private fun readStudyHomework(context: Context): List<Pair<String, String>> {
    val array = runCatching { JSONArray(studyPrefs(context).getString("homework", "[]")) }.getOrNull() ?: JSONArray()
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        title to item.optString("status", "لم ينجز")
    }
}
private fun saveStudyHomework(context: Context, items: List<Pair<String, String>>) {
    val array = JSONArray()
    items.forEach { array.put(JSONObject().put("title", it.first).put("status", it.second)) }
    studyPrefs(context).edit().putString("homework", array.toString()).apply()
}

@Composable
fun StudySpaceCard(isEnglish: Boolean) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val lastSubject = remember { studyPrefs(context).getString("last_subject", "الرياضيات") ?: "الرياضيات" }
    Card(Modifier.fillMaxWidth().clickable { open = true }, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = androidx.compose.ui.graphics.Color(0xFFE0F2ED)) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = androidx.compose.ui.graphics.Color(0xFF087F68), modifier = Modifier.padding(13.dp).size(27.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (isEnglish) "StudySpace" else "مساحة الدراسة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (isEnglish) "Subjects · focus · progress" else "المواد · التركيز · متابعة الإنجاز", style = MaterialTheme.typography.bodySmall)
                Text(if (isEnglish) "Last subject: " + lastSubject else "آخر مادة: " + lastSubject, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF087F68))
            }
            Button(onClick = { open = true }) { Text(if (isEnglish) "Open" else "افتح") }
        }
    }
    if (open) StudySpaceDialog(isEnglish, { open = false })
}

@Composable
private fun StudySpaceDialog(isEnglish: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { studyPrefs(context) }
    var subject by remember { mutableStateOf(prefs.getString("last_subject", studySubjects.first()) ?: studySubjects.first()) }
    var chapter by remember { mutableStateOf(prefs.getString("chapter", "") ?: "") }
    var lesson by remember { mutableStateOf(prefs.getString("lesson", "") ?: "") }
    var duration by remember { mutableIntStateOf(prefs.getInt("duration", 60)) }
    var unlimited by remember { mutableStateOf(prefs.getBoolean("unlimited", false)) }
    var interval by remember { mutableStateOf(prefs.getString("interval", "25/5") ?: "25/5") }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var startAt by remember { mutableLongStateOf(0L) }
    var pageTracking by remember { mutableStateOf(prefs.getBoolean("page_tracking", false)) }
    var page by remember { mutableStateOf(prefs.getString("last_page", "") ?: "") }
    var pdfUri by remember { mutableStateOf(prefs.getString("pdf_uri", "") ?: "") }
    var homework by remember { mutableStateOf(readStudyHomework(context)) }
    var newHomework by remember { mutableStateOf("") }
    var newStatus by remember { mutableStateOf("لم ينجز") }
    var addHomework by remember { mutableStateOf(false) }
    var lessonCompleted by remember { mutableStateOf(false) }
    var sessions by remember { mutableIntStateOf(prefs.getInt("sessions", 0)) }
    var completedLessons by remember { mutableIntStateOf(prefs.getInt("completed_lessons", 0)) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pdfUri = uri.toString()
            prefs.edit().putString("pdf_uri", pdfUri).apply()
        }
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsed = (System.currentTimeMillis() - startAt).coerceAtLeast(0L) / 1000L
            if (!unlimited && elapsed >= duration * 60L) running = false
        }
    }
    val seconds = if (unlimited) elapsed else (duration * 60L - elapsed).coerceAtLeast(0L)
    val timer = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEnglish) "StudySpace" else "مساحة الدراسة", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(if (isEnglish) "SUBJECT" else "المادة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = subject, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            studySubjects.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { subject = s; expanded = false }) }
                        }
                    }
                    OutlinedTextField(value = chapter, onValueChange = { chapter = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Chapter (optional)" else "الفصل (اختياري)") }, singleLine = true)
                    OutlinedTextField(value = lesson, onValueChange = { lesson = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Lesson (optional)" else "الدرس (اختياري)") }, singleLine = true)
                }
                item {
                    Text(if (isEnglish) "SESSION LENGTH" else "مدة الجلسة", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(30, 60, 120).forEach { m -> FilterChip(selected = !unlimited && duration == m, onClick = { duration = m; unlimited = false }, label = { Text(if (m == 30) "30 د" else if (m == 60) "1 س" else "2 س") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(180, 240).forEach { m -> FilterChip(selected = !unlimited && duration == m, onClick = { duration = m; unlimited = false }, label = { Text(if (m == 180) "3 س" else "4 س") }) }
                        FilterChip(selected = unlimited, onClick = { unlimited = true }, label = { Text(if (isEnglish) "Unlimited" else "بلا وقت") })
                    }
                }
                item {
                    Text(if (isEnglish) "FOCUS / BREAK" else "التركيز والاستراحة", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("25/5", "50/10", "بلا استراحة").forEach { value -> FilterChip(selected = interval == value, onClick = { interval = value }, label = { Text(value) }) }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "TEXTBOOK PDF" else "كتاب PDF", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Book, null); Spacer(Modifier.width(6.dp))
                        Text(if (pdfUri.isBlank()) (if (isEnglish) "Choose PDF" else "اختيار كتاب PDF") else (if (isEnglish) "PDF selected · change" else "تم اختيار الكتاب · تغيير"))
                    }
                    if (pdfUri.isNotBlank()) TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(pdfUri), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    }) { Text(if (isEnglish) "Open book" else "فتح الكتاب") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pageTracking, onCheckedChange = {
                            pageTracking = it
                            prefs.edit().putBoolean("page_tracking", it).apply()
                            if (!it) { page = ""; prefs.edit().remove("last_page").apply() }
                        })
                        Text(if (isEnglish) "Optional page tracking" else "تتبع الصفحات (اختياري)")
                    }
                    if (pageTracking) OutlinedTextField(value = page, onValueChange = { page = it.filter(Char::isDigit); prefs.edit().putString("last_page", page).apply() }, label = { Text(if (isEnglish) "Last page" else "آخر صفحة") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "LESSON PROGRESS" else "إنجاز الدرس", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lessonCompleted, onCheckedChange = { lessonCompleted = it })
                        Text(if (isEnglish) "Lesson completed" else "الدرس مكتمل")
                    }
                    Text(if (isEnglish) "3D learning models will be loaded here after licensed curriculum assets are connected." else "مكان النماذج التعليمية ثلاثية الأبعاد؛ ستُحمّل عند ربط أصول المنهج المرخّصة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEnglish) "HOMEWORK" else "الواجبات", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { addHomework = !addHomework }) { Text(if (isEnglish) "Add" else "إضافة") }
                    }
                    if (addHomework) {
                        OutlinedTextField(value = newHomework, onValueChange = { newHomework = it }, label = { Text(if (isEnglish) "Homework title" else "اسم الواجب") }, modifier = Modifier.fillMaxWidth())
                        var statusMenu by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = statusMenu, onExpandedChange = { statusMenu = !statusMenu }) {
                            OutlinedTextField(value = newStatus, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusMenu) })
                            ExposedDropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                listOf("كُتب", "اكتمل", "شبه منجز", "لم ينجز").forEach { state -> DropdownMenuItem(text = { Text(state) }, onClick = { newStatus = state; statusMenu = false }) }
                            }
                        }
                        Button(onClick = {
                            if (newHomework.isNotBlank()) {
                                homework = homework + (newHomework.trim() to newStatus)
                                saveStudyHomework(context, homework)
                                newHomework = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (isEnglish) "Save homework" else "حفظ الواجب") }
                    }
                    homework.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.first)
                                Text(item.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = {
                                val states = listOf("كُتب", "اكتمل", "شبه منجز", "لم ينجز")
                                val next = states[(states.indexOf(item.second).coerceAtLeast(0) + 1) % states.size]
                                homework = homework.toMutableList().also { it[index] = item.first to next }
                                saveStudyHomework(context, homework)
                            }) { Text(if (isEnglish) "Change" else "تغيير") }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "FOCUS TIMER" else "مؤقت التركيز", fontWeight = FontWeight.Bold)
                    Text(timer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = !running, onClick = {
                            if (startAt == 0L) startAt = System.currentTimeMillis() - elapsed * 1000L
                            running = true
                            prefs.edit().putString("last_subject", subject).putString("chapter", chapter).putString("lesson", lesson).putInt("duration", duration).putBoolean("unlimited", unlimited).putString("interval", interval).apply()
                        }) { Icon(Icons.Rounded.PlayArrow, null); Text(if (isEnglish) "Start" else "بدء") }
                        OutlinedButton(onClick = { running = false; startAt = 0L }) { Text(if (isEnglish) "Pause" else "إيقاف مؤقت") }
                        TextButton(onClick = {
                            running = false
                            sessions += 1
                            if (lessonCompleted) completedLessons += 1
                            prefs.edit().putInt("sessions", sessions).putInt("completed_lessons", completedLessons)
                                .putLong("study_seconds", prefs.getLong("study_seconds", 0L) + elapsed).apply()
                            elapsed = 0L; startAt = 0L; lessonCompleted = false
                        }) { Text(if (isEnglish) "Finish" else "إنهاء") }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "MY STUDY SUMMARY" else "ملخص إنجازي", fontWeight = FontWeight.Bold)
                    Text(if (isEnglish) "Completed lessons: " + completedLessons + " · Sessions: " + sessions else "الدروس المكتملة: " + completedLessons + " · الجلسات: " + sessions)
                    Text("الواجبات: " + homework.count { it.second == "كُتب" } + " كُتب · " + homework.count { it.second == "اكتمل" } + " مكتمل · " + homework.count { it.second == "شبه منجز" } + " شبه منجز · " + homework.count { it.second == "لم ينجز" } + " لم ينجز", style = MaterialTheme.typography.bodySmall)
                    Text(if (isEnglish) "Saved locally. Secure school reporting requires account-linked backend policies." else "الحفظ محلي حالياً؛ إرسال التقارير للإدارة يحتاج ربطاً آمناً بالحساب وقواعد صلاحيات قاعدة البيانات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEnglish) "Close" else "إغلاق") } }
    )
}
