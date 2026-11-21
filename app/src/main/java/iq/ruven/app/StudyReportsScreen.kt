package iq.ruven.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StudyReportsCard(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<SupabaseBackend.StudentStudyRecord>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<SupabaseBackend.ManagedProfile>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var rangeDays by remember { mutableStateOf<Int?>(30) }

    val filteredRecords = remember(records, profiles, search, rangeDays) {
        val cutoff = rangeDays?.let {
            LocalDate.now().minusDays(it.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
        val needle = search.trim().lowercase()
        records.filter { record ->
            val profile = profiles.firstOrNull { it.id == record.studentId }
            val matchesDate = cutoff == null || runCatching {
                !Instant.parse(record.recordedAt).isBefore(cutoff)
            }.getOrDefault(false)
            val searchable = listOfNotNull(
                profile?.fullName, profile?.className, record.subjectName,
                record.chapterName, record.lessonName, record.homeworkTitle,
                record.homeworkStatus, record.examKind
            ).joinToString(" ").lowercase()
            matchesDate && (needle.isBlank() || searchable.contains(needle))
        }
    }
    val studentCount = filteredRecords.map { it.studentId }.distinct().size
    val studyMinutes = filteredRecords.filter { it.recordType == "study_session" }.sumOf { it.durationSeconds.toLong() } / 60
    val completedLessons = filteredRecords.count { it.recordType == "lesson_progress" && it.lessonCompleted }
    val homeworkCounts = listOf("كُتب", "اكتمل", "شبه منجز", "لم ينجز").associateWith { state ->
        filteredRecords.count { it.recordType == "homework_status" && it.homeworkStatus == state }
    }

    fun loadReports() {
        loading = true
        scope.launch {
            try {
                val loadedProfiles = backend.loadManagedProfiles(session)
                val loadedRecords = backend.loadStudentStudyRecords(session)
                profiles = loadedProfiles
                records = loadedRecords
                error = null
            } catch (e: Exception) {
                error = e.message ?: if (isEnglish) "Could not load study reports." else "تعذر تحميل تقارير الدراسة."
            } finally {
                loading = false
            }
        }
    }

    Card(
        onClick = {
            open = true
            if (records.isEmpty() && !loading) loadReports()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isEnglish) "Study progress reports" else "تقارير التقدم الدراسي", fontWeight = FontWeight.Bold)
                Text(if (isEnglish) "View synced student activity" else "عرض نشاط الطلاب المتزامن", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (isEnglish) "Open" else "فتح", color = MaterialTheme.colorScheme.primary)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(if (isEnglish) "Student study reports" else "تقارير دراسة الطلاب", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (isEnglish) "Search student, class or subject" else "بحث باسم الطالب أو الصف أو المادة") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = rangeDays == 7, onClick = { rangeDays = 7 }, label = { Text(if (isEnglish) "7 days" else "7 أيام") })
                        FilterChip(selected = rangeDays == 30, onClick = { rangeDays = 30 }, label = { Text(if (isEnglish) "30 days" else "30 يوماً") })
                        FilterChip(selected = rangeDays == null, onClick = { rangeDays = null }, label = { Text(if (isEnglish) "All loaded" else "كل المحمّل") })
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (isEnglish) "Filtered summary" else "ملخص النتائج المفلترة", fontWeight = FontWeight.Bold)
                            Text(
                                if (isEnglish) "$studentCount students · $studyMinutes study minutes · $completedLessons completed lessons"
                                else "$studentCount طالب · $studyMinutes دقيقة دراسة · $completedLessons درس مكتمل"
                            )
                            Text(
                                if (isEnglish) "Homework: " + homeworkCounts.values.joinToString(" / ")
                                else "الواجبات — كُتب: ${homeworkCounts["كُتب"]} · اكتمل: ${homeworkCounts["اكتمل"]} · شبه منجز: ${homeworkCounts["شبه منجز"]} · لم ينجز: ${homeworkCounts["لم ينجز"]}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        if (isEnglish) "Showing up to the latest 500 server records. “All loaded” means all records returned by this request."
                        else "يعرض التطبيق أحدث 500 سجل كحد أقصى. خيار «كل المحمّل» يشمل السجلات التي أعادها الخادم فقط.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!loading && error == null && filteredRecords.isEmpty()) {
                        Text(if (isEnglish) "No records match these filters." else "لا توجد سجلات تطابق عوامل التصفية.")
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRecords, key = { it.id }) { record ->
                            val profile = profiles.firstOrNull { it.id == record.studentId }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(profile?.fullName ?: record.studentId.take(8), fontWeight = FontWeight.Bold)
                                    Text(listOfNotNull(profile?.className, record.subjectName).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        when (record.recordType) {
                                            "study_session" -> (if (isEnglish) "Study session · " else "جلسة دراسة · ") + "${record.durationSeconds / 60} " + if (isEnglish) "min" else "دقيقة"
                                            "lesson_progress" -> if (record.lessonCompleted) "درس مكتمل" else "تقدم درس"
                                            "homework_status" -> (record.homeworkTitle ?: "واجب") + " · " + (record.homeworkStatus ?: "")
                                            "exam_scope" -> "نطاق امتحان · " + (record.examKind ?: "")
                                            else -> record.recordType
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    listOfNotNull(record.chapterName, record.lessonName).takeIf { it.isNotEmpty() }?.let {
                                        Text(it.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(record.recordedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (filteredRecords.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val report = buildString {
                                    appendLine("RUVEN — Student study activity")
                                    appendLine("Range: ${rangeDays?.let { "$it days" } ?: "all loaded"}")
                                    appendLine("Search: $search")
                                    filteredRecords.forEach { record ->
                                        val name = profiles.firstOrNull { it.id == record.studentId }?.fullName ?: record.studentId
                                        appendLine("$name | ${record.subjectName} | ${record.recordType} | ${record.chapterName.orEmpty()} | ${record.lessonName.orEmpty()} | ${record.homeworkStatus.orEmpty()} | ${record.examKind.orEmpty()} | ${record.durationSeconds}s | ${record.recordedAt}")
                                    }
                                }
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), if (isEnglish) "Share filtered report" else "مشاركة التقرير المفلتر"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isEnglish) "Export filtered report" else "تصدير النتائج المفلترة") }
                    }
                    Text(
                        if (isEnglish) "Only authorized school administrators can access this view. Data reflects student-submitted activity, not independently verified learning."
                        else "هذه الصفحة متاحة لإدارة المدرسة المخوّلة فقط. البيانات تسجل نشاط الطالب المرسل ولا تثبت وحدها تحقق التعلم.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(if (isEnglish) "Close" else "إغلاق") }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { loadReports() }) {
                    Text(if (isEnglish) "Refresh" else "تحديث")
                }
            }
        )
    }
}
