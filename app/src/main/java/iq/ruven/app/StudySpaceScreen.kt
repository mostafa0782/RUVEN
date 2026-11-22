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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

private fun readExamMarkers(context: Context): List<String> {
    val array = runCatching { JSONArray(studyPrefs(context).getString("exam_markers", "[]")) }.getOrNull() ?: JSONArray()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}
private fun saveExamMarkers(context: Context, markers: List<String>) {
    val array = JSONArray()
    markers.forEach { array.put(it) }
    studyPrefs(context).edit().putString("exam_markers", array.toString()).apply()
}

private fun readStudySessionHistory(context: Context, userId: String): List<JSONObject> {
    val array = runCatching {
        JSONArray(studyPrefs(context).getString("weekly_sessions_$userId", "[]"))
    }.getOrElse { JSONArray() }
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

@Composable
fun StudySpaceCard(isEnglish: Boolean, session: iq.ruven.app.data.remote.SupabaseBackend.Session, backend: iq.ruven.app.data.remote.SupabaseBackend) {
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
    if (open) StudySpaceDialog(isEnglish, session, backend, { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudySpaceDialog(isEnglish: Boolean, session: iq.ruven.app.data.remote.SupabaseBackend.Session, backend: iq.ruven.app.data.remote.SupabaseBackend, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { studyPrefs(context) }
    val scope = rememberCoroutineScope()
    var cloudStatus by remember { mutableStateOf(if (isEnglish) "Cloud sync ready" else "المزامنة السحابية جاهزة") }
    var cloudBusy by remember { mutableStateOf(false) }
    var pendingOutboxCount by remember { mutableIntStateOf(0) }
    var cloudStudyRecords by remember { mutableStateOf<List<iq.ruven.app.data.remote.SupabaseBackend.StudentStudyRecord>>(emptyList()) }
    var cloudReportBusy by remember { mutableStateOf(false) }
    var cloudReportError by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(prefs.getString("last_subject", studySubjects.first()) ?: studySubjects.first()) }
    var chapter by remember { mutableStateOf(prefs.getString("chapter", "") ?: "") }
    var lesson by remember { mutableStateOf(prefs.getString("lesson", "") ?: "") }
    var duration by remember { mutableIntStateOf(prefs.getInt("duration", 60)) }
    var unlimited by remember { mutableStateOf(prefs.getBoolean("unlimited", false)) }
    var interval by remember { mutableStateOf(prefs.getString("interval", "25/5") ?: "25/5") }
    var purpose by remember { mutableStateOf(prefs.getString("session_purpose", "دراسة عامة") ?: "دراسة عامة") }
    var examDate by remember { mutableStateOf(prefs.getString("session_exam_date", "") ?: "") }
    var examScope by remember { mutableStateOf(prefs.getString("session_exam_scope", "") ?: "") }
    var teacherNotes by remember { mutableStateOf(prefs.getString("session_teacher_notes", "") ?: "") }
    var taskTitle by remember { mutableStateOf(prefs.getString("session_task_title", "") ?: "") }
    var running by remember { mutableStateOf(prefs.getBoolean("active_running_${session.userId}", false)) }
    var elapsed by remember { mutableLongStateOf(prefs.getLong("active_elapsed_${session.userId}", 0L)) }
    var startAt by remember { mutableLongStateOf(prefs.getLong("active_start_at_${session.userId}", 0L)) }
    var pageTracking by remember { mutableStateOf(prefs.getBoolean("page_tracking", false)) }
    var page by remember { mutableStateOf(prefs.getString("last_page", "") ?: "") }
    var pdfUri by remember { mutableStateOf(prefs.getString("pdf_uri", "") ?: "") }
    var showPdfReader by remember { mutableStateOf(false) }
    var show3DViewer by remember { mutableStateOf(false) }
    var selectedCatalogModel by remember { mutableStateOf<File?>(null) }
    var textbookEntries by remember { mutableStateOf<List<iq.ruven.app.data.remote.SupabaseBackend.TextbookCatalogEntry>>(emptyList()) }
    var textbookCatalogBusy by remember { mutableStateOf(false) }
    var textbookCatalogLoaded by remember { mutableStateOf(false) }
    var textbookCatalogError by remember { mutableStateOf("") }
    var textbookDownloadingId by remember { mutableStateOf<String?>(null) }
    var homework by remember { mutableStateOf(readStudyHomework(context)) }
    var newHomework by remember { mutableStateOf("") }
    var newStatus by remember { mutableStateOf("لم ينجز") }
    var addHomework by remember { mutableStateOf(false) }
    var lessonCompleted by remember { mutableStateOf(false) }
    var sessions by remember { mutableIntStateOf(prefs.getInt("sessions_${session.userId}", 0)) }
    var weeklyHistory by remember(session.userId) { mutableStateOf(readStudySessionHistory(context, session.userId)) }
    var completedLessons by remember { mutableIntStateOf(prefs.getInt("completed_lessons_${session.userId}", 0)) }
    var examMarkers by remember { mutableStateOf(readExamMarkers(context)) }
    var newExamMarker by remember { mutableStateOf("") }
    var examKind by remember { mutableStateOf("شهري") }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pdfUri = uri.toString()
            prefs.edit().putString("pdf_uri", pdfUri).apply()
        }
    }
    LaunchedEffect(session.userId) {
        val pendingSync = StudyRecordOutbox.syncPending(context, backend, session)
        pendingOutboxCount = pendingSync.pendingCount
        cloudReportBusy = true
        try {
            cloudStudyRecords = backend.loadStudentStudyRecords(session, studentId = session.userId)
            cloudReportError = ""
        } catch (failure: Exception) {
            cloudReportError = failure.message ?: "تعذر تحميل سجلك الدراسي السحابي."
        } finally {
            cloudReportBusy = false
        }
        if (pendingSync.syncedCount > 0 || pendingSync.pendingCount > 0) {
            cloudStatus = if (pendingSync.pendingCount == 0) {
                if (isEnglish) "Pending study records synced" else "تمت مزامنة السجلات الدراسية المعلّقة"
            } else {
                (if (isEnglish) "Offline queue: " else "سجلات بانتظار المزامنة: ") + pendingSync.pendingCount
            }
        }
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsed = (android.os.SystemClock.elapsedRealtime() - startAt).coerceAtLeast(0L) / 1000L
            prefs.edit().putLong("active_elapsed_${session.userId}", elapsed).putLong("active_start_at_${session.userId}", startAt).putBoolean("active_running_${session.userId}", true).apply()
            if (!unlimited && elapsed >= duration * 60L) {
                running = false
                prefs.edit().putBoolean("active_running_${session.userId}", false).putLong("active_elapsed_${session.userId}", elapsed).apply()
            }
        }
    }
    val seconds = if (unlimited) elapsed else (duration * 60L - elapsed).coerceAtLeast(0L)
    val timer = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    val selectedInterval = when (interval) {
        "50/10" -> StudyInterval.LONG_50_10
        "بلا استراحة" -> StudyInterval.NO_BREAKS
        else -> StudyInterval.POMODORO_25_5
    }
    val cycleState = studyCycleState(elapsed, selectedInterval)
    val phaseTime = "%02d:%02d".format(cycleState.remainingSeconds / 60, cycleState.remainingSeconds % 60)

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
                    Text(if (isEnglish) "WHY ARE YOU STUDYING?" else "سبب الدراسة", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("امتحان", "واجب", "مراجعة", "دراسة عامة").forEach { value ->
                            FilterChip(selected = purpose == value, onClick = { purpose = value; prefs.edit().putString("session_purpose", value).apply() }, label = { Text(value) })
                        }
                    }
                    if (purpose == "امتحان") {
                        OutlinedTextField(value = examDate, onValueChange = { examDate = it.take(40); prefs.edit().putString("session_exam_date", examDate).apply() }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Exam date (as published)" else "موعد الامتحان (حسب إعلان المدرسة)") }, singleLine = true, placeholder = { Text("مثال: 2026-12-03") })
                        OutlinedTextField(value = examScope, onValueChange = { examScope = it.take(500); prefs.edit().putString("session_exam_scope", examScope).apply() }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Exam material / chapters" else "مادة الامتحان والفصول الداخلة") }, minLines = 2)
                        OutlinedTextField(value = teacherNotes, onValueChange = { teacherNotes = it.take(1000); prefs.edit().putString("session_teacher_notes", teacherNotes).apply() }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Teacher's notes" else "ملاحظات المدرّس على المادة") }, minLines = 2)
                        Text(if (isEnglish) "Enter only details confirmed by your teacher or the school's published notice." else "اكتب المعلومات المؤكدة من المدرّس أو إعلان المدرسة؛ التطبيق لا يخمّن موعد الامتحان أو ملاحظاته.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (purpose == "واجب") {
                        OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it.take(200); prefs.edit().putString("session_task_title", taskTitle).apply() }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Homework / task" else "اسم الواجب أو المهمة") }, singleLine = true)
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isEnglish) "APPROVED TEXTBOOK CATALOG" else "فهرس الكتب المعتمدة", fontWeight = FontWeight.Bold)
                            Text(if (isEnglish) "Official edition details supplied by school administration." else "الكتب لا تظهر إلا بعد إدخال بيانات الطبعة واعتمادها من إدارة المدرسة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(enabled = !textbookCatalogBusy, onClick = {
                            scope.launch {
                                textbookCatalogBusy = true
                                textbookCatalogError = ""
                                try {
                                    textbookEntries = backend.loadTextbookCatalog(session, subjectName = subject)
                                    textbookCatalogLoaded = true
                                } catch (error: Exception) {
                                    textbookCatalogError = error.message ?: if (isEnglish) "Could not load catalog." else "تعذر تحميل الفهرس."
                                } finally {
                                    textbookCatalogBusy = false
                                }
                            }
                        }) { Text(if (textbookCatalogBusy) (if (isEnglish) "Loading…" else "جارٍ التحميل…") else (if (isEnglish) "Refresh" else "تحديث")) }
                    }
                    if (textbookCatalogBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (textbookCatalogError.isNotBlank()) Text(textbookCatalogError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (textbookCatalogLoaded && textbookEntries.isEmpty()) {
                        Text(if (isEnglish) "No approved entries for this subject yet." else "لا توجد كتب معتمدة لهذه المادة حالياً. لن نعرض كتباً أو طبعات غير موثقة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    textbookEntries.forEach { entry ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title, fontWeight = FontWeight.SemiBold)
                                Text("${entry.gradeName} · ${entry.subjectName}", style = MaterialTheme.typography.bodySmall)
                                Text(if (isEnglish) "Edition: ${entry.editionLabel}" else "الطبعة: ${entry.editionLabel}", style = MaterialTheme.typography.bodySmall)
                                Text(listOfNotNull(entry.publisher, entry.publicationYear?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                entry.sourceName?.let { Text(if (isEnglish) "Source: $it" else "المصدر: $it", style = MaterialTheme.typography.bodySmall) }
                                Text(if (isEnglish) "File size: ${(entry.fileSizeBytes / 1024).coerceAtLeast(1)} KB" else "حجم الملف: ${(entry.fileSizeBytes / 1024).coerceAtLeast(1)} كيلوبايت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.status == "approved") {
                                    TextButton(
                                        enabled = textbookDownloadingId == null,
                                        onClick = {
                                            scope.launch {
                                                textbookDownloadingId = entry.id
                                                textbookCatalogError = ""
                                                try {
                                                    val bytes = backend.downloadApprovedTextbook(session, entry.storagePath)
                                                    val file = withContext(Dispatchers.IO) {
                                                        File(context.filesDir, "ruven_textbook_${entry.id}.pdf").apply { writeBytes(bytes) }
                                                    }
                                                    pdfUri = Uri.fromFile(file).toString()
                                                    prefs.edit().putString("pdf_uri", pdfUri).apply()
                                                    showPdfReader = true
                                                } catch (error: Exception) {
                                                    textbookCatalogError = error.message ?: if (isEnglish) "Could not download this PDF." else "تعذر تنزيل الكتاب."
                                                } finally {
                                                    textbookDownloadingId = null
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (textbookDownloadingId == entry.id) (if (isEnglish) "Downloading…" else "جارٍ التنزيل…") else (if (isEnglish) "Download and read PDF" else "تنزيل وقراءة الكتاب"))
                                    }
                                }
                                if (entry.status != "approved") Text(if (isEnglish) "Awaiting approval" else "بانتظار الاعتماد", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                                entry.sourceUrl?.let { sourceUrl ->
                                    TextButton(onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
                                    }) { Text(if (isEnglish) "Open official source" else "فتح رابط المصدر") }
                                }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "TEXTBOOK PDF" else "كتاب PDF", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Book, null); Spacer(Modifier.width(6.dp))
                        Text(if (pdfUri.isBlank()) (if (isEnglish) "Choose PDF" else "اختيار كتاب PDF") else (if (isEnglish) "PDF selected · change" else "تم اختيار الكتاب · تغيير"))
                    }
                    if (pdfUri.isNotBlank()) TextButton(onClick = { showPdfReader = true }) {
                        Text(if (isEnglish) "Read book here" else "قراءة الكتاب داخل التطبيق")
                    }
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
                    OutlinedButton(onClick = { show3DViewer = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isEnglish) "Open 3D model viewer (GLB)" else "فتح عارض المجسمات ثلاثية الأبعاد (GLB)")
                    }
                    Text(if (isEnglish) "Open a licensed or teacher-provided GLB model. Drag to orbit and pinch to zoom." else "افتح مجسماً بصيغة GLB تملكه أو وفره المدرّس. اسحب للتدوير واستخدم إصبعين للتقريب والإبعاد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Study3DResourceSection(
                        session = session,
                        backend = backend,
                        subjectName = subject,
                        isEnglish = isEnglish,
                        onOpenModel = { file ->
                            selectedCatalogModel = file
                            show3DViewer = true
                        }
                    )
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
                                val savedTitle = newHomework.trim()
                                homework = homework + (savedTitle to newStatus)
                                saveStudyHomework(context, homework)
                                cloudBusy = true
                                scope.launch {
                                    runCatching {
                                        StudyRecordOutbox.enqueueAndSync(
                                            context, backend, session,
                                            StudyRecordDraft("homework_status", subject, chapter, lesson, homeworkTitle = savedTitle, homeworkStatus = newStatus)
                                        )
                                    }.onSuccess { result ->
                                        pendingOutboxCount = result.pendingCount
                                        cloudStatus = if (result.pendingCount == 0) {
                                            if (isEnglish) "Homework synced to school account" else "تمت مزامنة الواجب مع حساب المدرسة"
                                        } else {
                                            (if (isEnglish) "Saved offline · pending: " else "حُفظ محلياً · بانتظار المزامنة: ") + result.pendingCount
                                        }
                                    }.onFailure { cloudStatus = (if (isEnglish) "Could not queue record: " else "تعذر حفظ سجل المزامنة: ") + (it.message ?: "error") }
                                    cloudBusy = false
                                }
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
                                scope.launch {
                                    runCatching {
                                    StudyRecordOutbox.enqueueAndSync(
                                        context, backend, session,
                                        StudyRecordDraft("homework_status", subject, chapter, lesson, homeworkTitle = item.first, homeworkStatus = next)
                                    )
                                }.onSuccess { result ->
                                    pendingOutboxCount = result.pendingCount
                                    cloudStatus = if (result.pendingCount == 0) {
                                        if (isEnglish) "Homework status synced" else "تمت مزامنة حالة الواجب"
                                    } else {
                                        (if (isEnglish) "Saved offline · pending: " else "حُفظ محلياً · بانتظار المزامنة: ") + result.pendingCount
                                    }
                                }.onFailure { cloudStatus = (if (isEnglish) "Could not queue record: " else "تعذر حفظ سجل المزامنة: ") + (it.message ?: "error") }
                                }
                            }) { Text(if (isEnglish) "Change" else "تغيير") }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "EXAM SCOPE" else "نطاق الامتحانات", fontWeight = FontWeight.Bold)
                    Text(if (isEnglish) "Mark chapters and lessons included in exams." else "حدد الفصول والدروس الداخلة في الامتحانات.")
                    var kindMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = kindMenu, onExpandedChange = { kindMenu = !kindMenu }) {
                        OutlinedTextField(value = examKind, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), label = { Text(if (isEnglish) "Exam type" else "نوع الامتحان") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindMenu) })
                        ExposedDropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                            listOf("شهري", "نصف السنة", "نهائي", "تجريبي", "أخرى").forEach { kind -> DropdownMenuItem(text = { Text(kind) }, onClick = { examKind = kind; kindMenu = false }) }
                        }
                    }
                    OutlinedTextField(value = newExamMarker, onValueChange = { newExamMarker = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isEnglish) "Chapter / lesson" else "الفصل أو الدرس") })
                    Button(onClick = { if (newExamMarker.isNotBlank()) {
                        val marker = newExamMarker.trim()
                        examMarkers = examMarkers + (examKind + " · " + subject + " · " + marker)
                        saveExamMarkers(context, examMarkers)
                        scope.launch {
                            runCatching {
                                StudyRecordOutbox.enqueueAndSync(
                                    context, backend, session,
                                    StudyRecordDraft("exam_scope", subject, chapterName = marker, lessonName = lesson.takeIf { it.isNotBlank() }, examKind = examKind)
                                )
                            }.onSuccess { result ->
                                pendingOutboxCount = result.pendingCount
                                cloudStatus = if (result.pendingCount == 0) {
                                    if (isEnglish) "Exam scope synced" else "تمت مزامنة نطاق الامتحان"
                                } else {
                                    (if (isEnglish) "Saved offline · pending: " else "حُفظ محلياً · بانتظار المزامنة: ") + result.pendingCount
                                }
                            }.onFailure { cloudStatus = (if (isEnglish) "Could not queue record: " else "تعذر حفظ سجل المزامنة: ") + (it.message ?: "error") }
                        }
                        newExamMarker = ""
                    } }, modifier = Modifier.fillMaxWidth()) { Text(if (isEnglish) "Save exam scope" else "حفظ نطاق الامتحان") }
                    examMarkers.forEachIndexed { index, marker -> Row(verticalAlignment = Alignment.CenterVertically) { Text(marker, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { examMarkers = examMarkers.toMutableList().also { it.removeAt(index) }; saveExamMarkers(context, examMarkers) }) { Text(if (isEnglish) "Remove" else "حذف") } } }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "STUDY SESSION TIMER" else "مؤقت جلسة الدراسة", fontWeight = FontWeight.Bold)
                    Text((if (isEnglish) "Purpose: " else "السبب: ") + purpose + " · " + (if (isEnglish) "Focus time: " else "وقت الدراسة الفعلي: ") + (studyFocusSeconds(elapsed, selectedInterval) / 60) + " " + (if (isEnglish) "min" else "دقيقة"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        when (cycleState.phase) {
                            StudyPhase.FOCUS -> if (isEnglish) "Focus block · $phaseTime remaining" else "وقت التركيز · متبقٍ $phaseTime"
                            StudyPhase.BREAK -> if (isEnglish) "Break time · $phaseTime remaining" else "وقت الاستراحة · متبقٍ $phaseTime"
                            StudyPhase.NO_BREAK -> if (isEnglish) "Breaks disabled" else "الاستراحات معطّلة"
                        },
                        color = if (cycleState.phase == StudyPhase.BREAK) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = !running, onClick = {
                            if (startAt == 0L) startAt = android.os.SystemClock.elapsedRealtime() - elapsed * 1000L
                            running = true
                            prefs.edit().putBoolean("active_running_${session.userId}", true).putLong("active_start_at_${session.userId}", startAt).putLong("active_elapsed_${session.userId}", elapsed)
                                .putString("last_subject", subject).putString("chapter", chapter).putString("lesson", lesson).putInt("duration", duration).putBoolean("unlimited", unlimited).putString("interval", interval).apply()
                        }) { Icon(Icons.Rounded.PlayArrow, null); Text(if (isEnglish) "Start" else "بدء") }
                        OutlinedButton(onClick = {
                            if (running) elapsed = (System.currentTimeMillis() - startAt).coerceAtLeast(0L) / 1000L
                            running = false
                            startAt = 0L
                            prefs.edit().putBoolean("active_running_${session.userId}", false).putLong("active_elapsed_${session.userId}", elapsed).putLong("active_start_at_${session.userId}", 0L).apply()
                        }) { Text(if (isEnglish) "Pause" else "إيقاف مؤقت") }
                        TextButton(enabled = !cloudBusy, onClick = {
                            if (running) elapsed = (System.currentTimeMillis() - startAt).coerceAtLeast(0L) / 1000L
                            running = false
                            val totalElapsed = elapsed.coerceIn(0L, 86400L)
                            val finishedFocusSeconds = studyFocusSeconds(totalElapsed, selectedInterval).coerceIn(0L, 86400L)
                            val finishedBreakSeconds = (totalElapsed - finishedFocusSeconds).coerceAtLeast(0L)
                            val finishedLesson = lessonCompleted
                            val sessionId = java.util.UUID.randomUUID().toString()
                            sessions += 1
                            if (finishedLesson) completedLessons += 1
                            val weeklySessions = runCatching { JSONArray(prefs.getString("weekly_sessions_${session.userId}", "[]") ?: "[]") }.getOrDefault(JSONArray())
                            weeklySessions.put(JSONObject()
                                .put("id", sessionId)
                                .put("at", System.currentTimeMillis())
                                .put("subject", subject)
                                .put("purpose", purpose)
                                .put("lesson", lesson)
                                .put("seconds", finishedFocusSeconds)
                                .put("break_seconds", finishedBreakSeconds)
                                .put("planned_seconds", if (unlimited) JSONObject.NULL else duration * 60)
                                .put("completed", finishedLesson)
                                .put("exam_date", if (purpose == "امتحان") examDate else "")
                                .put("exam_scope", if (purpose == "امتحان") examScope else "")
                                .put("teacher_notes", if (purpose == "امتحان") teacherNotes else "")
                                .put("task_title", if (purpose == "واجب") taskTitle else ""))
                            val boundedWeeklySessions = JSONArray()
                            val firstWeeklyIndex = (weeklySessions.length() - 180).coerceAtLeast(0)
                            for (recordIndex in firstWeeklyIndex until weeklySessions.length()) boundedWeeklySessions.put(weeklySessions.getJSONObject(recordIndex))
                            weeklyHistory = (0 until boundedWeeklySessions.length()).mapNotNull { boundedWeeklySessions.optJSONObject(it) }
                            prefs.edit()
                                .putString("last_subject", subject)
                                .putInt("sessions_${session.userId}", sessions)
                                .putInt("completed_lessons_${session.userId}", completedLessons)
                                .putLong("study_seconds_${session.userId}", prefs.getLong("study_seconds_${session.userId}", 0L) + finishedFocusSeconds)
                                .putString("weekly_sessions_${session.userId}", boundedWeeklySessions.toString())
                                .putBoolean("active_running_${session.userId}", false)
                                .putLong("active_elapsed_${session.userId}", 0L)
                                .putLong("active_start_at_${session.userId}", 0L)
                                .apply()
                            cloudBusy = true
                            scope.launch {
                                val drafts = buildList {
                                    if (finishedFocusSeconds > 0L) add(
                                        StudyRecordDraft(
                                            recordType = "study_session",
                                            subjectName = subject,
                                            chapterName = if (purpose == "امتحان") examScope.takeIf { it.isNotBlank() } else chapter,
                                            lessonName = lesson,
                                            durationSeconds = finishedFocusSeconds,
                                            pageNumber = if (pageTracking) page.toIntOrNull() else null,
                                            purpose = purpose,
                                            plannedDurationSeconds = if (unlimited) null else duration * 60L,
                                            breakDurationSeconds = finishedBreakSeconds,
                                            examDate = examDate.takeIf { purpose == "امتحان" && it.isNotBlank() },
                                            teacherNotes = teacherNotes.takeIf { purpose == "امتحان" && it.isNotBlank() },
                                            taskTitle = taskTitle.takeIf { purpose == "واجب" && it.isNotBlank() }
                                        )
                                    )
                                    if (finishedLesson) add(
                                        StudyRecordDraft(
                                            "lesson_progress", subject, chapter, lesson,
                                            lessonCompleted = true,
                                            pageNumber = if (pageTracking) page.toIntOrNull() else null
                                        )
                                    )
                                }
                                var pending = 0
                                var failureMessage: String? = null
                                for (draft in drafts) {
                                    val result = runCatching { StudyRecordOutbox.enqueueAndSync(context, backend, session, draft) }
                                    if (result.isSuccess) pending = result.getOrThrow().pendingCount
                                    else { failureMessage = result.exceptionOrNull()?.message ?: "error"; break }
                                }
                                pendingOutboxCount = pending
                                cloudStatus = when {
                                    failureMessage != null -> (if (isEnglish) "Queue save failed: " else "فشل حفظ قائمة المزامنة: ") + failureMessage
                                    pending > 0 -> (if (isEnglish) "Saved offline · pending: " else "حُفظ محلياً · بانتظار المزامنة: ") + pending
                                    else -> if (isEnglish) "Study session saved and synced" else "تم حفظ جلسة الدراسة ومزامنتها"
                                }
                                cloudBusy = false
                            }
                            elapsed = 0L
                            startAt = 0L
                            lessonCompleted = false
                        }) { Text(if (isEnglish) "Finish session" else "إنهاء الجلسة") }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(if (isEnglish) "MY STUDY SUMMARY" else "ملخص إنجازي", fontWeight = FontWeight.Bold)
                    Text(if (isEnglish) "Completed lessons: " + completedLessons + " · Sessions: " + sessions else "الدروس المكتملة: " + completedLessons + " · الجلسات: " + sessions)
                    Text("إجمالي وقت الدراسة الفعلي: " + (prefs.getLong("study_seconds_${session.userId}", 0L) / 3600) + " ساعة و" + ((prefs.getLong("study_seconds_${session.userId}", 0L) % 3600) / 60) + " دقيقة", style = MaterialTheme.typography.bodySmall)
                    Text(if (isEnglish) "Synced account report" else "سجل حسابك المتزامن", fontWeight = FontWeight.SemiBold)
                    if (cloudReportBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (cloudReportError.isNotBlank()) Text(cloudReportError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(if (isEnglish) "${cloudStudyRecords.count { it.recordType == "study_session" }} synced sessions · ${cloudStudyRecords.filter { it.recordType == "study_session" }.sumOf { it.durationSeconds.toLong() } / 60} focused minutes" else "${cloudStudyRecords.count { it.recordType == "study_session" }} جلسة متزامنة · ${cloudStudyRecords.filter { it.recordType == "study_session" }.sumOf { it.durationSeconds.toLong() } / 60} دقيقة دراسة فعلية", style = MaterialTheme.typography.bodySmall)
                    Text(if (isEnglish) "Recent sessions" else "آخر جلسات الدراسة", fontWeight = FontWeight.SemiBold)
                    weeklyHistory.takeLast(5).asReversed().forEach { entry ->
                        val at = java.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.optLong("at")))
                        val focusMinutes = entry.optLong("seconds") / 60
                        val restMinutes = entry.optLong("break_seconds") / 60
                        Text("${entry.optString("subject")} · ${entry.optString("purpose")} · ${focusMinutes} د دراسة · ${restMinutes} د استراحة · $at", style = MaterialTheme.typography.bodySmall)
                    }
                    if (weeklyHistory.isEmpty()) Text(if (isEnglish) "No study sessions saved yet." else "ماكو جلسات دراسة مسجلة بعد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("الواجبات: " + homework.count { it.second == "كُتب" } + " كُتب · " + homework.count { it.second == "اكتمل" } + " مكتمل · " + homework.count { it.second == "شبه منجز" } + " شبه منجز · " + homework.count { it.second == "لم ينجز" } + " لم ينجز", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !cloudReportBusy, onClick = {
                        cloudReportBusy = true
                        scope.launch {
                            try {
                                cloudStudyRecords = backend.loadStudentStudyRecords(session, studentId = session.userId)
                                cloudReportError = ""
                            } catch (failure: Exception) {
                                cloudReportError = failure.message ?: "تعذر تحديث السجل."
                            } finally {
                                cloudReportBusy = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (isEnglish) "Refresh synced report" else "تحديث السجل المتزامن") }
                    OutlinedButton(onClick = {
                        val report = "RUVEN Study Report\nSubject: " + subject + "\nCompleted lessons: " + completedLessons + "\nSessions: " + sessions + "\nHomework: " + homework.joinToString { it.first + " — " + it.second } + "\nExam scope: " + examMarkers.joinToString(" | ")
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Study progress report").putExtra(Intent.EXTRA_TEXT, report)
                        context.startActivity(Intent.createChooser(send, if (isEnglish) "Share study report" else "مشاركة تقرير الدراسة"))
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (isEnglish) "Share study report" else "مشاركة تقرير الدراسة") }
                    Text(cloudStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (pendingOutboxCount > 0) {
                        OutlinedButton(
                            enabled = !cloudBusy,
                            onClick = {
                                cloudBusy = true
                                scope.launch {
                                    val result = StudyRecordOutbox.syncPending(context, backend, session)
                                    pendingOutboxCount = result.pendingCount
                                    cloudStatus = if (result.pendingCount == 0) {
                                        if (isEnglish) "All queued study records synced" else "تمت مزامنة جميع السجلات المعلّقة"
                                    } else {
                                        (if (isEnglish) "Still waiting: " else "ما زالت بانتظار المزامنة: ") + result.pendingCount
                                    }
                                    cloudBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isEnglish) "Retry pending sync ($pendingOutboxCount)" else "إعادة محاولة المزامنة ($pendingOutboxCount)")
                        }
                    }
                    if (cloudBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (isEnglish) "School administrators can read synced records; students can only add and view their own records." else "تستطيع إدارة المدرسة الاطلاع على السجلات المتزامنة، والطالب لا يستطيع إضافة أو قراءة إلا سجلات حسابه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEnglish) "Close" else "إغلاق") } }
    )
    if (showPdfReader && pdfUri.isNotBlank()) {
        PdfBookReaderDialog(
            uriString = pdfUri,
            initialPage = if (pageTracking) page.toIntOrNull() ?: 1 else 1,
            onPageChanged = { currentPage ->
                if (pageTracking) {
                    page = currentPage.toString()
                    prefs.edit().putString("last_page", page).apply()
                }
            },
            onDismiss = { showPdfReader = false },
            isEnglish = isEnglish
        )
    }
    if (show3DViewer) {
        StudyModelViewerDialog(
            isEnglish = isEnglish,
            initialModelFile = selectedCatalogModel,
            onDismiss = {
                show3DViewer = false
                selectedCatalogModel = null
            }
        )
    }

}
