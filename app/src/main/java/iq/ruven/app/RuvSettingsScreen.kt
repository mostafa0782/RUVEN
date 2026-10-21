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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private fun st(ar: String, language: String): String = if (language != "en") ar else when (ar) {
    "الإعدادات" -> "Settings"
    "الحساب، المظهر، الخلفيات، الخصوصية والدعم في مكان واحد." -> "Account, appearance, wallpapers, privacy and support in one place."
    "الحساب والأمان" -> "Account & security"
    "تغيير كلمة المرور" -> "Change password"
    "تحديث كلمة مرور حسابك المدرسي" -> "Update your school account password"
    "تسجيل الخروج" -> "Sign out"
    "إنهاء جلسة هذا الحساب على الجهاز" -> "End this account session on this device"
    "اللغة وإمكانية الوصول" -> "Language & accessibility"
    "لغة التطبيق" -> "App language"
    "العربية" -> "Arabic"
    "English" -> "English"
    "مختلط" -> "Mixed"
    "تنبيه: تفضيل اللغة يُحفظ، لكن ترجمة جميع شاشات التطبيق لم تكتمل بعد." -> "English is applied to Settings; localization of the remaining app screens is still in progress."
    "المظهر والتخصيص" -> "Appearance & customization"
    "داكن" -> "Dark"
    "فاتح" -> "Light"
    "زجاجي" -> "Glass"
    "لون الخلفية" -> "Background color"
    "كحلي" -> "Navy"
    "ليل داكن" -> "Midnight"
    "نعناعي" -> "Mint"
    "الخلفيات" -> "Wallpapers"
    "اختيار خلفية من الجهاز" -> "Choose wallpaper from device"
    "استخدم صورة ثابتة خلف محتوى التطبيق" -> "Use a still image behind app content"
    "إزالة الخلفية المخصصة" -> "Remove custom wallpaper"
    "الرجوع إلى لون الخلفية" -> "Return to the background color"
    "تعيين خلفية متحركة" -> "Set live wallpaper"
    "افتح معاينة خلفية RUVEN المتحركة للنظام" -> "Open the system preview for RUVEN live wallpaper"
    "تم اختيار خلفية مخصصة." -> "A custom wallpaper is selected."
    "الخصوصية والمعلومات" -> "Privacy & information"
    "سياسة الخصوصية" -> "Privacy policy"
    "البيانات واستخدامها وحمايتها" -> "Data collection, use and protection"
    "الشروط والأحكام" -> "Terms & conditions"
    "قواعد استخدام منظومة المدرسة" -> "Rules for using the school system"
    "من نحن" -> "About RUVEN"
    "تعريف بمنظومة RUVEN" -> "About the RUVEN school system"
    "الدعم والمشاركة" -> "Support & sharing"
    "اتصل بنا / الدعم" -> "Contact & support"
    "افتح صفحة الإبلاغ عن مشكلة" -> "Open the issue-reporting page"
    "مشاركة RUVEN" -> "Share RUVEN"
    "شارك رابط المشروع" -> "Share the project link"
    "قيّم التطبيق / أرسل ملاحظاتك" -> "Rate the app / send feedback"
    "أرسل اقتراحاً أو بلّغ عن مشكلة" -> "Suggest an improvement or report a problem"
    "حفظ جميع الإعدادات" -> "Save all settings"
    "تم حفظ التفضيلات على هذا الجهاز." -> "Preferences saved on this device."
    "إلغاء" -> "Cancel"
    "حجم النص" -> "Text size"
    "شفافية الزجاج" -> "Glass opacity"
    "تمويه الخلفية" -> "Background blur"
    "لمعان الزجاج" -> "Glass glow"
    "تسجيل الخروج؟" -> "Sign out?"
    "سيتم إنهاء الجلسة على هذا الجهاز. يمكنك تسجيل الدخول مرة أخرى لاحقاً." -> "Your session on this device will end. You can sign in again later."
    "كلمة المرور الجديدة" -> "New password"
    "تأكيد كلمة المرور" -> "Confirm password"
    "استخدم 8 محارف على الأقل." -> "Use at least 8 characters."
    "كلمة المرور طويلة أكثر من اللازم." -> "The password is too long."
    "كلمتا المرور غير متطابقتين." -> "The passwords do not match."
    "تم تغيير كلمة المرور بنجاح." -> "Password changed successfully."
    "تعذر تغيير كلمة المرور." -> "Could not change the password."
    "كلمة المرور الجديدة" -> "New password"
    "تأكيد كلمة المرور" -> "Confirm password"
    "استخدم 8 محارف على الأقل." -> "Use at least 8 characters."
    "كلمة المرور طويلة أكثر من اللازم." -> "The password is too long."
    "كلمتا المرور غير متطابقتين." -> "The passwords do not match."
    "تم تغيير كلمة المرور بنجاح." -> "Password changed successfully."
    "تعذر تغيير كلمة المرور." -> "Could not change the password."
    "تغيير" -> "Change"
    "إغلاق" -> "Close"
    "حسناً" -> "OK"
    "سياسة الخصوصية" -> "Privacy policy"
    "الشروط والأحكام" -> "Terms & conditions"
    "إغلاق" -> "Close"
    else -> ar
}

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
                Text(st("الإعدادات", language), color = SettingsNavy, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                Text(st("الحساب، المظهر، الخلفيات، الخصوصية والدعم في مكان واحد.", language), color = SettingsMuted, fontSize = 12.sp)
            }
        }
        item { SettingsGroup("الحساب والأمان", Icons.Rounded.Security, language) {
            SettingsAction("تغيير كلمة المرور", "تحديث كلمة مرور حسابك المدرسي", Icons.Rounded.Lock, language) { dialog = "password"; passwordMessage = null }
            SettingsAction("تسجيل الخروج", "إنهاء جلسة هذا الحساب على الجهاز", Icons.Rounded.Logout, language) { dialog = "signout" }
        } }
        item { SettingsGroup("اللغة وإمكانية الوصول", Icons.Rounded.Language, language) {
            Text(st("لغة التطبيق", language), color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            listOf("ar" to if (language == "en") "Arabic" else "العربية", "en" to "English", "mixed" to if (language == "en") "Mixed" else "مختلط").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = language == v, onClick = { language = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text(st("تنبيه: تفضيل اللغة يُحفظ، لكن ترجمة جميع شاشات التطبيق لم تكتمل بعد.", language), color = SettingsMuted, fontSize = 11.sp)
            HorizontalDivider()
            Text(if (language == "en") "Text size: ${(fontScale * 100).toInt()}%" else "حجم النص: ${(fontScale * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = fontScale, onValueChange = { fontScale = it; saved = false }, valueRange = .85f..1.3f)
        } }
        item { SettingsGroup("المظهر والتخصيص", Icons.Rounded.Palette, language) {
            listOf("dark" to if (language == "en") "Dark" else "داكن", "light" to if (language == "en") "Light" else "فاتح", "glass" to if (language == "en") "Glass" else "زجاجي").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = appearance == v, onClick = { appearance = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text(st("لون الخلفية", language), color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            listOf("navy" to if (language == "en") "Navy" else "كحلي", "midnight" to if (language == "en") "Midnight" else "ليل داكن", "light" to if (language == "en") "Light" else "فاتح", "mint" to if (language == "en") "Mint" else "نعناعي").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = background == v, onClick = { background = v; saved = false })
                    Text(label, color = SettingsNavy, fontSize = 13.sp)
                }
            }
            Text(if (language == "en") "Glass opacity: ${(glassOpacity * 100).toInt()}%" else "شفافية الزجاج: ${(glassOpacity * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassOpacity, onValueChange = { glassOpacity = it; saved = false }, valueRange = .25f.. .96f)
            Text(if (language == "en") "Background blur: ${glassBlur.toInt()} dp" else "تمويه الخلفية: ${glassBlur.toInt()} dp", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassBlur, onValueChange = { glassBlur = it; saved = false }, valueRange = 0f..28f)
            Text(if (language == "en") "Glass glow: ${(glassGlow * 100).toInt()}%" else "لمعان الزجاج: ${(glassGlow * 100).toInt()}%", color = SettingsNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Slider(value = glassGlow, onValueChange = { glassGlow = it; saved = false }, valueRange = 0f..1f)
        } }
        item { SettingsGroup("الخلفيات", Icons.Rounded.Wallpaper, language) {
            SettingsAction("اختيار خلفية من الجهاز", "استخدم صورة ثابتة خلف محتوى التطبيق", Icons.Rounded.Image, language) { wallpaperPicker.launch(arrayOf("image/*")) }
            SettingsAction("إزالة الخلفية المخصصة", "الرجوع إلى لون الخلفية", Icons.Rounded.DeleteOutline, language) {
                wallpaperUri = null
                prefs.edit().remove("settings_wallpaper_uri").apply()
                saved = false
                onSaved()
            }
            SettingsAction("تعيين خلفية متحركة", "افتح معاينة خلفية RUVEN المتحركة للنظام", Icons.Rounded.MotionPhotosOn, language) {
                openIntent(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, RuvenLiveWallpaperService::class.java)
                ))
            }
            if (!wallpaperUri.isNullOrBlank()) Text(st("تم اختيار خلفية مخصصة.", language), color = Color(0xFF16845B), fontSize = 11.sp)
        } }
        item { SettingsGroup("الخصوصية والمعلومات", Icons.Rounded.Policy, language) {
            SettingsAction("سياسة الخصوصية", "البيانات واستخدامها وحمايتها", Icons.Rounded.PrivacyTip, language) { dialog = "privacy" }
            SettingsAction("الشروط والأحكام", "قواعد استخدام منظومة المدرسة", Icons.Rounded.Description, language) { dialog = "terms" }
            SettingsAction("من نحن", "تعريف بمنظومة RUVEN", Icons.Rounded.Info, language) { dialog = "about" }
        } }
        item { SettingsGroup("الدعم والمشاركة", Icons.Rounded.SupportAgent, language) {
            SettingsAction("اتصل بنا / الدعم", "افتح صفحة الإبلاغ عن مشكلة", Icons.Rounded.Email, language) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mostafa0782/RUVEN/issues")))
            }
            SettingsAction("مشاركة RUVEN", "شارك رابط المشروع", Icons.Rounded.Share, language) {
                openIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "RUVEN - المنظومة المدرسية")
                    putExtra(Intent.EXTRA_TEXT, "RUVEN — منظومة مدرسية\nhttps://github.com/mostafa0782/RUVEN")
                }, "مشاركة RUVEN"))
            }
            SettingsAction("قيّم التطبيق / أرسل ملاحظاتك", "أرسل اقتراحاً أو بلّغ عن مشكلة", Icons.Rounded.StarRate, language) {
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
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text(st("حفظ جميع الإعدادات", language))
            }
            if (saved) Text(st("تم حفظ التفضيلات على هذا الجهاز.", language), color = Color(0xFF16845B), fontSize = 12.sp)
        }
    }
    if (dialog == "signout") AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(st("تسجيل الخروج؟", language)) },
        text = { Text(st("سيتم إنهاء الجلسة على هذا الجهاز. يمكنك تسجيل الدخول مرة أخرى لاحقاً.", language)) },
        confirmButton = { TextButton(onClick = { dialog = null; onSignOut() }) { Text(st("تسجيل الخروج", language)) } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } }
    )
    if (dialog == "password") AlertDialog(
        onDismissRequest = { if (!passwordBusy) dialog = null },
        title = { Text(st("تغيير كلمة المرور", language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(newPassword, { newPassword = it; passwordMessage = null }, label = { Text(st("كلمة المرور الجديدة", language)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(confirmPassword, { confirmPassword = it; passwordMessage = null }, label = { Text(st("تأكيد كلمة المرور", language)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                passwordMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(enabled = !passwordBusy, onClick = {
            when {
                newPassword.length < 8 -> passwordMessage = st("استخدم 8 محارف على الأقل.", language)
                newPassword.length > 72 -> passwordMessage = st("كلمة المرور طويلة أكثر من اللازم.", language)
                newPassword != confirmPassword -> passwordMessage = st("كلمتا المرور غير متطابقتين.", language)
                else -> scope.launch {
                    passwordBusy = true
                    try {
                        backend.updatePassword(session, newPassword)
                        passwordMessage = st("تم تغيير كلمة المرور بنجاح.", language)
                        newPassword = ""; confirmPassword = ""
                    } catch (e: Exception) { passwordMessage = e.message ?: st("تعذر تغيير كلمة المرور.", language) }
                    finally { passwordBusy = false }
                }
            }
        }) { if (passwordBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(st("تغيير", language)) } },
        dismissButton = { TextButton(onClick = { if (!passwordBusy) dialog = null }) { Text(st("إغلاق", language)) } }
    )
    val info = when (dialog) {
        "privacy" -> if (language == "en") """
            DRAFT — pending formal approval by school administration.

            1. Data handled: school account identifiers, name, role, class/section, school posts and attachments, notification status, and settings needed to operate the app.
            2. Purposes: authenticate approved users, show school-approved schedules, homework, preparations and announcements, deliver school workflow notifications, and protect account security.
            3. Storage and processors: account and school content are handled by the school's configured Supabase project. A private storage bucket is used for school attachments. Network requests use HTTPS.
            4. Access and sharing: access is limited by account role and database/storage policies. Data is not intended for advertising or sale. Information may be disclosed only when required by law or authorized school procedures.
            5. Retention and deletion: the school administration must define retention periods, backup handling, and the process for correction/deletion requests before public deployment.
            6. Security: use a unique password, sign out on shared devices, and report suspected unauthorized access. No online system can guarantee absolute security.
            7. Minors: because students may be under 18, the school must confirm appropriate guardian notices, approvals and handling procedures.
            8. Your choices: request account/profile corrections or data-related help through school administration. Technical issues can be reported at github.com/mostafa0782/RUVEN/issues.
            9. Changes: the school should publish a dated revision when this policy changes.

            This is an implementation draft, not a school-approved legal notice. The school must fill in its official contact details and approve the retention, deletion and guardian procedures.
        """.trimIndent() else """
            مسودة — بانتظار الاعتماد الرسمي من إدارة المدرسة.

            ١. البيانات: معرّف الحساب المدرسي والاسم والدور والشعبة، والمنشورات والمرفقات المدرسية، وحالة الإشعارات والإعدادات اللازمة لتشغيل التطبيق.
            ٢. الأغراض: توثيق دخول الحسابات المعتمدة، وعرض الجداول والواجبات والتحاضير والإعلانات المعتمدة، وإرسال إشعارات سير العمل المدرسي، وحماية الحسابات.
            ٣. التخزين والمعالجة: تُعالج بيانات الحساب والمحتوى عبر مشروع Supabase الذي تضبطه المدرسة. تُحفظ المرفقات في مساحة تخزين خاصة، وتستخدم الاتصالات HTTPS.
            ٤. الوصول والمشاركة: تُقيّد الصلاحيات بحسب الدور وسياسات قاعدة البيانات والتخزين. لا يُقصد استخدام البيانات للإعلانات أو بيعها. ولا تُكشف إلا عند إلزام قانوني أو إجراء مدرسي مخوّل.
            ٥. الاحتفاظ والحذف: على الإدارة تحديد مدد الاحتفاظ والنسخ الاحتياطية وآلية طلب تصحيح البيانات أو حذفها قبل الإطلاق العام.
            ٦. الأمان: استخدم كلمة مرور فريدة، وسجّل الخروج من الأجهزة المشتركة، وأبلغ عن أي دخول مشبوه. لا يمكن لأي نظام متصل ضمان أمان مطلق.
            ٧. القاصرون: لأن بعض الطلبة دون ١٨ عاماً، يجب على المدرسة اعتماد إشعارات أولياء الأمور والموافقات والإجراءات المناسبة.
            ٨. حقوق المستخدم: يمكن طلب تصحيح بيانات الحساب أو المساعدة بشأنها من إدارة المدرسة. ويمكن الإبلاغ عن المشكلات التقنية عبر github.com/mostafa0782/RUVEN/issues.
            ٩. التغييرات: ينبغي نشر نسخة مؤرخة عند تعديل هذه السياسة.

            هذه مسودة تنفيذية وليست سياسة قانونية معتمدة من المدرسة. يجب إضافة بيانات الاتصال الرسمية واعتماد مدد الاحتفاظ والحذف وإجراءات أولياء الأمور.
        """.trimIndent()
        "terms" -> if (language == "en") """
            DRAFT — pending formal approval by school administration.

            1. Eligibility: access is for school accounts created or approved by authorized school staff. Do not create or share unauthorized accounts.
            2. Account security: keep passwords private, use the sign-out option on shared devices, and promptly report suspected compromise. You are responsible for activity performed through your account, subject to applicable law and school policy.
            3. Acceptable use: use RUVEN for legitimate school purposes. Do not impersonate others, bypass role restrictions, probe the service without authorization, upload malware, or publish unlawful, abusive, confidential or unrelated material.
            4. Student information: do not upload or share personal information, grades, images or documents about students unless you have explicit school authorization.
            5. Official content: schedules, announcements, homework, preparations, decisions and leave information are authoritative only when published or approved through the school's designated process. Confirm urgent matters with the school.
            6. Availability and updates: features may be changed, interrupted or unavailable during maintenance. Keep the app updated through verified RUVEN releases.
            7. Enforcement: the school may restrict or suspend accounts according to its policies and applicable procedures.
            8. Reporting: report technical problems at github.com/mostafa0782/RUVEN/issues and school/disciplinary matters to school administration.
            9. Changes: revised terms should be dated and communicated by the school.

            This is a draft for review, not approved school policy or legal advice. The school must approve it, add its official contact details, and define appeals and account suspension procedures before deployment.
        """.trimIndent() else """
            مسودة — بانتظار الاعتماد الرسمي من إدارة المدرسة.

            ١. الأهلية: الدخول مخصص للحسابات التي تنشئها أو تعتمدها كوادر المدرسة المخوّلة. يُمنع إنشاء حسابات غير مصرح بها أو مشاركة الحسابات.
            ٢. أمان الحساب: حافظ على سرية كلمة المرور، واستخدم تسجيل الخروج على الأجهزة المشتركة، وأبلغ سريعاً عن أي اشتباه باختراق. تُراعى القوانين وسياسات المدرسة في تحديد مسؤولية نشاط الحساب.
            ٣. الاستخدام المقبول: استخدم RUVEN للأغراض المدرسية المشروعة. يُمنع انتحال الشخصيات أو تجاوز الصلاحيات أو اختبار الخدمة دون إذن أو رفع برمجيات ضارة أو نشر محتوى مخالف أو مسيء أو سري أو غير متعلق بالمدرسة.
            ٤. بيانات الطلبة: لا ترفع أو تشارك معلومات شخصية أو درجات أو صوراً أو وثائق تخص الطلبة دون تصريح واضح من المدرسة.
            ٥. المحتوى الرسمي: لا تُعد الجداول والإعلانات والواجبات والتحاضير والقرارات والإجازات معتمدة إلا إذا نُشرت أو اعتُمدت عبر الإجراء المدرسي المحدد. أكّد الأمور العاجلة مع المدرسة.
            ٦. التوفر والتحديثات: قد تتغير بعض الوظائف أو تتوقف أثناء الصيانة. ثبّت التحديثات من إصدارات RUVEN الموثوقة.
            ٧. تطبيق القواعد: يجوز للمدرسة تقييد الحساب أو تعليقه وفق سياساتها وإجراءاتها المعتمدة.
            ٨. الإبلاغ: أبلغ عن المشكلات التقنية عبر github.com/mostafa0782/RUVEN/issues، ووجّه الشؤون المدرسية أو الانضباطية إلى الإدارة.
            ٩. التعديلات: ينبغي تأريخ الشروط المعدلة وإبلاغ المستخدمين بها من قبل المدرسة.

            هذه مسودة للمراجعة وليست شروطاً مدرسية معتمدة أو استشارة قانونية. يجب اعتمادها وإضافة بيانات الاتصال الرسمية وتحديد إجراءات الاعتراض وتعليق الحساب قبل النشر.
        """.trimIndent()
        "about" -> if (language == "en") "RUVEN is a school digital system designed to bring approved announcements, schedules, homework, lesson preparations, notifications and authorized account workflows into one application. It is maintained as a school-focused project and its policies must be approved by the school administration." else "RUVEN منظومة رقمية مدرسية تهدف إلى جمع الإعلانات والجداول والواجبات والتحاضير والإشعارات وإدارة الحسابات المصرح بها في تطبيق واحد. تُدار الوظائف المدرسية بحسب الصلاحيات، وتحتاج السياسات الرسمية إلى اعتماد إدارة المدرسة."
        else -> null
    }
    if (info != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(st(when (dialog) { "privacy" -> "سياسة الخصوصية"; "terms" -> "الشروط والأحكام"; else -> "من نحن" }, language)) },
        text = { Text(info, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { dialog = null }) { Text(st("إغلاق", language)) } }
    )
}

@Composable
private fun SettingsGroup(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, language: String = "ar", content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = SettingsBlue) }
                Text(st(title, language), color = SettingsNavy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, language: String = "ar", onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color(0xFFF6F8FC), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = SettingsBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(st(title, language), color = SettingsNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(st(subtitle, language), color = SettingsMuted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronLeft, null, tint = SettingsMuted)
        }
    }
}
