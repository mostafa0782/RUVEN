package iq.ruven.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID

internal fun createLostItemClaimToken(): String = UUID.randomUUID().toString().replace("-", "").uppercase()

private fun createClaimQrBitmap(token: String): Bitmap? = runCatching {
    require(token.matches(Regex("[A-F0-9]{32}")))
    val matrix = QRCodeWriter().encode(
        "RUVEN-CLAIM:$token",
        BarcodeFormat.QR_CODE,
        640,
        640,
        mapOf(EncodeHintType.MARGIN to 2)
    )
    Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 640) for (x in 0 until 640) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()

@Composable
internal fun LostItemClaimQr(token: String) {
    val bitmap = remember(token) { createClaimQrBitmap(token) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "RUVEN lost-item claim QR", modifier = Modifier.size(220.dp))
            Text(token, style = MaterialTheme.typography.labelSmall)
            Text("رمز عشوائي فقط — لا يحتوي اسمك أو رقمك أو موقعك", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("تعذر إنشاء رمز QR. حاول مرة أخرى.", color = MaterialTheme.colorScheme.error)
        }
    }
}
