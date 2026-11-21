package iq.ruven.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

internal object RuvenUpdater {
    internal const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private const val MIN_APK_BYTES = 1_000_000L
    private val trustedAssetHosts = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    internal fun isTrustedReleaseAssetUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.equals("github.com", ignoreCase = true) == true &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.startsWith("/mostafa0782/RUVEN/releases/download/") &&
            uri.path.length > "/mostafa0782/RUVEN/releases/download/".length
    }

    private fun isTrustedDownloadTarget(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.lowercase() in trustedAssetHosts &&
            url.userInfo == null &&
            (url.port == -1 || url.port == 443)

    data class Release(val versionName: String, val apkUrl: String, val sha256: String)

    suspend fun latestRelease(): Release = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/mostafa0782/RUVEN/releases/latest").openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "RUVEN-Android")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                if (status == 404) throw IllegalStateException("ماكو إصدار رسمي منشور حالياً.")
                throw IllegalStateException("تعذر فحص التحديثات (HTTP $status).")
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v").trim()
            val assets = json.optJSONArray("assets") ?: throw IllegalStateException("الإصدار ما يحتوي ملف تثبيت.")
            var apkUrl: String? = null
            var checksumUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                when (asset.optString("name")) {
                    "RUVEN.apk" -> apkUrl = asset.optString("browser_download_url")
                        .takeIf(::isTrustedReleaseAssetUrl)
                    "RUVEN.apk.sha256" -> checksumUrl = asset.optString("browser_download_url")
                        .takeIf(::isTrustedReleaseAssetUrl)
                }
            }
            val verifiedApkUrl = apkUrl ?: throw IllegalStateException("ملف RUVEN.apk غير موجود بالإصدار الرسمي.")
            val verifiedChecksumUrl = checksumUrl ?: throw IllegalStateException("الإصدار الرسمي لا يحتوي بصمة تحقق للملف.")
            val checksumConnection = URL(verifiedChecksumUrl).openConnection() as HttpURLConnection
            val checksumText = try {
                checksumConnection.connectTimeout = 12000
                checksumConnection.readTimeout = 12000
                checksumConnection.setRequestProperty("User-Agent", "RUVEN-Android")
                if (checksumConnection.responseCode !in 200..299 ||
                    !isTrustedDownloadTarget(checksumConnection.url)
                ) {
                    throw IllegalStateException("تعذر التحقق من بصمة ملف التحديث.")
                }
                checksumConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine().orEmpty().trim() }
            } finally {
                checksumConnection.disconnect()
            }
            val checksum = checksumText.substringBefore(' ').trim().lowercase()
            if (!checksum.matches(Regex("[0-9a-f]{64}"))) {
                throw IllegalStateException("بصمة ملف التحديث غير صالحة.")
            }
            Release(tag, verifiedApkUrl, checksum)
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(current: String, latest: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(current)
        val b = parts(latest)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (right != left) return right > left
        }
        return false
    }

    suspend fun downloadAndInstall(activity: Activity, apkUrl: String, expectedSha256: String) = withContext(Dispatchers.IO) {
        if (!isTrustedReleaseAssetUrl(apkUrl)) {
            throw IllegalArgumentException("رابط التحديث غير موثوق.")
        }
        if (!expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw IllegalArgumentException("بصمة التحديث غير صالحة.")
        }

        val connection = (URL(apkUrl).openConnection() as HttpURLConnection)
        val apk = File(activity.cacheDir, "RUVEN-update.apk")
        var verified = false
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "RUVEN-Android")
            val status = connection.responseCode
            if (status !in 200..299 || !isTrustedDownloadTarget(connection.url)) {
                throw IllegalStateException("تعذر تنزيل التحديث من مصدر موثوق (HTTP $status).")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_APK_BYTES) {
                throw IllegalStateException("حجم ملف التحديث يتجاوز الحد الآمن.")
            }

            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloadedBytes += count
                        if (downloadedBytes > MAX_APK_BYTES) {
                            throw IllegalStateException("حجم ملف التحديث يتجاوز الحد الآمن.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (downloadedBytes < MIN_APK_BYTES) {
                throw IllegalStateException("ملف التحديث غير مكتمل أو غير صالح.")
            }

            val actualSha256 = apk.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }
            if (!MessageDigest.isEqual(
                    actualSha256.toByteArray(Charsets.US_ASCII),
                    expectedSha256.lowercase().toByteArray(Charsets.US_ASCII)
                )
            ) {
                throw IllegalStateException("فشل التحقق من سلامة ملف التحديث؛ لم يتم تثبيته.")
            }
            verified = true
        } finally {
            connection.disconnect()
            if (!verified) apk.delete()
        }

        withContext(Dispatchers.Main) {
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"))
                activity.startActivity(settings)
                throw IllegalStateException("فعّل السماح بتثبيت التطبيقات من هذا المصدر، ثم ارجع واضغط تحديث التطبيق مرة ثانية.")
            }
            activity.startActivity(intent)
        }
    }
}
