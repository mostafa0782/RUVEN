package iq.ruven.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.launch

private val SettingsNavy = Color(0xFF10253F)
private val SettingsBlue = Color(0xFF2867D8)
private val SettingsMuted = Color(0xFF718096)

@Composable
fun RuvSettingsScreen(prefs: SharedPreferences, session: SupabaseBackend.Session, backend: SupabaseBackend, onSaved: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(prefs.getString("settings_language", "ar") ?: "ar") }
    var appearance by remember { mutableStateOf(prefs.getString("settings_appearance", "dark") ?: "dark") }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("settings_font_scale", 1f)) }
    var background by remember { mutableStateOf(prefs.getString("settings_background", "navy") ?: "navy") }
    var glassOpacity by remember { mutableFloatStateOf(prefs.getFloat("settings_glass_opacity", .72f)) }
    var glassBlur by remember { mutableFloatStateOf(prefs.getFloat("settings_glass_blur", 12f)) }
    var glassGlow by remember { mutableFloatStateOf(prefs.getFloat("settings_glass_glow", .45f)) }
    var wallpaperUri by remember { mutableStateOf(prefs.getString("settings_wallpaper_uri", null)) }
    var saved by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordBusy by remember { mutableStateOf(false) }
    var passwordMessage by remember { mutableStateOf<String?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            wallpaperUri = uri.toString()
            prefs.edit().putString("settings_wallpaper_uri", wallpaperUri).apply()
            saved = true
            onSaved()
        }
    }
    fun openIntent(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "تعذر فتح هذا الخيار على الجهاز.", Toast.LENGTH_LONG).show()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("الإعدادات", color = SettingsNavy, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                Text("الحساب، المظهر، الخلفيات، الخصوصية والدعم في مكان واحد.", color = SettingsMuted, fontSize = 12.sp)
            }
        }
        item { SettingsGroup("الحساب والأمان", Icons.Rounded.Security) {
            SettingsAction("تغيير كلمة المرور", "تحديث كلمة مرور حسابك المدرسي", Icons.Rounded.Lock) { dialog = "password"; passwordMessage = null }
            SettingsAction("تسجيل الخروج", "إنهاء جلسة هذا الحساب على الجهاز", Icons.Rounded.Logout) { dialog = "signout" }
        } }
        item { SettingsGroup("اللغة وإمكانية الوصول", Icons.Rounded.Language) {
            Text("لغة التطبيق", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            listOf("ar" to "العربية", "en" to "English", "mixed" to "مختلط").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = language == v, onClick = { language = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text("تنبيه: تفضيل اللغة يُحفظ، لكن ترجمة جميع شاشات التطبيق لم تكتمل بعد.", color = SettingsMuted, fontSize = 11.sp)
            HorizontalDivider()
            Text("حجم النص: ${(fontScale * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = fontScale, onValueChange = { fontScale = it; saved = false }, valueRange = .85f..1.3f)
        } }
        item { SettingsGroup("المظهر والتخصيص", Icons.Rounded.Palette) {
            listOf("dark" to "داكن", "light" to "فاتح", "glass" to "زجاجي").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = appearance == v, onClick = { appearance = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text("لون الخلفية", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            listOf("navy" to "كحلي", "midnight" to "ليل داكن", "light" to "فاتح", "mint" to "نعناعي").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = background == v, onClick = { background = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text("شفافية الزجاج: ${(glassOpacity * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassOpacity, onValueChange = { glassOpacity = it; saved = false }, valueRange = .25f.. .96f)
            Text("تمويه الخلفية: ${glassBlur.toInt()} dp", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassBlur, onValueChange = { glassBlur = it; saved = false }, valueRange = 0f..28f)
            Text("لمعان الزجاج: ${(glassGlow * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassGlow, onValueChange = { glassGlow = it; saved = false }, valueRange = 0f..1f)
        } }
        item { SettingsGroup("الخلفيات", Icons.Rounded.Wallpaper) {
            SettingsAction("اختيار خلفية من الجهاز", "استخدم صورة ثابتة خلف محتوى التطبيق", Icons.Rounded.Image) { wallpaperPicker.launch(arrayOf("image/*")) }
            SettingsAction("إزالة الخلفية المخصصة", "الرجوع إلى لون الخلفية", Icons.Rounded.DeleteOutline) {
                wallpaperUri = null
                prefs.edit().remove("settings_wallpaper_uri").apply()
                saved = false
                onSaved()
            }
            SettingsAction("تعيين خلفية متحركة", "افتح معاينة خلفية RUVEN المتحركة للنظام", Icons.Rounded.MotionPhotosOn) {
                openIntent(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, RuvenLiveWallpaperService::class.java)
                ))
            }
            if (!wallpaperUri.isNullOrBlank()) Text("تم اختيار خلفية مخصصة.", color = Color(0xFF16845B), fontSize = 11.sp)
        } }
        item { SettingsGroup("الخصوصية والمعلومات", Icons.Rounded.Policy) {
            SettingsAction("سياسة الخصوصية", "البيانات واستخدامها وحمايتها", Icons.Rounded.PrivacyTip) { dialog = "privacy" }
            SettingsAction("الشروط والأحكام", "قواعد استخدام منظومة المدرسة", Icons.Rounded.Description) { dialog = "terms" }
            SettingsAction("من نحن", "تعريف بمنظومة RUVEN", Icons.Rounded.Info) { dialog = "about" }
        } }
        item { SettingsGroup("الدعم والمشاركة", Icons.Rounded.SupportAgent) {
            SettingsAction("اتصل بنا / الدعم", "افتح صفحة الإبلاغ عن مشكلة", Icons.Rounded.Email) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mostafa0782/RUVEN/issues")))
            }
            SettingsAction("مشاركة RUVEN", "شارك رابط المشروع", Icons.Rounded.Share) {
                openIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "RUVEN - المنظومة المدرسية")
                    putExtra(Intent.EXTRA_TEXT, "RUVEN — منظومة مدرسية\nhttps://github.com/mostafa0782/RUVEN")
                }, "مشاركة RUVEN"))
            }
            SettingsAction("قيّم التطبيق / أرسل ملاحظاتك", "أرسل اقتراحاً أو بلّغ عن مشكلة", Icons.Rounded.StarRate) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mostafa0782/RUVEN/issues")))
            }
        } }
        item {
            Button(onClick = {
                prefs.edit().putString("settings_language", language).putString("settings_appearance", appearance)
                    .putFloat("settings_font_scale", fontScale).putString("settings_background", background)
                    .putFloat("settings_glass_opacity", glassOpacity).putFloat("settings_glass_blur", glassBlur)
                    .putFloat("settings_glass_glow", glassGlow).apply()
                saved = true
                onSaved()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("حفظ جميع الإعدادات")
            }
            if (saved) Text("تم حفظ التفضيلات على هذا الجهاز.", color = Color(0xFF16845B), fontSize = 12.sp)
        }
    }
    if (dialog == "signout") AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text("تسجيل الخروج؟") },
        text = { Text("سيتم إنهاء الجلسة على هذا الجهاز. يمكنك تسجيل الدخول مرة أخرى لاحقاً.") },
        confirmButton = { TextButton(onClick = { dialog = null; onSignOut() }) { Text("تسجيل الخروج") } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } }
    )
    if (dialog == "password") AlertDialog(
        onDismissRequest = { if (!passwordBusy) dialog = null },
        title = { Text("تغيير كلمة المرور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(newPassword, { newPassword = it; passwordMessage = null }, label = { Text("كلمة المرور الجديدة") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(confirmPassword, { confirmPassword = it; passwordMessage = null }, label = { Text("تأكيد كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                passwordMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(enabled = !passwordBusy, onClick = {
            when {
                newPassword.length < 8 -> passwordMessage = "استخدم 8 محارف على الأقل."
                newPassword.length > 72 -> passwordMessage = "كلمة المرور طويلة أكثر من اللازم."
                newPassword != confirmPassword -> passwordMessage = "كلمتا المرور غير متطابقتين."
                else -> scope.launch {
                    passwordBusy = true
                    try {
                        backend.updatePassword(session, newPassword)
                        passwordMessage = "تم تغيير كلمة المرور بنجاح."
                        newPassword = ""; confirmPassword = ""
                    } catch (e: Exception) { passwordMessage = e.message ?: "تعذر تغيير كلمة المرور." }
                    finally { passwordBusy = false }
                }
            }
        }) { if (passwordBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("تغيير") } },
        dismissButton = { TextButton(onClick = { if (!passwordBusy) dialog = null }) { Text("إغلاق") } }
    )
    val info = when (dialog) {
        "privacy" -> "يستخدم RUVEN بيانات الحساب المدرسي ومحتوى المدرسة اللازم لتقديم الوظائف عبر خادم المدرسة وSupabase. لا تشارك كلمة مرورك. يجب على إدارة المدرسة اعتماد سياسة نهائية توضّح الاحتفاظ بالبيانات وطلبات الحذف قبل الإطلاق العام."
        "terms" -> "يُستخدم RUVEN للأغراض المدرسية المصرح بها. حافظ على سرية بيانات الدخول ولا تنشر معلومات الطلبة دون إذن. قد تتوقف بعض الوظائف أثناء الصيانة. هذه مسودة تحتاج اعتماد إدارة المدرسة."
        "about" -> "RUVEN منظومة رقمية مدرسية تهدف إلى جمع الإعلانات والجدول والواجبات والتحاضير والإشعارات وإدارة الحسابات المصرح بها في تطبيق واحد."
        else -> null
    }
    if (info != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(when (dialog) { "privacy" -> "سياسة الخصوصية"; "terms" -> "الشروط والأحكام"; else -> "من نحن" }) },
        text = { Text(info) },
        confirmButton = { TextButton(onClick = { dialog = null }) { Text("إغلاق") } }
    )
}

@Composable
private fun SettingsGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = SettingsBlue) }
                Text(title, color = SettingsNavy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color(0xFFF6F8FC), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = SettingsBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = SettingsNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = SettingsMuted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronLeft, null, tint = SettingsMuted)
        }
    }
}
