package iq.ruven.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RenderedPdfPage(val bitmap: Bitmap, val pageCount: Int)

@Composable
fun PdfBookReaderDialog(
    uriString: String,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean
) {
    val context = LocalContext.current
    var pageIndex by remember(uriString) { mutableIntStateOf((initialPage - 1).coerceAtLeast(0)) }
    var pageCount by remember(uriString) { mutableIntStateOf(0) }
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uriString, pageIndex) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val descriptor: ParcelFileDescriptor = if (uri.scheme == "file") {
                    val path = uri.path ?: throw IllegalStateException("مسار ملف PDF غير صالح.")
                    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("تعذر فتح ملف PDF.")
                }
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        require(renderer.pageCount > 0) { "ملف PDF لا يحتوي صفحات." }
                        val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safeIndex).use { page ->
                            val scale = (1500f / page.width).coerceAtMost(2f).coerceAtLeast(0.5f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            RenderedPdfPage(image, renderer.pageCount)
                        }
                    }
                }
            }
        }
        result.onSuccess {
            bitmap?.takeIf { old -> old !== it.bitmap }?.recycle()
            bitmap = it.bitmap
            pageCount = it.pageCount
            onPageChanged(pageIndex + 1)
        }.onFailure {
            error = it.message ?: if (isEnglish) "Could not render this PDF." else "تعذر عرض ملف PDF."
        }
        loading = false
    }

    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEnglish) "Book reader" else "قارئ الكتاب") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (pageCount > 0) (if (isEnglish) "Page " else "الصفحة ") + "${pageIndex + 1} / $pageCount"
                    else if (isEnglish) "Loading PDF…" else "جارٍ تحميل الكتاب…",
                    style = MaterialTheme.typography.labelLarge
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = "PDF page", modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentScale = ContentScale.Fit) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(enabled = !loading && pageIndex > 0, onClick = { pageIndex-- }) {
                        Text(if (isEnglish) "Previous" else "السابق")
                    }
                    OutlinedButton(enabled = !loading && pageCount > 0 && pageIndex < pageCount - 1, onClick = { pageIndex++ }) {
                        Text(if (isEnglish) "Next" else "التالي")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEnglish) "Close" else "إغلاق") } }
    )
}
