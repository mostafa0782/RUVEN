package iq.ruven.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun TextbookAdminCard(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    isEnglish: Boolean
) {
    var showManager by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (isEnglish) "Textbook administration" else "إدارة الكتب الدراسية", fontWeight = FontWeight.Bold)
                Text(if (isEnglish) "Upload, review and publish official PDFs" else "رفع الكتب ومراجعتها واعتمادها للطلاب", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showManager = true }) {
                Text(if (isEnglish) "Manage" else "إدارة")
            }
        }
    }
    if (showManager) {
        TextbookAdminDialog(session, backend, isEnglish, onDismiss = { showManager = false })
    }
}

@Composable
private fun TextbookAdminDialog(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var grade by remember { mutableStateOf("الثالث المتوسط") }
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("الرياضيات") }
    var title by remember { mutableStateOf("") }
    var edition by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2026") }
    var publisher by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SupabaseBackend.TextbookCatalogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedPdf = uri
            selectedPdfName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            error = ""
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            try {
                entries = backend.loadTextbookCatalog(session)
                error = ""
            } catch (e: Exception) {
                error = e.message ?: if (isEnglish) "Could not load textbook entries." else "تعذر تحميل سجلات الكتب."
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.96f).heightIn(max = 760.dp)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnglish) "Textbook catalogue manager" else "إدارة فهرس الكتب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(if (isEnglish) "Close" else "إغلاق") }
                }
                Text(if (isEnglish) "New official textbook PDF" else "إضافة كتاب رسمي جديد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(grade, { grade = it }, label = { Text(if (isEnglish) "Grade" else "الصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(className, { className = it }, label = { Text(if (isEnglish) "Class / section (optional)" else "الشعبة (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(subject, { subject = it }, label = { Text(if (isEnglish) "Subject" else "المادة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text(if (isEnglish) "Book title" else "عنوان الكتاب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(edition, { edition = it }, label = { Text(if (isEnglish) "Edition / version" else "الطبعة / الإصدار") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(if (isEnglish) "Publication year" else "سنة النشر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(publisher, { publisher = it }, label = { Text(if (isEnglish) "Publisher (optional)" else "الناشر (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(source, { source = it }, label = { Text(if (isEnglish) "Source / authority" else "جهة المصدر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text(if (isEnglish) "Official HTTPS source URL (optional)" else "رابط المصدر الرسمي HTTPS (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedPdfName.isBlank()) (if (isEnglish) "Choose PDF (max 20 MB)" else "اختيار PDF (حد أقصى 20 ميغابايت)") else selectedPdfName)
                }
                Text(if (isEnglish) "New uploads remain pending until an administrator approves them." else "أي ملف جديد يبقى قيد المراجعة ولا يظهر للطلاب قبل اعتماده.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !saving && selectedPdf != null && grade.isNotBlank() && subject.isNotBlank() && title.isNotBlank() && edition.isNotBlank(),
                    onClick = {
                        val uri = selectedPdf ?: return@Button
                        scope.launch {
                            saving = true
                            error = ""
                            notice = ""
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val output = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        var total = 0
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            require(total <= 20_971_520) { "حجم الملف يتجاوز 20 ميغابايت." }
                                            output.write(buffer, 0, count)
                                        }
                                        output.toByteArray()
                                    } ?: throw IllegalStateException("تعذر قراءة ملف PDF.")
                                }
                                val yearValue = year.toIntOrNull()
                                require(year.isBlank() || (yearValue != null && yearValue in 2000..2100)) { "سنة النشر غير صالحة." }
                                backend.uploadTextbook(
                                    session, grade, className, subject, title, edition, yearValue,
                                    publisher, source, sourceUrl, bytes
                                )
                                selectedPdf = null
                                selectedPdfName = ""
                                title = ""
                                notice = if (isEnglish) "Uploaded. Awaiting review." else "تم رفع الكتاب، وهو الآن بانتظار الاعتماد."
                                reload()
                            } catch (e: Exception) {
                                error = e.message ?: if (isEnglish) "Upload failed." else "فشل رفع الكتاب."
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (isEnglish) "Upload for review" else "رفع وإرسال للمراجعة")
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnglish) "Catalogue entries" else "سجلات الفهرس", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reload() }, enabled = !loading) { Text(if (isEnglish) "Refresh" else "تحديث") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!loading && entries.isEmpty()) Text(if (isEnglish) "No textbook records." else "لا توجد كتب مسجلة بعد.", style = MaterialTheme.typography.bodySmall)
                entries.forEach { entry ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text("${entry.gradeName} · ${entry.subjectName} · ${entry.editionLabel}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                when (entry.status) {
                                    "approved" -> if (isEnglish) "Approved" else "معتمد"
                                    "rejected" -> if (isEnglish) "Rejected" else "مرفوض"
                                    else -> if (isEnglish) "Pending review" else "بانتظار المراجعة"
                                },
                                color = when (entry.status) {
                                    "approved" -> MaterialTheme.colorScheme.primary
                                    "rejected" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (entry.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewTextbook(session, entry.id, true)
                                                    notice = if (isEnglish) "Textbook approved." else "تم اعتماد الكتاب."
                                                    reload()
                                                } catch (e: Exception) { error = e.message ?: "تعذر اعتماد الكتاب." }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (isEnglish) "Approve" else "اعتماد") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewTextbook(session, entry.id, false)
                                                    notice = if (isEnglish) "Textbook rejected." else "تم رفض الكتاب."
                                                    reload()
                                                } catch (e: Exception) { error = e.message ?: "تعذر رفض الكتاب." }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (isEnglish) "Reject" else "رفض") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
