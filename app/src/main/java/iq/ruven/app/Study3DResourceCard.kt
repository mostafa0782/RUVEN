package iq.ruven.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ViewInAr
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
import java.io.File

private const val MAX_STUDY_MODEL_BYTES = 25 * 1024 * 1024

@Composable
fun Study3DResourceSection(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    subjectName: String,
    isEnglish: Boolean,
    onOpenModel: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<SupabaseBackend.Study3DResource>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                entries = backend.loadStudy3DResources(session, subjectName)
                    .filter { it.status == "approved" }
            } catch (failure: Exception) {
                error = failure.message ?: if (isEnglish) "Could not load 3D resources." else "تعذر تحميل المجسمات التعليمية."
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(session.userId, subjectName) { reload() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (isEnglish) "Approved 3D learning resources" else "المجسمات التعليمية المعتمدة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { reload() }, enabled = !loading && downloadingId == null) {
                    Text(if (isEnglish) "Refresh" else "تحديث")
                }
            }
            Text(
                if (isEnglish) "Licensed GLB models with a 2D fallback for unsupported devices."
                else "مجسمات GLB مرخّصة، مع بديل ثنائي الأبعاد للأجهزة التي لا تدعم العرض ثلاثي الأبعاد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (!loading && error.isBlank() && entries.isEmpty()) {
                Text(if (isEnglish) "No approved models for this subject yet." else "لا توجد مجسمات معتمدة لهذه المادة حالياً.", style = MaterialTheme.typography.bodySmall)
            }
            entries.forEach { entry ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    entry.chapterName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    entry.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        (if (isEnglish) "License: " else "الترخيص: ") + entry.licenseName +
                            " · " + (if (isEnglish) "By: " else "المصدر: ") + entry.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(entry.fallback2DDescription, style = MaterialTheme.typography.bodySmall)
                    entry.fallback2DUrl?.let { url ->
                        TextButton(onClick = { openHttps(context, url) }) {
                            Text(if (isEnglish) "Open 2D alternative" else "فتح البديل ثنائي الأبعاد")
                        }
                    }
                    entry.sourceUrl?.let { url ->
                        TextButton(onClick = { openHttps(context, url) }) {
                            Text(if (isEnglish) "Open source / license" else "فتح المصدر والترخيص")
                        }
                    }
                    Button(
                        enabled = downloadingId == null,
                        onClick = {
                            scope.launch {
                                downloadingId = entry.id
                                error = ""
                                try {
                                    val bytes = backend.downloadApprovedStudy3DResource(session, entry.storagePath)
                                    require(bytes.size in 12..MAX_STUDY_MODEL_BYTES) {
                                        if (isEnglish) "Model exceeds the 25 MB limit." else "حجم المجسم يتجاوز 25 ميغابايت."
                                    }
                                    val declared = glbDeclaredLength(bytes.copyOfRange(0, 12))
                                    require(declared == bytes.size.toLong()) {
                                        if (isEnglish) "The downloaded GLB file is invalid." else "ملف GLB المنزّل غير صالح."
                                    }
                                    val file = withContext(Dispatchers.IO) {
                                        File(context.cacheDir, "ruven_catalog_${entry.id}.glb").apply { writeBytes(bytes) }
                                    }
                                    onOpenModel(file)
                                } catch (failure: Exception) {
                                    error = failure.message ?: if (isEnglish) "Could not open this model." else "تعذر فتح المجسم."
                                } finally {
                                    downloadingId = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (downloadingId == entry.id) (if (isEnglish) "Downloading…" else "جارٍ التنزيل…") else (if (isEnglish) "Download and open 3D model" else "تنزيل وفتح المجسم"))
                    }
                }
            }
        }
    }
}

@Composable
fun Study3DResourceAdminCard(
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
            Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (isEnglish) "3D learning resources" else "المجسمات التعليمية ثلاثية الأبعاد", fontWeight = FontWeight.Bold)
                Text(if (isEnglish) "Upload licensed models and review publication" else "رفع مجسمات مرخّصة ومراجعة اعتمادها", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showManager = true }) { Text(if (isEnglish) "Manage" else "إدارة") }
        }
    }
    if (showManager) Study3DResourceAdminDialog(session, backend, isEnglish) { showManager = false }
}

@Composable
private fun Study3DResourceAdminDialog(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("العلوم") }
    var chapter by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("CC BY 4.0") }
    var attribution by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var fallbackDescription by remember { mutableStateOf("") }
    var fallbackUrl by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SupabaseBackend.Study3DResource>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty()
            error = ""
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            try {
                entries = backend.loadStudy3DResources(session)
                error = ""
            } catch (failure: Exception) {
                error = failure.message ?: if (isEnglish) "Could not load model catalogue." else "تعذر تحميل فهرس المجسمات."
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.97f).heightIn(max = 780.dp)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnglish) "3D catalogue manager" else "إدارة فهرس المجسمات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(if (isEnglish) "Close" else "إغلاق") }
                }
                Text(if (isEnglish) "New licensed GLB model" else "إضافة مجسم GLB مرخّص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(subject, { subject = it }, label = { Text(if (isEnglish) "Subject" else "المادة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(chapter, { chapter = it }, label = { Text(if (isEnglish) "Chapter / lesson (optional)" else "الفصل / الدرس (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text(if (isEnglish) "Model title" else "عنوان المجسم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(if (isEnglish) "Description (optional)" else "الوصف (اختياري)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(license, { license = it }, label = { Text(if (isEnglish) "License (required)" else "الترخيص (مطلوب)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(attribution, { attribution = it }, label = { Text(if (isEnglish) "Creator / attribution (required)" else "صاحب العمل / نسبة المصدر (مطلوب)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text(if (isEnglish) "Source URL (HTTPS, optional)" else "رابط المصدر HTTPS (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(fallbackDescription, { fallbackDescription = it }, label = { Text(if (isEnglish) "2D fallback description (required)" else "شرح البديل ثنائي الأبعاد (مطلوب)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(fallbackUrl, { fallbackUrl = it }, label = { Text(if (isEnglish) "2D fallback URL (HTTPS, optional)" else "رابط البديل ثنائي الأبعاد HTTPS (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { picker.launch(arrayOf("model/gltf-binary", "model/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedName.isBlank()) (if (isEnglish) "Choose GLB (max 25 MB)" else "اختيار GLB (حد أقصى 25 ميغابايت)") else selectedName)
                }
                Text(if (isEnglish) "Uploads remain private and pending until an administrator approves them. Only approved resources are visible to students."
                    else "يبقى الملف خاصاً وقيد المراجعة إلى أن تعتمد الإدارة نشره. الطلاب لا يرون إلا الموارد المعتمدة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !saving && selectedUri != null && subject.isNotBlank() && title.isNotBlank() && license.isNotBlank() && attribution.isNotBlank() && fallbackDescription.isNotBlank(),
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        scope.launch {
                            saving = true
                            error = ""
                            notice = ""
                            try {
                                val bytes = withContext(Dispatchers.IO) { readBoundedBytes(context, uri, MAX_STUDY_MODEL_BYTES) }
                                val declared = glbDeclaredLength(bytes.copyOfRange(0, minOf(12, bytes.size)))
                                require(declared == bytes.size.toLong()) {
                                    if (isEnglish) "Selected file is not a complete glTF 2.0 binary (GLB)." else "الملف المحدد ليس GLB صالحاً ومكتملًا وفق glTF 2.0."
                                }
                                backend.uploadStudy3DResource(
                                    session, subject, chapter, title, description, license,
                                    attribution, sourceUrl, fallbackDescription, fallbackUrl, bytes
                                )
                                selectedUri = null
                                selectedName = ""
                                title = ""
                                notice = if (isEnglish) "Uploaded and awaiting review." else "تم رفع المجسم وهو بانتظار المراجعة."
                                reload()
                            } catch (failure: Exception) {
                                error = failure.message ?: if (isEnglish) "Upload failed." else "فشل رفع المجسم."
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnglish) "Catalogue entries" else "سجلات الفهرس", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reload() }, enabled = !loading) { Text(if (isEnglish) "Refresh" else "تحديث") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                entries.forEach { entry ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text("${entry.subjectName} · ${entry.licenseName}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                when (entry.status) {
                                    "approved" -> if (isEnglish) "Approved" else "معتمد"
                                    "rejected" -> if (isEnglish) "Rejected" else "مرفوض"
                                    else -> if (isEnglish) "Pending review" else "بانتظار المراجعة"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = when (entry.status) {
                                    "approved" -> MaterialTheme.colorScheme.primary
                                    "rejected" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                            if (entry.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewStudy3DResource(session, entry.id, true)
                                                    notice = if (isEnglish) "Model approved." else "تم اعتماد المجسم."
                                                    reload()
                                                } catch (failure: Exception) { error = failure.message ?: "تعذر اعتماد المجسم." }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (isEnglish) "Approve" else "اعتماد") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    backend.reviewStudy3DResource(session, entry.id, false)
                                                    notice = if (isEnglish) "Model rejected." else "تم رفض المجسم."
                                                    reload()
                                                } catch (failure: Exception) { error = failure.message ?: "تعذر رفض المجسم." }
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

private suspend fun readBoundedBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray =
    withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر قراءة الملف المحدد.")
        input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "حجم الملف يتجاوز الحد المسموح." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

private fun openHttps(context: Context, url: String) {
    if (!url.startsWith("https://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
