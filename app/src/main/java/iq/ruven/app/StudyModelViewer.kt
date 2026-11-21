package iq.ruven.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_STUDY_GLB_BYTES = 25L * 1024L * 1024L

@Composable
fun StudyModelViewerDialog(isEnglish: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modelFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = ""
                try {
                    modelFile = copyValidatedGlb(context, uri)
                } catch (failure: Exception) {
                    error = failure.message ?: if (isEnglish) "Could not open this GLB file." else "تعذر فتح ملف GLB."
                } finally {
                    busy = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isEnglish) "3D learning model" else "المجسم التعليمي ثلاثي الأبعاد",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (isEnglish) "GLB · interactive viewer" else "GLB · عارض تفاعلي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(if (isEnglish) "Close" else "إغلاق")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        enabled = !busy
                    ) {
                        Text(
                            if (modelFile == null) {
                                if (isEnglish) "Choose GLB" else "اختيار مجسم GLB"
                            } else {
                                if (isEnglish) "Change model" else "تغيير المجسم"
                            }
                        )
                    }
                    if (modelFile != null) {
                        OutlinedButton(
                            onClick = { modelFile = null; error = "" },
                            enabled = !busy
                        ) {
                            Text(if (isEnglish) "Remove" else "إزالة")
                        }
                    }
                }

                Text(
                    modelFile?.name ?: if (isEnglish) "No model selected" else "لم يتم اختيار مجسم بعد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val selectedFile = modelFile
                    if (selectedFile == null) {
                        Text(
                            if (isEnglish) "Choose a GLB model to view it here." else "اختر ملف GLB ليظهر المجسم هنا.",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        StudyModelScene(
                            file = selectedFile,
                            isEnglish = isEnglish,
                            onError = { error = it }
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(38.dp))
                    }
                    if (error.isNotBlank()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Text(
                    if (isEnglish) "Touch and drag to rotate · pinch to zoom · two-finger movement to pan."
                    else "اسحب بإصبع واحد لتدوير المجسم، وقرّب بإصبعين للتكبير أو التصغير، وحرك بإصبعين لتحريك المشهد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StudyModelScene(file: File, isEnglish: Boolean, onError: (String) -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 4.0f)
    }

    val modelInstance by produceState<io.github.sceneview.model.ModelInstance?>(
        initialValue = null,
        key1 = file.absolutePath
    ) {
        value = try {
            modelLoader.loadModelInstance(Uri.fromFile(file).path ?: file.absolutePath)
        } catch (failure: Exception) {
            onError(
                failure.message ?: if (isEnglish) "The model could not be rendered." else "تعذر عرض المجسم."
            )
            null
        }
    }

    val modelNode = remember(modelInstance) {
        modelInstance?.let { ModelNode(modelInstance = it, scaleToUnits = 1.2f) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = cameraNode.worldPosition,
                targetPosition = Position()
            ),
            childNodes = listOfNotNull(modelNode)
        )
        if (modelInstance == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

private suspend fun copyValidatedGlb(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val destination = File(context.cacheDir, "ruven_study_model_" + System.currentTimeMillis() + ".glb")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر قراءة الملف المحدد.")
        input.use { source ->
            destination.outputStream().buffered().use { output ->
                val header = ByteArray(4)
                var headerRead = 0
                while (headerRead < header.size) {
                    val count = source.read(header, headerRead, header.size - headerRead)
                    if (count < 0) break
                    headerRead += count
                }
                require(
                    headerRead == 4 &&
                        header[0] == 0x67.toByte() &&
                        header[1] == 0x6c.toByte() &&
                        header[2] == 0x54.toByte() &&
                        header[3] == 0x46.toByte()
                ) {
                    "هذا ليس ملف GLB صالحاً. اختر ملفاً يبدأ بترويسة glTF."
                }
                output.write(header)
                var total = 4L
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_STUDY_GLB_BYTES) {
                        "حجم المجسم يتجاوز الحد المسموح (25 MB)."
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        destination
    } catch (failure: Exception) {
        destination.delete()
        throw failure
    }
}
