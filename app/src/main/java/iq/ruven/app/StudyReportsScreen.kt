package iq.ruven.app

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
import android.content.Intent

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

    Card(
        onClick = {
            open = true
            if (records.isEmpty() && !loading) {
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
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!loading && error == null && records.isEmpty()) {
                        Text(if (isEnglish) "No synced student records yet." else "لا توجد سجلات دراسية متزامنة حتى الآن.")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(records, key = { it.id }) { record ->
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
                                }
                            }
                        }
                    }
                    if (records.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val report = buildString {
                                    appendLine("RUVEN — Student study activity")
                                    records.forEach { record ->
                                        val name = profiles.firstOrNull { it.id == record.studentId }?.fullName ?: record.studentId
                                        appendLine("$name | ${record.subjectName} | ${record.recordType} | ${record.chapterName.orEmpty()} | ${record.lessonName.orEmpty()} | ${record.homeworkStatus.orEmpty()} | ${record.examKind.orEmpty()} | ${record.durationSeconds}s")
                                    }
                                }
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), if (isEnglish) "Share report" else "مشاركة التقرير"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isEnglish) "Export / share report" else "تصدير / مشاركة التقرير") }
                    }
                    Text(if (isEnglish) "Only authorized school administrators can access this view. Data reflects student-submitted activity, not independently verified learning." else "هذه الصفحة متاحة لإدارة المدرسة المخوّلة فقط. البيانات تسجل نشاط الطالب المرسل ولا تثبت وحدها تحقق التعلم.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(if (isEnglish) "Close" else "إغلاق") }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        try {
                            profiles = backend.loadManagedProfiles(session)
                            records = backend.loadStudentStudyRecords(session)
                            error = null
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر تحديث التقارير."
                        } finally { loading = false }
                    }
                }) { Text(if (isEnglish) "Refresh" else "تحديث") }
            }
        )
    }
}
