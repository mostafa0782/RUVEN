package iq.ruven.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import iq.ruven.app.data.monitor.BackendHealthWorker
import java.util.concurrent.TimeUnit
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Announcement
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iq.ruven.app.data.model.SchoolPermissions
import iq.ruven.app.data.model.SchoolRole
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Ink = Color(0xFF10253F)
private val Blue = Color(0xFF2867D8)
private val Cyan = Color(0xFF56C7E8)
private val Canvas = Color(0xFFF4F7FC)
private val Muted = Color(0xFF718096)

private fun readLocalItems(prefs: SharedPreferences, key: String): List<String> = runCatching {
    val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() && it != "null" }
    }
}.getOrDefault(emptyList())

private fun writeLocalItems(prefs: SharedPreferences, key: String, values: List<String>) {
    val array = JSONArray()
    values.forEach(array::put)
    prefs.edit().putString(key, array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    private fun scheduleBackendHealthChecks() {
        // Health monitoring is enabled only after project URL and publishable key are configured.
        // It checks backend availability; it is not a guarantee against provider auto-pausing.
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return
        val request = PeriodicWorkRequestBuilder<BackendHealthWorker>(2, TimeUnit.HOURS)
            .setConstraints(androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build())
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            BackendHealthWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleBackendHealthChecks()
        RuvenNotifications.createChannel(this)
        setContent { RuvenTheme { RuvenSplashGate { RuvenApp() } } }
    }
}

@Composable
private fun RuvenSplashGate(content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }
    var lineExpanded by remember { mutableStateOf(false) }
    val lineProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (lineExpanded) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 950,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "welcomeGoldLine"
    )
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        lineExpanded = true
        kotlinx.coroutines.delay(1950)
        showSplash = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(280)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(520))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF070D18),
                                Color(0xFF101D31),
                                Color(0xFF050912)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 1000, delayMillis = 220
                            )
                        ) + slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 850,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            ),
                            initialOffsetY = { it / 3 }
                        )
                    ) {
                        Text(
                            text = "WELCOME",
                            color = Color(0xFFE8C675),
                            fontSize = 31.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 7.sp,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9B7028),
                                        Color(0xFFFFE8A3),
                                        Color(0xFFD5A84F),
                                        Color(0xFFFFF1C4)
                                    )
                                )
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f * lineProgress)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF6D4A17),
                                        Color(0xFFFFE9A0),
                                        Color(0xFFB8872E)
                                    )
                                ),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RuvenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = Blue, secondary = Cyan, background = Canvas, surface = Color.White,
        onPrimary = Color.White, onBackground = Ink, onSurface = Ink
    ), content = content)
}

private enum class RuvenTab(val title: String, val englishTitle: String) {
    Home("الرئيسية", "Home"), Schedule("الجدول", "Timetable"), Homework("الواجبات", "Homework"),
    Preparations("التحاضير", "Preparations"), Notices("الإعلانات", "Announcements"), Workflows("العمليات المدرسية", "School workflows"),
    Admin("الحسابات", "Accounts"), Sections("الشعب", "Sections"), Roster("سجل الطلاب", "Student roster"), Notifications("الإشعارات", "Notifications"), Settings("الإعدادات", "Settings")
}

private val ruvenEnglishLabels = mapOf(
    "الله أكبر" to "God is greatest",
    "الرئيسية" to "Home",
    "الجدول" to "Timetable",
    "الواجبات" to "Homework",
    "التحاضير" to "Preparations",
    "الإعلانات" to "Announcements",
    "العمليات المدرسية" to "School workflows",
    "الحسابات" to "Accounts",
    "الشعب" to "Sections",
    "الإشعارات" to "Notifications",
    "الإعدادات" to "Settings",
    "تعذر تحميل محتوى المدرسة." to "Could not load school content.",
    "جارٍ استعادة جلسة الدخول…" to "Restoring sign-in session…",
    "جارٍ فحص الإصدارات الرسمية…" to "Checking official releases…",
    "افتح التحديث من التطبيق مباشرة." to "Open the update directly from the app.",
    "تم تنزيل التحديث. أكمل التثبيت من نافذة أندرويد." to "Update downloaded. Finish installation in Android.",
    "تعذر فحص التحديثات." to "Could not check for updates.",
    "تعذر تحديث محتوى المدرسة." to "Could not refresh school content.",
    "تحديث RUVEN" to "Update RUVEN",
    "حسناً" to "OK",
    "خلفية RUVEN" to "RUVEN wallpaper",
    "منظومتك المدرسية" to "Your school system",
    "تحديث المحتوى والإشعارات" to "Refresh content and notifications",
    "جارٍ الفحص…" to "Checking…",
    "تحقق من التحديثات" to "Check for updates",
    "خروج" to "Sign out",
    "الجدول الدراسي" to "Timetable",
    "الجداول المنشورة من إدارة المدرسة." to "Timetables published by school administration.",
    "الواجبات المنشورة حسب المادة والشعبة." to "Homework published by subject and class.",
    "تحاضير الدروس المنشورة من المدرسين والإدارة." to "Lesson preparations published by teachers and administration.",
    "الإعلانات المدرسية" to "School announcements",
    "الإعلانات المنشورة من إدارة المدرسة." to "Announcements published by school administration.",
    "تعذر تحميل الإشعارات." to "Could not load notifications.",
    "إشعارات فعلية من قاعدة البيانات: قرارات الإدارة وحالة الإجازات." to "Database notifications: school decisions and leave status.",
    "تحديث" to "Refresh",
    "ما عندك إشعارات حاليًا." to "You have no notifications right now.",
    "جديد" to "New",
    "تعذر تحديث الإشعار." to "Could not update notification.",
    "تحديد كمقروء" to "Mark as read",
    "الإجازات" to "Leave requests",
    "الدليل" to "Directory",
    "المالك" to "Owner",
    "الإدارة المدرسية" to "School administration",
    "القرارات" to "Decisions",
    "التقرير اليومي" to "Daily report",
    "الأختام" to "Seals",
    "تعذر تحميل بيانات العمليات المدرسية." to "Could not load school workflow data.",
    "تعذر تنفيذ العملية." to "Could not complete the action.",
    "الدليل، القرارات المختومة، طلبات الإجازة، والتقرير اليومي المتصل بالخادم." to "Directory, sealed decisions, leave requests, and server-backed daily reports.",
    "قائمة الأسماء متاحة للمراقبين والإدارة. الطلاب يقدمون الإجازات ويتابعون التقارير من الأقسام الأخرى." to "The directory is available to monitors and administrators. Students can submit leave requests and view reports in other sections.",
    "عرض المدرسين والهيئة التربوية فقط" to "Show teachers and educational staff only",
    "قرار" to "Decision",
    "إدارة الأختام متاحة للمالك وأعضاء الإدارة المخوّلين فقط." to "Seal management is limited to the owner and authorized administrators.",
    "اسم الختم" to "Seal name",
    "صفة صاحب الختم" to "Seal holder title",
    "إنشاء قرار إداري" to "Create an administrative decision",
    "الطالب المستهدف" to "Target student",
    "بدون شعبة" to "No section",
    "نوع القرار" to "Decision type",
    "نقل" to "Transfer",
    "حذف" to "Remove",
    "قبول" to "Accept",
    "آخر" to "Other",
    "الشعبة الحالية" to "Current section",
    "الشعبة الجديدة (إن وجدت)" to "New section (if any)",
    "السبب والتعليمات" to "Reason and instructions",
    "ختم القرار" to "Decision seal",
    "لا يوجد ختم باسم حسابك. أنشئ ختمًا من تبويب الأختام أولًا." to "No seal is assigned to your account. Create a seal in the Seals tab first.",
    "تعيين مراقب للتنفيذ" to "Assign a monitor to execute",
    "ماكو قرارات ظاهرة لهذا الحساب." to "No decisions are available for this account.",
    "ملاحظة التنفيذ (اختيارية)" to "Execution note (optional)",
    "تم تأكيد استلام القرار." to "Decision receipt acknowledged.",
    "استلام" to "Acknowledge",
    "تم التنفيذ" to "Completed",
    "تم تسجيل تنفيذ القرار." to "Decision execution recorded.",
    "طلب إجازة" to "Leave request",
    "سبب الإجازة" to "Leave reason",
    "طلبات بانتظار قرار الإدارة" to "Requests awaiting an administrative decision",
    "ماكو طلبات إجازة ظاهرة حاليًا." to "No leave requests are currently available.",
    "أنشئ ختمًا باسم حسابك أولًا حتى توافق على الطلب." to "Create a seal for your account before approving a request.",
    "تمت الموافقة على الإجازة وأُرسل إشعار للطالب." to "Leave approved and the student was notified.",
    "موافقة" to "Approve",
    "تم رفض الإجازة وإشعار الطالب." to "Leave rejected and the student was notified.",
    "رفض" to "Reject",
    "تم تسجيل الحضور." to "Attendance recorded.",
    "حاضر" to "Present",
    "تم تسجيل الغياب." to "Absence recorded.",
    "غائب" to "Absent",
    "أهم أحداث اليوم" to "Key events today",
    "وعود الإدارة أو الأمور المقررة" to "Administrative commitments or planned items",
    "أمور لم تُنفّذ بعد" to "Items not yet completed",
    "ماكو تقارير يومية منشورة حاليًا." to "No daily reports have been published.",
    "أحداث اليوم" to "Today's events",
    "وعود الإدارة" to "Administrative commitments",
    "لم يُنفّذ بعد" to "Not yet completed",
    "تحديث بيانات الخادم" to "Refresh server data",
    "آخر تحديثات المدرسة المنشورة. اسحب التحديث من الخادم بزر التحديث." to "Latest published school updates. Refresh from the server using the update button.",
    "تحديث الإشعارات" to "Refresh notifications",
    "ماكو إشعارات منشورة حاليًا" to "No notifications have been published.",
    "تظهر هنا تحديثات المدرسة المتاحة لحسابك." to "School updates available to your account appear here.",
    "كحلي زجاجي" to "Glass navy",
    "ليل داكن" to "Midnight",
    "فاتح" to "Light",
    "نعناعي" to "Mint",
    "خصص تجربة RUVEN على هذا الجهاز." to "Customize your RUVEN experience on this device.",
    "اللغة / Language" to "Language",
    "العربية" to "Arabic",
    "Mixed • عناوين إنجليزية وشرح عربي" to "Mixed • English headings and Arabic details",
    "المظهر" to "Appearance",
    "داكن" to "Dark",
    "زجاجي" to "Glass",
    "الخلفية" to "Background",
    "حفظ الإعدادات" to "Save settings",
    "انحفظت تفضيلاتك على هذا الجهاز." to "Preferences saved on this device.",
    "طالب" to "Student",
    "مراقب الشعبة" to "Class monitor",
    "عضو برلمان الطلبة" to "Student parliament member",
    "أستاذ" to "Teacher",
    "أستاذ مشرف شعبة" to "Class supervisor",
    "معاون المدير" to "Assistant principal",
    "المدير" to "Principal",
    "مرشد تربوي" to "Educational counselor",
    "عضو تربوي من وزارة التربية" to "Ministry of Education member",
    "مراقب شعبة" to "Class monitor",
    "معاون" to "Assistant principal",
    "مدير" to "Principal",
    "الثالث متوسط" to "Third intermediate",
    "تعذر تحميل الشعب." to "Could not load sections.",
    "إدارة الشعب" to "Section management",
    "إدارة مرئية للشعب المفتوحة والمغلقة، مع منع إنشاء الشعب غير المطلوبة." to "Manage open and closed sections; avoid creating unneeded sections.",
    "إجمالي الشعب" to "Total sections",
    "مفتوحة" to "Open",
    "مغلقة" to "Closed",
    "إضافة شعبة" to "Add section",
    "أدخل الصف ورمز الشعبة المطلوبة" to "Enter the grade and section code",
    "افتح النموذج عند الحاجة فقط" to "Open the form only when needed",
    "إغلاق ▲" to "Close ▲",
    "إضافة +" to "Add +",
    "الصف الدراسي" to "Grade",
    "فتح الشعبة مباشرة للطلاب والموظفين" to "Make the section visible to students and staff immediately",
    "تعذر إنشاء الشعبة." to "Could not create the section.",
    "جارٍ الإنشاء..." to "Creating…",
    "إنشاء الشعبة" to "Create section",
    "تعذر تنفيذ العملية" to "Could not complete the action",
    "بحث بالصف أو رمز الشعبة" to "Search by grade or section",
    "تحميل..." to "Loading…",
    "ماكو شعب مسجلة" to "No sections registered",
    "أنشئ الشعبة المطلوبة فقط؛ الشعب الفارغة لا تنشأ وحدها." to "Create only the sections you need; empty sections are not created automatically.",
    "ما لقينا شعبة مطابقة" to "No matching section found",
    "ابحث باسم الصف أو رمز الشعبة A–F." to "Search by grade or section code A–F.",
    "مفتوحة للمستخدمين" to "Visible to users",
    "مغلقة ومخفية" to "Closed and hidden",
    "تعذر تغيير حالة الشعبة." to "Could not change section status.",
    "تعذر تحميل الحسابات." to "Could not load accounts.",
    "إدارة الحسابات" to "Account management",
    "لوحة موحّدة لإدارة المستخدمين والأدوار والشعب." to "Manage users, roles and sections in one place.",
    "كل الحسابات" to "All accounts",
    "طلاب" to "Students",
    "موظفون" to "Staff",
    "بحث بالاسم أو البريد أو الدور" to "Search by name, email or role",
    "إنشاء حساب جديد" to "Create account",
    "أدخل بيانات الحساب الجديد" to "Enter the new account details",
    "إنشاء حساب طالب أو موظف بصلاحيات محددة" to "Create a student or staff account with defined permissions",
    "ينشئ حساب دخول حقيقياً. لا يمكن إنشاء مالك آخر، وكلمة المرور لا تُعرض ضمن قائمة الحسابات." to "Creates a real sign-in account. A second owner cannot be created, and passwords are never shown in account lists.",
    "اسم الطالب أو الموظف" to "Student or staff name",
    "البريد الإلكتروني" to "Email address",
    "كلمة مرور مؤقتة (8 أحرف على الأقل)" to "Temporary password (at least 8 characters)",
    "الشعبة (A–F)، إلزامية للطالب" to "Section (A–F), required for students",
    "اكتب A أو B أو C أو D أو E أو F؛ يمكن تركها فارغة للموظفين." to "Enter A, B, C, D, E or F; staff may leave it blank.",
    "تم إنشاء الحساب. احفظ البريد وكلمة المرور المؤقتة وأرسلها لصاحب الحساب بأمان." to "Account created. Save the email and temporary password and share them securely with the account holder.",
    "تعذر إنشاء الحساب." to "Could not create account.",
    "جارٍ إنشاء الحساب..." to "Creating account…",
    "إنشاء الحساب" to "Create account",
    "جارٍ التحديث..." to "Refreshing…",
    "تحديث قائمة الحسابات" to "Refresh account list",
    "تعذر تحميل الحسابات" to "Could not load accounts",
    "ماكو حسابات إضافية" to "No additional accounts",
    "تظهر الحسابات هنا بعد تسجيلها في Supabase Auth." to "Accounts appear here after registration in Supabase Auth.",
    "ما لقينا حساب مطابق" to "No matching account found",
    "جرّب الاسم أو البريد أو الدور أو الشعبة." to "Try the name, email, role or section.",
    "الدور الحالي: " to "Current role: ",
    "حساب المالك محمي ولا يمكن تغيير دوره من هذه اللوحة." to "The owner account is protected and its role cannot be changed here.",
    "الدور الجديد: " to "New role: ",
    "الصف والشعبة (مثال: الثالث متوسط - A)" to "Grade and section (example: Third intermediate - A)",
    "تم تحديث الحساب بنجاح." to "Account updated successfully.",
    "تعذر حفظ صلاحيات الحساب." to "Could not save account permissions.",
    "جارٍ الحفظ..." to "Saving…",
    "حفظ الدور والشعبة" to "Save role and section",
    "هلا بيك 👋" to "Welcome 👋",
    "خلّ يومك الدراسي أوضح وأسهل." to "Make your school day clearer and easier.",
    "RUVEN • مدرستك بذكاء" to "RUVEN • Your school, smarter",
    "كل شي يخص دراستك،\\nبمكان واحد." to "Everything about your studies,\\nin one place.",
    "تابع الواجبات، افتح الجدول، وابقَ على اطلاع بآخر إعلانات المدرسة." to "Track homework, check the timetable, and stay up to date with school announcements.",
    "شوف واجباتك" to "View homework",
    "اختصارات سريعة" to "Quick shortcuts",
    "اختَر شنو تحتاج هسه" to "Choose what you need",
    "حصصك ومواعيدك" to "Classes and times",
    "لا يفوتك شي" to "Stay on top of tasks",
    "محتوى الدروس" to "Lesson content",
    "آخر المستجدات" to "Latest updates",
    "ملخصك الدراسي" to "Your study summary",
    "عدادات العناصر المحفوظة محلياً" to "Locally saved item counts",
    "عرض أقل" to "Show less",
    "تفاصيل" to "Details",
    "جداول" to "timetables",
    "واجبات" to "tasks",
    "تحاضير" to "preparations",
    "تفاصيل المساحة" to "Workspace details",
    "منشورات الخادم: " to "Server posts: ",
    "الواجبات المحلية: " to "Local homework: ",
    "التحاضير المحلية: " to "Local preparations: ",
    "الجداول المحلية: " to "Local timetables: ",
    "آخر منشورات المدرسة" to "Latest school posts",
    "واجبات وتحاضير وإعلانات منشورة من الخادم" to "Homework, preparations and announcements published by the server",
    "الكل" to "All",
    "الجداول" to "Timetables",
    "ابحث باسم الواجب أو المادة…" to "Search homework or subject…",
    "مسح البحث" to "Clear search",
    "تعذر تحميل المحتوى" to "Could not load content",
    "ما لقينا نتيجة" to "No results found",
    "ماكو منشورات بعد" to "No posts yet",
    "جرّب كلمة ثانية أو امسح البحث." to "Try another word or clear the search.",
    "من تنشر المدرسة واجباً أو إعلاناً، راح يظهر هنا." to "School homework and announcements will appear here when published.",
    "مساحتك وصلاحياتك" to "Your space and permissions",
    "تقدر تتابع الجدول والواجبات المنشورة لشعبتك." to "You can follow the timetable and homework published for your section.",
    "تابع شؤون الشعبة وبلّغ الإدارة عند الحاجة." to "Follow section matters and contact administration when needed.",
    "مثّل الطلبة وارفع المقترحات ضمن صلاحياتك." to "Represent students and submit suggestions within your permissions.",
    "تابع التحاضير والواجبات ضمن موادك المسندة." to "Follow preparations and homework for your assigned subjects.",
    "تابع الشعبة المكلّف بها." to "Follow your assigned section.",
    "تابع المحتوى المدرسي حسب الصلاحيات." to "Follow school content according to your permissions.",
    "إشراف إداري على شؤون المدرسة." to "Administrative oversight of school matters.",
    "متابعة الشؤون التربوية مع مراعاة الخصوصية." to "Support educational matters while respecting privacy.",
    "دور رقابي تربوي ضمن الصلاحيات المعتمدة." to "Educational oversight within approved permissions.",
    "إدارة النظام والحسابات والإعدادات." to "Manage the system, accounts and settings.",
    "RUVEN • نسخة قيد التطوير" to "RUVEN • Development build",
    "تحديث من خادم المدرسة" to "Refresh from school server",
    "إضافة محتوى رسمي" to "Add official content",
    "سيُرسل إلى خادم المدرسة، وتطبّق صلاحيات قاعدة البيانات." to "Sent to the school server; database permissions apply.",
    "إغلاق" to "Close",
    "إنشاء" to "Create",
    "المادة" to "Subject",
    "الشعبة / الصف" to "Section / grade",
    "العنوان" to "Title",
    "التفاصيل" to "Details",
    "إرفاق ملف من الجهاز (اختياري)" to "Attach a file from device (optional)",
    "نشر على خادم المدرسة" to "Publish to school server",
    "جارٍ الإرسال…" to "Uploading…",
    "تم إرسال المحتوى إلى الخادم." to "Content sent to the server.",
    "تعذر نشر المحتوى. تحقّق من الصلاحيات والاتصال." to "Could not publish content. Check permissions and connection.",
    "بحث" to "Search",
    "ابحث بالعنوان أو المادة أو الشعبة" to "Search by title, subject or section",
    "ماكو محتوى منشور بهذا القسم حالياً." to "No content has been published in this section yet.",
    "ماكو نتائج مطابقة للبحث." to "No matching search results.",
    "اسحب تحديث المحتوى من الخادم للتأكد من آخر البيانات." to "Refresh from the server to check for the latest data.",
    "فتح / تنزيل الملف المرفق" to "Open / download attachment",
    "ختم: " to "Seal: ",
    "معرّف: " to "ID: ",
    "إصدار القرار وإشعار المراقب" to "Issue decision and notify monitor",
    "من تاريخ YYYY-MM-DD" to "From date YYYY-MM-DD",
    "إلى تاريخ YYYY-MM-DD" to "To date YYYY-MM-DD",
    "إرسال طلب الإجازة" to "Submit leave request",
    "السبب: " to "Reason: ",
    "الحالة: " to "Status: ",
    " • الحضور: " to " • Attendance: ",
    "تمت الموافقة بختم: " to "Approved with seal: ",
    "تاريخ التقرير YYYY-MM-DD" to "Report date YYYY-MM-DD",
    "نشر التقرير للطلاب" to "Publish report to students",
    "قائمة الأسماء متاحة للمراقبين والإدارة. الطلاب يقدمون الإجازات ويتابعون التقارير من الأقسام الأخرى." to "The directory is available to monitors and administrators. Students can submit leave requests and view reports in other sections.",
    "العمليات المدرسية" to "School workflows",
    "الدليل، القرارات المختومة، طلبات الإجازة، والتقرير اليومي المتصل بالخادم." to "Directory, sealed decisions, leave requests, and server-backed daily reports.",
    "عرض المدرسين والهيئة التربوية فقط" to "Show teachers and educational staff only",
    "إدارة الأختام متاحة للمالك وأعضاء الإدارة المخوّلين فقط." to "Seal management is restricted to the owner and authorized administrators.",
    "إنشاء ختم" to "Create seal",
    "جارٍ الحفظ…" to "Saving…",
    "الطالب المستهدف" to "Target student",
    "نوع القرار" to "Decision type",
    "نقل" to "Transfer",
    "حذف" to "Remove",
    "قبول" to "Accept",
    "آخر" to "Other",
    "الشعبة الحالية" to "Current section",
    "الشعبة الجديدة (إن وجدت)" to "New section (if applicable)",
    "السبب والتعليمات" to "Reason and instructions",
    "ختم القرار" to "Decision seal",
    "لا يوجد ختم باسم حسابك. أنشئ ختمًا من تبويب الأختام أولًا." to "No seal is assigned to your account. Create a seal in the Seals tab first.",
    "تعيين مراقب للتنفيذ" to "Assign a monitor to execute",
    "ماكو قرارات ظاهرة لهذا الحساب." to "No decisions are available for this account.",
    "ملاحظة التنفيذ (اختيارية)" to "Execution note (optional)",
    "استلام" to "Acknowledge",
    "تم التنفيذ" to "Completed",
    "تم تأكيد استلام القرار." to "Decision receipt acknowledged.",
    "تم تسجيل تنفيذ القرار." to "Decision execution recorded.",
    "سبب الإجازة" to "Leave reason",
    "طلبات بانتظار قرار الإدارة" to "Requests awaiting an administrative decision",
    "ماكو طلبات إجازة ظاهرة حاليًا." to "No leave requests are currently available.",
    "أنشئ ختمًا باسم حسابك أولًا حتى توافق على الطلب." to "Create a seal for your account before approving a request.",
    "تمت الموافقة على الإجازة وأُرسل إشعار للطالب." to "Leave approved and the student was notified.",
    "موافقة" to "Approve",
    "تم رفض الإجازة وإشعار الطالب." to "Leave rejected and the student was notified.",
    "حاضر" to "Present",
    "غائب" to "Absent",
    "تم تسجيل الحضور." to "Attendance recorded.",
    "تم تسجيل الغياب." to "Absence recorded.",
    "أهم أحداث اليوم" to "Key events today",
    "وعود الإدارة أو الأمور المقررة" to "Administrative commitments or planned items",
    "أمور لم تُنفّذ بعد" to "Items not yet completed",
    "ماكو تقارير يومية منشورة حاليًا." to "No daily reports have been published.",
    "أحداث اليوم" to "Today's events",
    "وعود الإدارة" to "Administrative commitments",
    "لم يُنفّذ بعد" to "Not completed yet",
    "تحديث بيانات الخادم" to "Refresh server data",
    "الإشعارات" to "Notifications",
    "آخر تحديثات المدرسة المنشورة. اسحب التحديث من الخادم بزر التحديث." to "Latest published school updates. Refresh from the server using the button.",
    "إشعارات فعلية من قاعدة البيانات: قرارات الإدارة وحالة الإجازات." to "Database notifications: administrative decisions and leave status.",
    "كل شي يخص دراستك،\nبمكان واحد." to "Everything about your studies,\nin one place.",
    "منشورات الخادم: " to "Server posts: ",
    "الواجبات المحلية: " to "Local homework: ",
    "التحاضير المحلية: " to "Local preparations: ",
    "الجداول المحلية: " to "Local timetables: ",
    "الجدول الدراسي" to "Timetable",
    "الجداول المنشورة من إدارة المدرسة." to "Timetables published by school administration.",
    "الواجبات المنشورة حسب المادة والشعبة." to "Homework published by subject and class.",
    "تحاضير الدروس المنشورة من المدرسين والإدارة." to "Lesson preparations published by teachers and administration.",
    "الإعلانات المدرسية" to "School announcements",
    "الإعلانات المنشورة من إدارة المدرسة." to "Announcements published by school administration.",
    "الإعدادات" to "Settings",
    "خصص تجربة RUVEN على هذا الجهاز." to "Customize your RUVEN experience on this device.",
    "RUVEN • نسخة قيد التطوير" to "RUVEN • Development build",
    "حجم الخط: " to "Font size: ",
    "الشعبة: " to "Section: ",
    "الشعب المسجلة (" to "Registered sections (",
    "نوع الحساب: " to "Account type: ",
    "من " to "From ",
    " إلى " to " to ",
    "بدون شعبة" to "No section",
    "غير محدد" to "Not specified",
    "قرار مختوم" to "Sealed decision",
    "ختم الإدارة" to "Administration seal",
    "pending" to "Pending",
    "acknowledged" to "Acknowledged",
    "completed" to "Completed",
    "approved" to "Approved",
    "rejected" to "Rejected",
    "not_recorded" to "Not recorded",
    "present" to "Present",
    "absent" to "Absent",
    "student" to "Student",
    "teacher" to "Teacher",
    "class_monitor" to "Class monitor",
    "class_supervisor" to "Class supervisor",
    "assistant_principal" to "Assistant principal",
    "principal" to "Principal",
    "educational_counselor" to "Educational counselor",
    "owner" to "Owner",
    "student_parliament" to "Student parliament member",
    "العربية" to "Arabic",
    "كحلي زجاجي" to "Glass navy",
    "ليل داكن" to "Midnight",
    "فاتح" to "Light",
    "نعناعي" to "Mint",
    "إلغاء" to "Cancel",
    "نوع الملف غير مدعوم. استخدم PDF أو JPG أو PNG أو WEBP أو DOCX أو XLSX." to "Unsupported file type. Use PDF, JPG, PNG, WEBP, DOCX, or XLSX.",
    "حجم الملف يجب ألا يتجاوز 20 ميغابايت." to "File size must not exceed 20 MB.",
    "تعذر قراءة الملف المحدد." to "Could not read the selected file.",
    "تعذر فتح الملف المرفق." to "Could not open the attachment.",
    "تم الحفظ" to "Saved",
    "المرفقات" to "Attachments",
    "لا يوجد محتوى منشور بهذا القسم حالياً." to "No content is currently published in this section.",
    "أرسل المحتوى إلى خادم المدرسة" to "Send content to the school server",
    "أعداد العناصر المنشورة على الخادم" to "Published server content counts",
    "الواجبات المنشورة: " to "Published homework: ",
    "التحاضير المنشورة: " to "Published preparations: ",
    "الجداول المنشورة: " to "Published timetables: ",
    "مراقب الشعبة" to "Class monitor",
    "أستاذ مشرف شعبة" to "Class supervisor",
    "معاون المدير" to "Assistant principal",
    "مرشد تربوي" to "Educational counselor",
    "عضو تربوي من وزارة التربية" to "Ministry of Education member",
    "عضو برلمان الطلبة" to "Student parliament member",
    "المالك" to "Owner"
)

@Composable
private fun translated(ar: String): String {
    if (!LocalEnglish.current) return ar
    ruvenEnglishLabels[ar]?.let { return it }
    val prefix = ruvenEnglishLabels.entries.firstOrNull { (key, _) -> key.endsWith(" ") && ar.startsWith(key) }
    return if (prefix != null) prefix.value + ar.removePrefix(prefix.key) else ar
}

private val LocalEnglish = androidx.compose.runtime.staticCompositionLocalOf { false }

private fun ruvenText(ar: String, en: String, english: Boolean): String = if (english) en else ar

@Composable
private fun RuvenApp() {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var session by remember { mutableStateOf<SupabaseBackend.Session?>(null) }
    var profile by remember { mutableStateOf<SupabaseBackend.Profile?>(null) }
    var restoringSession by remember { mutableStateOf(true) }
    var remoteItems by remember { mutableStateOf<List<SupabaseBackend.SchoolItem>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val backend = remember { SupabaseBackend() }
    val scope = rememberCoroutineScope()
    val authPrefs = remember(context) { context.getSharedPreferences("ruven_auth", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        val savedRefreshToken = authPrefs.getString("refresh_token", null)
        if (!savedRefreshToken.isNullOrBlank()) {
            try {
                val restored = backend.refreshSession(savedRefreshToken)
                val restoredProfile = backend.loadMyProfile(restored)
                session = restored
                profile = restoredProfile
                authPrefs.edit().putString("refresh_token", restored.refreshToken).apply()
                try {
                    val loaded = backend.loadPublishedItems(restored, restoredProfile.className)
                    remoteItems = loaded
                    RuvenNotifications.processSync(context, restoredProfile.id, loaded)
                    runCatching { RuvenNotifications.processWorkflowSync(context, restoredProfile.id, backend.loadUserNotifications(restored)) }
                } catch (e: Exception) {
                    feedError = e.message ?: "تعذر تحميل محتوى المدرسة."
                }
            } catch (_: Exception) {
                authPrefs.edit().remove("refresh_token").apply()
            }
        }
        restoringSession = false
    }

    if (restoringSession) {
        Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(translated("جارٍ استعادة جلسة الدخول…"), color = Ink)
            }
        }
    } else if (session == null || profile == null) {
        val loginEnglish = context.getSharedPreferences("ruven_local_data", Context.MODE_PRIVATE).getString("settings_language", "ar") == "en"
        CompositionLocalProvider(LocalEnglish provides loginEnglish, LocalLayoutDirection provides (if (loginEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl)) {
        SupabaseLoginScreen { newSession, newProfile ->
            authPrefs.edit().putString("refresh_token", newSession.refreshToken).apply()
            session = newSession
            profile = newProfile
            feedError = null
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            scope.launch {
                try {
                    val loaded = backend.loadPublishedItems(newSession, newProfile.className)
                    remoteItems = loaded
                    // First sync is intentionally silent; only later new items alert the user.
                    RuvenNotifications.processSync(context, newProfile.id, loaded)
                    runCatching { RuvenNotifications.processWorkflowSync(context, newProfile.id, backend.loadUserNotifications(newSession)) }
                } catch (e: Exception) {
                    feedError = e.message ?: "تعذر تحميل محتوى المدرسة."
                }
            }
        }
        }
    } else {
        val activeSession = session!!
        val activeProfile = profile!!
        RuvenAuthenticatedApp(
            profile = activeProfile,
            session = activeSession,
            backend = backend,
            remoteItems = remoteItems,
            feedError = feedError,
            updateMessage = updateMessage,
            checkingUpdate = checkingUpdate,
            onDismissUpdateMessage = { updateMessage = null },
            onCheckUpdates = {
                if (!checkingUpdate) scope.launch {
                    checkingUpdate = true
                    updateMessage = "جارٍ فحص الإصدارات الرسمية…"
                    try {
                        val release = RuvenUpdater.latestRelease()
                        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                        val currentVersion = packageInfo.versionName ?: "0.1.0"
                        if (!RuvenUpdater.isNewer(currentVersion, release.versionName)) {
                            updateMessage = "أنت تستخدم أحدث إصدار رسمي (v$currentVersion)."
                        } else {
                            updateMessage = "يتوفر تحديث v${release.versionName}. جارٍ تنزيله…"
                            val activity = context as? Activity ?: throw IllegalStateException("افتح التحديث من التطبيق مباشرة.")
                            RuvenUpdater.downloadAndInstall(activity, release.apkUrl, release.sha256)
                            updateMessage = "تم تنزيل التحديث. أكمل التثبيت من نافذة أندرويد."
                        }
                    } catch (e: Exception) {
                        updateMessage = e.message ?: "تعذر فحص التحديثات."
                    } finally {
                        checkingUpdate = false
                    }
                }
            },
            onRefresh = {
                scope.launch {
                    try {
                        val loaded = backend.loadPublishedItems(activeSession, activeProfile.className)
                        remoteItems = loaded
                        RuvenNotifications.processSync(context, activeProfile.id, loaded)
                        runCatching { RuvenNotifications.processWorkflowSync(context, activeProfile.id, backend.loadUserNotifications(activeSession)) }
                        feedError = null
                    } catch (e: Exception) {
                        feedError = e.message ?: "تعذر تحديث محتوى المدرسة."
                    }
                }
            },
            onSignOut = {
                scope.launch { runCatching { backend.signOut(activeSession) } }
                authPrefs.edit().remove("refresh_token").apply()
                session = null
                profile = null
                remoteItems = emptyList()
                feedError = null
                scope.launch { backend.signOut(activeSession) }
            }
        )
    }
}

private fun schoolRoleFromDatabase(value: String): SchoolRole = when (value.lowercase()) {
    "class_monitor" -> SchoolRole.CLASS_MONITOR
    "parliament_member" -> SchoolRole.PARLIAMENT_MEMBER
    "teacher" -> SchoolRole.TEACHER
    "class_supervisor" -> SchoolRole.CLASS_SUPERVISOR
    "assistant_principal" -> SchoolRole.ASSISTANT_PRINCIPAL
    "principal" -> SchoolRole.PRINCIPAL
    "educational_counselor" -> SchoolRole.EDUCATIONAL_COUNSELOR
    "ministry_education_member" -> SchoolRole.MINISTRY_EDUCATION_MEMBER
    "owner" -> SchoolRole.OWNER
    else -> SchoolRole.STUDENT
}

@Composable
private fun RuvenAuthenticatedApp(
    profile: SupabaseBackend.Profile,
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    remoteItems: List<SupabaseBackend.SchoolItem>,
    feedError: String?,
    updateMessage: String?,
    checkingUpdate: Boolean,
    onDismissUpdateMessage: () -> Unit,
    onCheckUpdates: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val role = remember(profile.role) { schoolRoleFromDatabase(profile.role) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ruven_local_data", Context.MODE_PRIVATE) }
    var settingsVersion by remember { mutableIntStateOf(0) }
    val settingsSnapshot = remember(settingsVersion) {
        Triple(
            prefs.getString("settings_appearance", "dark") ?: "dark",
            prefs.getFloat("settings_font_scale", 1.0f),
            prefs.getString("settings_background", "navy") ?: "navy"
        )
    }
    val appLanguage = remember(settingsVersion) { prefs.getString("settings_language", "ar") ?: "ar" }
    val isEnglish = appLanguage == "en"
    val appBackground = when (settingsSnapshot.third) {
        "midnight" -> Color(0xFF171923)
        "light" -> Color(0xFFF1F5F9)
        "mint" -> Color(0xFFE5F6F0)
        else -> Color(0xFF101B32)
    }
    val glassEnabled = settingsSnapshot.first == "glass"
    val glassOpacity = prefs.getFloat("settings_glass_opacity", 0.72f).coerceIn(0.25f, 0.96f)
    val glassGlow = prefs.getFloat("settings_glass_glow", 0.45f).coerceIn(0f, 1f)
    val glassBlur = prefs.getFloat("settings_glass_blur", 12f).coerceIn(0f, 28f)
    val appSurface = if (glassEnabled) Color(0xFFB9DDFB).copy(alpha = (glassOpacity * (0.7f + glassGlow * 0.3f)).coerceIn(0.25f, 0.98f))
        else if (settingsSnapshot.first == "light") Color.White else Color(0xFF19263B)
    val wallpaperUri = remember(settingsVersion) { prefs.getString("settings_wallpaper_uri", null) }
    val wallpaperBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, wallpaperUri) {
        value = if (wallpaperUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(wallpaperUri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val homework = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "homework")) } }
    val preparations = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "preparations")) } }
    val schedules = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "schedules")) } }
    val scheduleFiles = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "schedule_files")) } }
    val reports = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "reports")) } }
    val announcements = remember(prefs) { mutableStateListOf<String>().apply { addAll(readLocalItems(prefs, "announcements")) } }
    LaunchedEffect(homework.toList()) { writeLocalItems(prefs, "homework", homework.toList()) }
    LaunchedEffect(preparations.toList()) { writeLocalItems(prefs, "preparations", preparations.toList()) }
    LaunchedEffect(schedules.toList()) { writeLocalItems(prefs, "schedules", schedules.toList()) }
    LaunchedEffect(scheduleFiles.toList()) { writeLocalItems(prefs, "schedule_files", scheduleFiles.toList()) }
    LaunchedEffect(reports.toList()) { writeLocalItems(prefs, "reports", reports.toList()) }
    LaunchedEffect(announcements.toList()) { writeLocalItems(prefs, "announcements", announcements.toList()) }
    if (updateMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissUpdateMessage,
            title = { Text(translated("تحديث RUVEN")) },
            text = { Text(updateMessage) },
            confirmButton = {
                TextButton(onClick = onDismissUpdateMessage) { Text(translated("حسناً")) }
            }
        )
    }
    CompositionLocalProvider(LocalEnglish provides isEnglish, LocalLayoutDirection provides (if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl), LocalDensity provides androidx.compose.ui.unit.Density(LocalDensity.current.density, settingsSnapshot.second)) {
        Box(Modifier.fillMaxSize()) {
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap!!,
                    contentDescription = "خلفية RUVEN",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (glassEnabled && glassBlur > 0f) Modifier.blur(glassBlur.dp) else Modifier)
                )
                if (glassEnabled) Box(Modifier.fillMaxSize().background(Color(0xFF0B1930).copy(alpha = (1f - glassOpacity).coerceIn(0.05f, 0.65f))))
            } else if (glassEnabled) {
                Box(Modifier.fillMaxSize().background(appBackground)) {
                    Box(
                        Modifier.size(340.dp).offset(x = (-110).dp, y = 120.dp)
                            .background(Brush.radialGradient(listOf(Color(0xFF56C7E8).copy(alpha = 0.65f * glassGlow), Color.Transparent)), CircleShape)
                            .then(if (glassBlur > 0f) Modifier.blur(glassBlur.dp) else Modifier)
                    )
                    Box(
                        Modifier.size(300.dp).align(Alignment.BottomEnd).offset(x = 80.dp, y = (-90).dp)
                            .background(Brush.radialGradient(listOf(Color(0xFF5E8BFF).copy(alpha = 0.55f * glassGlow), Color.Transparent)), CircleShape)
                            .then(if (glassBlur > 0f) Modifier.blur(glassBlur.dp) else Modifier)
                    )
                }
            }
            Scaffold(containerColor = if (wallpaperBitmap != null) appBackground.copy(alpha = if (glassEnabled) 0.12f else 0.82f) else appBackground, bottomBar = {
            NavigationBar(containerColor = appSurface, tonalElevation = 8.dp) {
                val tabs = buildList {
                    add(RuvenTab.Home to Icons.Rounded.Home)
                    add(RuvenTab.Schedule to Icons.Rounded.CalendarMonth)
                    add(RuvenTab.Homework to Icons.AutoMirrored.Rounded.MenuBook)
                    add(RuvenTab.Preparations to Icons.Rounded.Description)
                    add(RuvenTab.Notices to Icons.AutoMirrored.Rounded.Announcement)
                    add(RuvenTab.Workflows to Icons.Rounded.Description)
                    if (role == SchoolRole.OWNER) {
                        add(RuvenTab.Admin to Icons.Rounded.School)
                        add(RuvenTab.Sections to Icons.Rounded.School)
                        add(RuvenTab.Roster to Icons.Rounded.Person)
                    }
                    add(RuvenTab.Notifications to Icons.Rounded.Notifications)
                    add(RuvenTab.Settings to Icons.Rounded.Settings)
                }
                tabs.forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(item.second, contentDescription = if (isEnglish) item.first.englishTitle else item.first.title) },
                        label = { Text(if (isEnglish) item.first.englishTitle else item.first.title, fontSize = 10.sp, maxLines = 1) }, alwaysShowLabel = true)
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().background(appSurface).padding(horizontal = 18.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(translated("RUVEN"), color = Blue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text(ruvenText(translated("منظومتك المدرسية"), "Your school system", isEnglish), color = Muted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(profile.fullName, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(roleLabel(role), color = Blue, fontSize = 11.sp)
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = ruvenText(translated("تحديث المحتوى والإشعارات"), "Refresh content and notifications", isEnglish), tint = Blue)
                            }
                            TextButton(
                                onClick = onCheckUpdates,
                                enabled = !checkingUpdate,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                if (checkingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = Blue
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Blue
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    if (checkingUpdate) ruvenText(translated("جارٍ الفحص…"), "Checking…", isEnglish) else ruvenText(translated("تحقق من التحديثات"), "Check for updates", isEnglish),
                                    color = Blue,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = onSignOut) { Text(translated("خروج"), color = Blue, fontSize = 11.sp) }
                        }
                    }
                }
                Surface(color = Color(0xFFEAF2FF), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                        Text(
                            "بعض الأقسام ما زالت قيد التطوير؛ المنشورات هنا تُحمّل من خادم المدرسة.",
                            color = Color(0xFF315786), fontSize = 10.sp, lineHeight = 15.sp
                        )
                    }
                }
                AnimatedContent(targetState = tab,
                    transitionSpec = {
                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                            slideInVertically(animationSpec = androidx.compose.animation.core.tween(220)) { it / 18 }) togetherWith
                            (fadeOut(animationSpec = androidx.compose.animation.core.tween(140)) +
                                androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(140)) { -it / 24 })
                    },
                    label = "ruven-tab-transition", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        0 -> HomeScreen(role, remoteItems.count { it.itemType == "homework" }, remoteItems.count { it.itemType == "preparation" }, remoteItems.count { it.itemType == "timetable" }, remoteItems, feedError, isEnglish) { tab = it }
                        1 -> ServerSchoolItemsScreen(session, backend, remoteItems, "timetable", ruvenText(translated("الجدول الدراسي"), "Timetable", isEnglish), ruvenText(translated("الجداول المنشورة من إدارة المدرسة."), "Timetables published by school administration.", isEnglish), role, profile.className, onRefresh, isEnglish)
                        2 -> ServerSchoolItemsScreen(session, backend, remoteItems, "homework", ruvenText(translated("الواجبات"), "Homework", isEnglish), ruvenText(translated("الواجبات المنشورة حسب المادة والشعبة."), "Homework published by subject and class.", isEnglish), role, profile.className, onRefresh, isEnglish)
                        3 -> ServerSchoolItemsScreen(session, backend, remoteItems, "preparation", ruvenText(translated("التحاضير"), "Preparations", isEnglish), ruvenText(translated("تحاضير الدروس المنشورة من المدرسين والإدارة."), "Lesson preparations published by teachers and administration.", isEnglish), role, profile.className, onRefresh, isEnglish)
                        4 -> ServerSchoolItemsScreen(session, backend, remoteItems, "announcement", ruvenText(translated("الإعلانات المدرسية"), "School announcements", isEnglish), ruvenText(translated("الإعلانات المنشورة من إدارة المدرسة."), "Announcements published by school administration.", isEnglish), role, profile.className, onRefresh, isEnglish)
                        5 -> SchoolWorkflowsScreen(profile, session, backend, role)
                        6 -> if (role == SchoolRole.OWNER) OwnerAccountManagementScreen(session, backend)
                            else ServerNotificationsScreen(session, backend)
                        7 -> if (role == SchoolRole.OWNER) OwnerSectionsScreen(session, backend)
                            else RuvSettingsScreen(prefs = prefs, session = session, backend = backend, onSaved = { settingsVersion++ }, onSignOut = onSignOut)
                        8 -> if (role == SchoolRole.OWNER) OwnerStudentRosterScreen(session, backend) else ServerNotificationsScreen(session, backend)
                        9 -> ServerNotificationsScreen(session, backend)
                        else -> RuvSettingsScreen(prefs = prefs, session = session, backend = backend, onSaved = { settingsVersion++ }, onSignOut = onSignOut)
                    }
                }
            }
        }
        }
    }
}

@Composable

private fun isSchoolAdminRole(role: SchoolRole): Boolean = role in setOf(
    SchoolRole.OWNER, SchoolRole.PRINCIPAL, SchoolRole.ASSISTANT_PRINCIPAL, SchoolRole.EDUCATIONAL_COUNSELOR
)

private fun localIsoDate(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

@Composable
private fun ServerNotificationsScreen(session: SupabaseBackend.Session, backend: SupabaseBackend) {
    var notifications by remember { mutableStateOf<List<SupabaseBackend.UserNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            loading = true
            try { notifications = backend.loadUserNotifications(session); error = null }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل الإشعارات." }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.userId) { reload() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader("الإشعارات", "إشعارات فعلية من قاعدة البيانات: قرارات الإدارة وحالة الإجازات.") }
        item { OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text(translated("تحديث")) } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (!loading && notifications.isEmpty() && error == null) item { Text(translated("ما عندك إشعارات حاليًا."), color = Muted) }
        items(notifications, key = { it.id }) { n ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (n.readAt == null) Color(0xFFEAF2FF) else Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n.title, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (n.readAt == null) Text(translated("جديد"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(n.body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Text(n.createdAt, color = Muted, fontSize = 10.sp)
                    if (n.readAt == null) TextButton(onClick = {
                        scope.launch { try { backend.markNotificationRead(session, n.id); notifications = notifications.map { if (it.id == n.id) it.copy(readAt = localIsoDate()) else it } } catch (e: Exception) { error = e.message ?: "تعذر تحديث الإشعار." } }
                    }) { Text(translated("تحديد كمقروء")) }
                }
            }
        }
    }
}

@Composable
private fun SchoolWorkflowsScreen(profile: SupabaseBackend.Profile, session: SupabaseBackend.Session, backend: SupabaseBackend, role: SchoolRole) {
    val admin = isSchoolAdminRole(role)
    val monitor = role == SchoolRole.CLASS_MONITOR
    val student = role == SchoolRole.STUDENT
    var section by remember { mutableStateOf(if (student) "الإجازات" else "الدليل") }
    var profiles by remember { mutableStateOf<List<SupabaseBackend.ManagedProfile>>(emptyList()) }
    var seals by remember { mutableStateOf<List<SupabaseBackend.SchoolSeal>>(emptyList()) }
    var decisions by remember { mutableStateOf<List<SupabaseBackend.SchoolDecision>>(emptyList()) }
    var leaves by remember { mutableStateOf<List<SupabaseBackend.LeaveRequest>>(emptyList()) }
    var dailyReports by remember { mutableStateOf<List<SupabaseBackend.DailyReport>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedStudent by remember { mutableStateOf("") }
    var selectedMonitor by remember { mutableStateOf("") }
    var selectedSeal by remember { mutableStateOf("") }
    var executionNote by remember { mutableStateOf("") }
    var targetClass by remember { mutableStateOf("") }
    var sourceClass by remember { mutableStateOf(profile.className.orEmpty()) }
    var decisionType by remember { mutableStateOf("transfer_student") }
    var decisionReason by remember { mutableStateOf("") }
    var sealName by remember { mutableStateOf("") }
    var sealTitle by remember { mutableStateOf(if (role == SchoolRole.OWNER) "المالك" else "الإدارة المدرسية") }
    var leaveStart by remember { mutableStateOf(localIsoDate()) }
    var leaveEnd by remember { mutableStateOf(localIsoDate()) }
    var leaveReason by remember { mutableStateOf("") }
    var reportDate by remember { mutableStateOf(localIsoDate()) }
    var reportEvents by remember { mutableStateOf("") }
    var reportPromises by remember { mutableStateOf("") }
    var reportUnresolved by remember { mutableStateOf("") }
    var filterTeachers by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mySeals = seals.filter { it.ownerId == session.userId }
    val allowedTabs = buildList {
        add("الدليل")
        add("القرارات")
        add("الإجازات")
        add("التقرير اليومي")
        if (admin) add("الأختام")
    }
    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                seals = backend.loadSchoolSeals(session)
                if (admin || monitor) profiles = backend.loadManagedProfiles(session)
                decisions = backend.loadSchoolDecisions(session)
                leaves = backend.loadLeaveRequests(session)
                dailyReports = backend.loadDailyReports(session, if (admin) null else profile.className)
                if (selectedSeal.isBlank() || seals.none { it.id == selectedSeal && it.ownerId == session.userId }) selectedSeal = seals.firstOrNull { it.ownerId == session.userId }?.id.orEmpty()
            } catch (e: Exception) { error = e.message ?: "تعذر تحميل بيانات العمليات المدرسية." }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.userId) { reload() }
    fun perform(action: suspend () -> Unit, success: String) {
        scope.launch {
            busy = true
            try { action(); notice = success; error = null; reload() }
            catch (e: Exception) { error = e.message ?: "تعذر تنفيذ العملية." }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("العمليات المدرسية", "الدليل، القرارات المختومة، طلبات الإجازة، والتقرير اليومي المتصل بالخادم.") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allowedTabs.forEach { label ->
                    FilterChip(selected = section == label, onClick = { section = label }, label = { Text(translated(label), fontSize = 11.sp) })
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (notice != null) item { Text(notice!!, color = Color(0xFF16845B), fontSize = 12.sp) }
        when (section) {
            "الدليل" -> {
                if (!admin && !monitor) item { Text(translated("قائمة الأسماء متاحة للمراقبين والإدارة. الطلاب يقدمون الإجازات ويتابعون التقارير من الأقسام الأخرى."), color = Muted, fontSize = 12.sp) }
                if (admin || monitor) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = filterTeachers, onCheckedChange = { filterTeachers = it }); Text(translated("عرض المدرسين والهيئة التربوية فقط"), color = Ink, fontSize = 12.sp) } }
                    val visibleProfiles = profiles.filter { p -> if (filterTeachers) p.role in setOf("teacher","class_supervisor","assistant_principal","principal","educational_counselor") else true }
                    items(visibleProfiles, key = { it.id }) { p ->
                        Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(p.fullName, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(translated("${p.role} • ${p.className ?: "بدون شعبة"}"), color = Muted, fontSize = 11.sp)
                                }
                                if (admin) TextButton(onClick = { selectedStudent = p.id; section = "القرارات" }, enabled = p.role == "student") { Text(translated("قرار")) }
                            }
                        }
                    }
                }
            }
            "الأختام" -> {
                if (!admin) item { Text(translated("إدارة الأختام متاحة للمالك وأعضاء الإدارة المخوّلين فقط."), color = Muted) }
                if (admin) {
                    item { OutlinedTextField(sealName, { sealName = it }, label = { Text(translated("اسم الختم")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(sealTitle, { sealTitle = it }, label = { Text(translated("صفة صاحب الختم")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { Button(onClick = { perform({ backend.createSchoolSeal(session,sealName,sealTitle); sealName = "" }, "تم إنشاء الختم.") }, enabled = !busy && sealName.trim().length >= 2 && sealTitle.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جارٍ الحفظ…" else "إنشاء ختم") } }
                    items(seals, key = { it.id }) { seal -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(translated("ختم: ${seal.name}"), color = Ink, fontWeight = FontWeight.Bold); Text(seal.title, color = Muted, fontSize = 12.sp); Text(translated("معرّف: ${seal.id.take(8)}…"), color = Muted, fontSize = 10.sp) } } }
                }
            }
            "القرارات" -> {
                if (admin) {
                    item { Text(translated("إنشاء قرار إداري"), color = Ink, fontWeight = FontWeight.Bold) }
                    item { Text(translated("الطالب المستهدف"), color = Muted, fontSize = 12.sp) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            profiles.filter { it.role == "student" }.take(100).forEach { p ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedStudent == p.id, onClick = { selectedStudent = p.id })
                                    Column { Text(p.fullName, color = Ink, fontSize = 12.sp); Text(p.className ?: "بدون شعبة", color = Muted, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                    item { Text(translated("نوع القرار"), color = Ink, fontWeight = FontWeight.SemiBold) }
                    item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("transfer_student" to "نقل", "remove_student" to "حذف", "accept_request" to "قبول", "other" to "آخر").forEach { (v,l) -> FilterChip(selected = decisionType == v, onClick = { decisionType = v }, label = { Text(l) }) }
                    } }
                    item { OutlinedTextField(sourceClass, { sourceClass = it }, label = { Text(translated("الشعبة الحالية")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(targetClass, { targetClass = it }, label = { Text(translated("الشعبة الجديدة (إن وجدت)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(decisionReason, { decisionReason = it }, label = { Text(translated("السبب والتعليمات")) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { Text(translated("ختم القرار"), color = Ink, fontWeight = FontWeight.SemiBold) }
                    item { Column { mySeals.forEach { seal -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedSeal == seal.id, onClick = { selectedSeal = seal.id }); Text(translated("${seal.name} • ${seal.title}"), color = Ink, fontSize = 12.sp) } }; if (mySeals.isEmpty()) Text(translated("لا يوجد ختم باسم حسابك. أنشئ ختمًا من تبويب الأختام أولًا."), color = Muted, fontSize = 11.sp) } }
                    item { Text(translated("تعيين مراقب للتنفيذ"), color = Ink, fontWeight = FontWeight.SemiBold) }
                    item { Column { profiles.filter { it.role == "class_monitor" }.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selectedMonitor == p.id, onClick = { selectedMonitor = p.id }); Text(translated("${p.fullName} • ${p.className ?: "بدون شعبة"}"), color = Ink, fontSize = 12.sp) } } } }
                    item { Button(onClick = { val p = profiles.firstOrNull { it.id == selectedStudent }; perform({ backend.createSchoolDecision(session,decisionType,selectedStudent,sourceClass.ifBlank { p?.className },targetClass,decisionReason,selectedMonitor,selectedSeal); decisionReason = "" }, "تم إرسال القرار المختوم للمراقب.") }, enabled = !busy && selectedStudent.isNotBlank() && selectedSeal.isNotBlank() && decisionReason.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text(translated("إصدار القرار وإشعار المراقب")) } }
                }
                if (decisions.isEmpty()) item { Text(translated("ماكو قرارات ظاهرة لهذا الحساب."), color = Muted, fontSize = 12.sp) }
                items(decisions, key = { it.id }) { d ->
                    val studentName = profiles.firstOrNull { it.id == d.studentId }?.fullName ?: d.studentId.take(8)
                    val seal = seals.firstOrNull { it.id == d.sealId }
                    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(translated("${when(d.type) { "transfer_student" -> "قرار نقل"; "remove_student" -> "قرار حذف"; "accept_request" -> "قرار قبول"; else -> "قرار إداري" }} • $studentName"), color = Ink, fontWeight = FontWeight.Bold)
                            Text(d.reason, color = Muted, fontSize = 12.sp)
                            Text(translated("الحالة: ${d.status} • الختم: ${seal?.name ?: "قرار مختوم"}"), color = Blue, fontSize = 11.sp)
                            if (d.targetClass != null) Text(translated("من ${d.sourceClass ?: "غير محدد"} إلى ${d.targetClass}"), color = Muted, fontSize = 11.sp)
                            if (monitor && d.assignedMonitor == session.userId && d.status in setOf("pending","acknowledged")) {
                                OutlinedTextField(executionNote, { executionNote = it }, label = { Text(translated("ملاحظة التنفيذ (اختيارية)")) }, modifier = Modifier.fillMaxWidth(), minLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { perform({ backend.updateSchoolDecision(session,d.id,"acknowledged",executionNote) }, "تم تأكيد استلام القرار.") }, modifier = Modifier.weight(1f)) { Text(translated("استلام")) }
                                    Button(onClick = { perform({ backend.updateSchoolDecision(session,d.id,"completed",executionNote.ifBlank { "تم التنفيذ" }) }, "تم تسجيل تنفيذ القرار.") }, modifier = Modifier.weight(1f)) { Text(translated("تم التنفيذ")) }
                                }
                            }
                        }
                    }
                }
            }
            "الإجازات" -> {
                if (student) {
                    item { Text(translated("طلب إجازة"), color = Ink, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(leaveStart, { leaveStart = it }, label = { Text(translated("من تاريخ YYYY-MM-DD")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(leaveEnd, { leaveEnd = it }, label = { Text(translated("إلى تاريخ YYYY-MM-DD")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(leaveReason, { leaveReason = it }, label = { Text(translated("سبب الإجازة")) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { Button(onClick = { perform({ backend.createLeaveRequest(session,profile.className ?: "",leaveStart,leaveEnd,leaveReason); leaveReason = "" }, "تم إرسال طلب الإجازة للإدارة.") }, enabled = !busy && !profile.className.isNullOrBlank() && leaveReason.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text(translated("إرسال طلب الإجازة")) } }
                }
                if (admin && leaves.any { it.status == "pending" }) item { Text(translated("طلبات بانتظار قرار الإدارة"), color = Ink, fontWeight = FontWeight.Bold) }
                if (leaves.isEmpty()) item { Text(translated("ماكو طلبات إجازة ظاهرة حاليًا."), color = Muted, fontSize = 12.sp) }
                items(leaves, key = { it.id }) { l ->
                    val studentName = profiles.firstOrNull { it.id == l.studentId }?.fullName ?: if (l.studentId == session.userId) profile.fullName else l.studentId.take(8)
                    val seal = seals.firstOrNull { it.id == l.sealId }
                    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(studentName, color = Ink, fontWeight = FontWeight.Bold)
                            Text(translated("${l.startsOn} → ${l.endsOn} • ${l.className}"), color = Blue, fontSize = 11.sp)
                            Text(translated("السبب: ${l.reason}"), color = Muted, fontSize = 12.sp)
                            Text(translated("الحالة: ${l.status} • الحضور: ${l.attendanceStatus}"), color = Ink, fontSize = 11.sp)
                            if (l.status == "approved") Text(translated("تمت الموافقة بختم: ${seal?.name ?: "ختم الإدارة"}"), color = Color(0xFF16845B), fontSize = 11.sp)
                            if (admin && l.status == "pending") {
                                if (mySeals.isEmpty()) Text(translated("أنشئ ختمًا باسم حسابك أولًا حتى توافق على الطلب."), color = Muted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { perform({ backend.reviewLeaveRequest(session,l.id,true,mySeals.firstOrNull()?.id) }, "تمت الموافقة على الإجازة وأُرسل إشعار للطالب.") }, enabled = !busy && mySeals.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(translated("موافقة")) }
                                    OutlinedButton(onClick = { perform({ backend.reviewLeaveRequest(session,l.id,false,null) }, "تم رفض الإجازة وإشعار الطالب.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(translated("رفض")) }
                                }
                            }
                            if (monitor && l.status == "approved" && l.className == profile.className && l.attendanceStatus == "not_recorded") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { perform({ backend.markLeaveAttendance(session,l.id,"present") }, "تم تسجيل الحضور.") }, modifier = Modifier.weight(1f)) { Text(translated("حاضر")) }
                                    Button(onClick = { perform({ backend.markLeaveAttendance(session,l.id,"absent") }, "تم تسجيل الغياب.") }, modifier = Modifier.weight(1f)) { Text(translated("غائب")) }
                                }
                            }
                        }
                    }
                }
            }
            "التقرير اليومي" -> {
                if (monitor) {
                    item { OutlinedTextField(reportDate, { reportDate = it }, label = { Text(translated("تاريخ التقرير YYYY-MM-DD")) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(reportEvents, { reportEvents = it }, label = { Text(translated("أهم أحداث اليوم")) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(reportPromises, { reportPromises = it }, label = { Text(translated("وعود الإدارة أو الأمور المقررة")) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(reportUnresolved, { reportUnresolved = it }, label = { Text(translated("أمور لم تُنفّذ بعد")) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { Button(onClick = { perform({ backend.createDailyReport(session,profile.className ?: "",reportDate,reportEvents,reportPromises,reportUnresolved,true); reportEvents = ""; reportPromises = ""; reportUnresolved = "" }, "نُشر التقرير اليومي لطلاب الشعبة.") }, enabled = !busy && !profile.className.isNullOrBlank() && (reportEvents.isNotBlank() || reportPromises.isNotBlank() || reportUnresolved.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text(translated("نشر التقرير للطلاب")) } }
                }
                if (dailyReports.isEmpty()) item { Text(translated("ماكو تقارير يومية منشورة حاليًا."), color = Muted, fontSize = 12.sp) }
                items(dailyReports, key = { it.id }) { report ->
                    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(translated("${report.className} • ${report.reportDate}"), color = Ink, fontWeight = FontWeight.Bold)
                            if (report.events.isNotBlank()) { Text(translated("أحداث اليوم"), color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(report.events, color = Ink, fontSize = 12.sp) }
                            if (report.promises.isNotBlank()) { Text(translated("وعود الإدارة"), color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(report.promises, color = Ink, fontSize = 12.sp) }
                            if (report.unresolved.isNotBlank()) { Text(translated("لم يُنفّذ بعد"), color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(report.unresolved, color = Ink, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text(translated("تحديث بيانات الخادم")) } }
    }
}

@Composable
private fun NotificationsScreen(
    items: List<SupabaseBackend.SchoolItem>,
    error: String?,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader("الإشعارات", "آخر تحديثات المدرسة المنشورة. اسحب التحديث من الخادم بزر التحديث.")
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(translated("تحديث الإشعارات"))
            }
        }
        if (error != null) item {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        if (items.isEmpty()) item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Blue, modifier = Modifier.size(32.dp))
                    Text(translated("ماكو إشعارات منشورة حاليًا"), fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(translated("تظهر هنا تحديثات المدرسة المتاحة لحسابك."), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        } else items(items, key = { it.id }) { item ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, color = Ink, fontWeight = FontWeight.Bold)
                    Text(item.body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Text(item.createdAt, color = Blue, fontSize = 10.sp)
                }
            }
        }
        item {
            Text(
                "تنبيه: هذه الصفحة تعرض المنشورات المنشورة من الخادم حاليًا؛ إشعارات القرارات والإجازات ستظهر بعد ربط واجهات سير العمل بها.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SettingsScreen(prefs: SharedPreferences, onSaved: () -> Unit) {
    var language by remember { mutableStateOf(prefs.getString("settings_language", "ar") ?: "ar") }
    var appearance by remember { mutableStateOf(prefs.getString("settings_appearance", "dark") ?: "dark") }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("settings_font_scale", 1.0f)) }
    var background by remember { mutableStateOf(prefs.getString("settings_background", "navy") ?: "navy") }
    var saved by remember { mutableStateOf(false) }
    val backgroundOptions = listOf(
        "navy" to ("كحلي زجاجي" to Color(0xFF101B32)),
        "midnight" to ("ليل داكن" to Color(0xFF171923)),
        "light" to ("فاتح" to Color(0xFFF1F5F9)),
        "mint" to ("نعناعي" to Color(0xFFE5F6F0))
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PageHeader("الإعدادات", "خصص تجربة RUVEN على هذا الجهاز.") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(translated("اللغة / Language"), color = Ink, fontWeight = FontWeight.Bold)
                    listOf("ar" to "العربية", "en" to "English", "mixed" to "Mixed • عناوين إنجليزية وشرح عربي").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = language == value, onClick = { language = value; saved = false })
                            Text(label, color = Ink, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider()
                    Text(translated("المظهر"), color = Ink, fontWeight = FontWeight.Bold)
                    listOf("dark" to "داكن", "light" to "فاتح", "glass" to "زجاجي").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = appearance == value, onClick = { appearance = value; saved = false })
                            Text(label, color = Ink, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider()
                    Text(translated("حجم الخط: ${(fontScale * 100).toInt()}%"), color = Ink, fontWeight = FontWeight.Bold)
                    Slider(value = fontScale, onValueChange = { fontScale = it; saved = false }, valueRange = 0.85f..1.3f)
                    Text(translated("الخلفية"), color = Ink, fontWeight = FontWeight.Bold)
                    backgroundOptions.forEach { (value, pair) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = background == value, onClick = { background = value; saved = false })
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(pair.second))
                            Spacer(Modifier.width(9.dp))
                            Text(pair.first, color = Ink, fontSize = 13.sp)
                        }
                    }
                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("settings_language", language)
                                .putString("settings_appearance", appearance)
                                .putFloat("settings_font_scale", fontScale)
                                .putString("settings_background", background)
                                .apply()
                            saved = true
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(translated("حفظ الإعدادات"))
                    }
                    AnimatedVisibility(visible = saved, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                        Text(translated("انحفظت تفضيلاتك على هذا الجهاز."), color = Color(0xFF16845B), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Text(
                "اللغة والخط المخصص وصورة الخلفية تحتاج ربطًا إضافيًا؛ ألوان الخلفية وشريط التنقل تُطبّق بعد الحفظ.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
            )
        }
    }
}

private fun roleLabel(role: SchoolRole): String = when (role) {
    SchoolRole.STUDENT -> "طالب"
    SchoolRole.CLASS_MONITOR -> "مراقب الشعبة"
    SchoolRole.PARLIAMENT_MEMBER -> "عضو برلمان الطلبة"
    SchoolRole.TEACHER -> "أستاذ"
    SchoolRole.CLASS_SUPERVISOR -> "أستاذ مشرف شعبة"
    SchoolRole.ASSISTANT_PRINCIPAL -> "معاون المدير"
    SchoolRole.PRINCIPAL -> "المدير"
    SchoolRole.EDUCATIONAL_COUNSELOR -> "مرشد تربوي"
    SchoolRole.MINISTRY_EDUCATION_MEMBER -> "عضو تربوي من وزارة التربية"
    SchoolRole.OWNER -> "المالك"
}


private data class RoleOption(val value: String, val label: String)

private val managedRoleOptions = listOf(
    RoleOption("student", "طالب"),
    RoleOption("class_monitor", "مراقب شعبة"),
    RoleOption("parliament_member", "عضو برلمان الطلبة"),
    RoleOption("teacher", "أستاذ"),
    RoleOption("class_supervisor", "أستاذ مشرف شعبة"),
    RoleOption("assistant_principal", "معاون"),
    RoleOption("principal", "مدير"),
    RoleOption("educational_counselor", "مرشد تربوي"),
    RoleOption("ministry_education_member", "عضو تربوي من وزارة التربية")
)

@Composable
private fun OwnerStudentRosterScreen(session: SupabaseBackend.Session, backend: SupabaseBackend) {
    var grade by remember { mutableStateOf("الثالث المتوسط") }
    var section by remember { mutableStateOf("C") }
    var pastedNames by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SupabaseBackend.RosterEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            loading = true
            try { entries = backend.loadStudentRoster(session); error = null }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل سجل الطلاب." }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.userId) { reload() }
    val names = pastedNames.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.length >= 2 }
    val uniqueNames = names.distinctBy { it.lowercase() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("سجل الطلاب والتحقق من الشعب", "الصق قائمة نصية معتمدة؛ كل اسم في سطر مستقل. لن تُعتمد شعبة الطالب من اختياره وحده.") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("الصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = section, onValueChange = { section = it.uppercase().take(1) }, label = { Text("الشعبة (A–F)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = pastedNames, onValueChange = { pastedNames = it }, label = { Text("الأسماء — كل اسم بسطر") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5)
                    Text("بعد التنظيف: ${uniqueNames.size} اسم فريد • ${names.size - uniqueNames.size} تكرار داخل النص", color = Muted, fontSize = 12.sp)
                    Button(onClick = {
                        scope.launch {
                            loading = true; error = null; message = null
                            try {
                                val result = backend.importStudentRosterText(session, grade, section, pastedNames)
                                message = "تم حفظ ${result.first} اسمًا. سجلات لم تُحفظ/أخطاء: ${result.second}."
                                pastedNames = ""
                                entries = backend.loadStudentRoster(session)
                            } catch (e: Exception) { error = e.message ?: "تعذر حفظ القائمة." }
                            finally { loading = false }
                        }
                    }, enabled = !loading && grade.isNotBlank() && section in setOf("A","B","C","D","E","F") && uniqueNames.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "جارٍ الحفظ…" else "حفظ قائمة الشعبة")
                    }
                    TextButton(onClick = { reload() }, enabled = !loading) { Text("تحديث السجل") }
                    message?.let { Text(it, color = Blue) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { PageHeader("الأسماء المحفوظة", "إجمالي السجلات: ${entries.size}") }
        items(entries, key = { it.id }) { entry ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(entry.fullName, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("${entry.gradeName} — ${entry.sectionCode} • ${entry.status}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun OwnerSectionsScreen(session: SupabaseBackend.Session, backend: SupabaseBackend) {
    var sections by remember { mutableStateOf<List<SupabaseBackend.ManagedSection>>(emptyList()) }
    var sectionSearch by remember { mutableStateOf("") }
    var gradeName by remember { mutableStateOf("الثالث متوسط") }
    var sectionCode by remember { mutableStateOf("A") }
    var sectionMenu by remember { mutableStateOf(false) }
    var showCreateForm by remember { mutableStateOf(false) }
    var openOnCreate by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            try { sections = backend.loadManagedSections(session); error = null }
            catch (e: Exception) { error = e.message ?: "تعذر تحميل الشعب." }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.userId) { reload() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("إدارة الشعب", "إدارة مرئية للشعب المفتوحة والمغلقة، مع منع إنشاء الشعب غير المطلوبة.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("إجمالي الشعب" to sections.size, "مفتوحة" to sections.count { it.isOpen }, "مغلقة" to sections.count { !it.isOpen }).forEach { (label, count) ->
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(count.toString(), color = Blue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(translated("إضافة شعبة"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCreateForm) "أدخل الصف ورمز الشعبة المطلوبة" else "افتح النموذج عند الحاجة فقط", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showCreateForm = !showCreateForm }) {
                            Text(if (showCreateForm) "إغلاق ▲" else "إضافة +")
                        }
                    }
                    if (showCreateForm) {
                    OutlinedTextField(gradeName, { gradeName = it }, label = { Text(translated("الصف الدراسي")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box {
                        OutlinedButton(onClick = { sectionMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(translated("الشعبة: $sectionCode")) }
                        DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                            listOf("A", "B", "C", "D", "E", "F").forEach { code ->
                                DropdownMenuItem(text = { Text(code) }, onClick = { sectionCode = code; sectionMenu = false })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = openOnCreate, onCheckedChange = { openOnCreate = it })
                        Text(translated("فتح الشعبة مباشرة للطلاب والموظفين"), color = Ink, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true; error = null; notice = null
                                try {
                                    backend.createManagedSection(session, gradeName, sectionCode, openOnCreate)
                                    notice = "تم إنشاء الشعبة $sectionCode."
                                    sections = backend.loadManagedSections(session)
                                    showCreateForm = false
                                } catch (e: Exception) { error = e.message ?: "تعذر إنشاء الشعبة." }
                                finally { saving = false }
                            }
                        },
                        enabled = !saving && gradeName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (saving) "جارٍ الإنشاء..." else "إنشاء الشعبة") }
                    }
                }
            }
        }
        if (notice != null) item { Text(notice!!, color = Color(0xFF176B45), fontSize = 13.sp) }
        if (error != null) item { EmptyCard("تعذر تنفيذ العملية", error!!) }
        item {
            OutlinedTextField(
                value = sectionSearch, onValueChange = { sectionSearch = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(translated("بحث بالصف أو رمز الشعبة")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(translated("الشعب المسجلة (${sections.size})"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { reload() }, enabled = !loading) { Text(if (loading) "تحميل..." else "تحديث") }
            }
        }
        if (!loading && sections.isEmpty()) item { EmptyCard("ماكو شعب مسجلة", "أنشئ الشعبة المطلوبة فقط؛ الشعب الفارغة لا تنشأ وحدها.") }
        val filteredSections = sections.filter { section ->
            val query = sectionSearch.trim()
            query.isEmpty() || section.gradeName.contains(query, ignoreCase = true) || section.sectionCode.contains(query, ignoreCase = true)
        }
        if (!loading && sections.isNotEmpty() && filteredSections.isEmpty()) item { EmptyCard("ما لقينا شعبة مطابقة", "ابحث باسم الصف أو رمز الشعبة A–F.") }
        itemsIndexed(filteredSections, key = { _, section -> section.id }) { _, section ->
            var updating by remember(section.id) { mutableStateOf(false) }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(translated("${section.gradeName} — شعبة ${section.sectionCode}"), color = Ink, fontWeight = FontWeight.Bold)
                        Text(if (section.isOpen) "مفتوحة للمستخدمين" else "مغلقة ومخفية", color = if (section.isOpen) Color(0xFF176B45) else Muted, fontSize = 12.sp)
                    }
                    Switch(checked = section.isOpen, enabled = !updating, onCheckedChange = { next ->
                        scope.launch {
                            updating = true
                            try {
                                backend.setManagedSectionOpen(session, section.id, next)
                                sections = backend.loadManagedSections(session)
                                error = null
                            } catch (e: Exception) { error = e.message ?: "تعذر تغيير حالة الشعبة." }
                            finally { updating = false }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun OwnerAccountManagementScreen(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend
) {
    var accounts by remember { mutableStateOf<List<SupabaseBackend.ManagedProfile>>(emptyList()) }
    var accountSearch by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var newFullName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("student") }
    var newClassName by remember { mutableStateOf("") }
    var newRoleMenu by remember { mutableStateOf(false) }
    var showCreateAccountForm by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            isLoading = true
            error = null
            try {
                accounts = backend.loadManagedProfiles(session)
            } catch (e: Exception) {
                error = e.message ?: "تعذر تحميل الحسابات."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(session.userId) { reload() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("إدارة الحسابات", "لوحة موحّدة لإدارة المستخدمين والأدوار والشعب.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("كل الحسابات" to accounts.size, "طلاب" to accounts.count { it.role == "student" }, "موظفون" to accounts.count { it.role != "student" && it.role != "owner" }).forEach { (label, count) ->
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(count.toString(), color = Blue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = accountSearch,
                onValueChange = { accountSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(translated("بحث بالاسم أو البريد أو الدور")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(translated("إنشاء حساب جديد"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCreateAccountForm) "أدخل بيانات الحساب الجديد" else "إنشاء حساب طالب أو موظف بصلاحيات محددة", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showCreateAccountForm = !showCreateAccountForm }) {
                            Text(if (showCreateAccountForm) "إغلاق ▲" else "إضافة +")
                        }
                    }
                    if (showCreateAccountForm) {
                    Text(translated("ينشئ حساب دخول حقيقياً. لا يمكن إنشاء مالك آخر، وكلمة المرور لا تُعرض ضمن قائمة الحسابات."), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    OutlinedTextField(newFullName, { newFullName = it }, label = { Text(translated("اسم الطالب أو الموظف")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newEmail, { newEmail = it }, label = { Text(translated("البريد الإلكتروني")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text(translated("كلمة مرور مؤقتة (8 أحرف على الأقل)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Box {
                        OutlinedButton(onClick = { newRoleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(translated("نوع الحساب: ") + (managedRoleOptions.firstOrNull { it.value == newRole }?.label ?: newRole))
                        }
                        DropdownMenu(expanded = newRoleMenu, onDismissRequest = { newRoleMenu = false }) {
                            managedRoleOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { newRole = option.value; newRoleMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        newClassName, { newClassName = it.uppercase().filter { ch -> ch in 'A'..'F' || ch.isWhitespace() || ch == '-' } },
                        label = { Text(translated("الشعبة (A–F)، إلزامية للطالب")) },
                        supportingText = { Text(translated("اكتب A أو B أو C أو D أو E أو F؛ يمكن تركها فارغة للموظفين.")) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                creating = true; error = null; notice = null
                                try {
                                    backend.createManagedAccount(session, newFullName, newEmail, newPassword, newRole, newClassName)
                                    notice = "تم إنشاء الحساب. احفظ البريد وكلمة المرور المؤقتة وأرسلها لصاحب الحساب بأمان."
                                    newFullName = ""; newEmail = ""; newPassword = ""; newRole = "student"; newClassName = ""
                                    accounts = backend.loadManagedProfiles(session)
                                    showCreateAccountForm = false
                                } catch (e: Exception) {
                                    error = e.message ?: "تعذر إنشاء الحساب."
                                } finally { creating = false }
                            }
                        },
                        enabled = !creating &&
                            newFullName.trim().length in 2..100 &&
                            newEmail.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) &&
                            newPassword.length in 8..72 &&
                            (newRole != "student" || newClassName.trim().uppercase() in setOf("A", "B", "C", "D", "E", "F")),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (creating) "جارٍ إنشاء الحساب..." else "إنشاء الحساب") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "جارٍ التحديث..." else "تحديث قائمة الحسابات")
            }
        }
        if (error != null) item { EmptyCard("تعذر تحميل الحسابات", error!!) }
        if (notice != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F7EF))) {
                Text(notice!!, modifier = Modifier.padding(14.dp), color = Color(0xFF176B45), fontSize = 13.sp)
            }
        }
        if (!isLoading && error == null && accounts.isEmpty()) {
            item { EmptyCard("ماكو حسابات إضافية", "تظهر الحسابات هنا بعد تسجيلها في Supabase Auth.") }
        }
        val filteredAccounts = accounts.filter { account ->
            val query = accountSearch.trim()
            query.isEmpty() || account.fullName.contains(query, ignoreCase = true) ||
                account.email.orEmpty().contains(query, ignoreCase = true) ||
                (managedRoleOptions.firstOrNull { it.value == account.role }?.label ?: account.role).contains(query, ignoreCase = true) ||
                account.className.orEmpty().contains(query, ignoreCase = true)
        }
        if (!isLoading && error == null && accounts.isNotEmpty() && filteredAccounts.isEmpty()) {
            item { EmptyCard("ما لقينا حساب مطابق", "جرّب الاسم أو البريد أو الدور أو الشعبة.") }
        }
        itemsIndexed(filteredAccounts, key = { _, account -> account.id }) { _, account ->
            var selectedRole by remember(account.id, account.role) { mutableStateOf(account.role) }
            var className by remember(account.id, account.className) { mutableStateOf(account.className.orEmpty()) }
            var menuExpanded by remember { mutableStateOf(false) }
            var saving by remember(account.id) { mutableStateOf(false) }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(account.fullName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(account.email ?: account.id, color = Muted, fontSize = 11.sp)
                    Text(translated("الدور الحالي: ") + (managedRoleOptions.firstOrNull { it.value == account.role }?.label ?: account.role), color = Blue, fontSize = 12.sp)
                    if (account.role == "owner") {
                        Text(translated("حساب المالك محمي ولا يمكن تغيير دوره من هذه اللوحة."), color = Muted, fontSize = 12.sp)
                    } else {
                        Box {
                            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(translated("الدور الجديد: ") + (managedRoleOptions.firstOrNull { it.value == selectedRole }?.label ?: selectedRole))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                managedRoleOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { selectedRole = option.value; menuExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = className,
                            onValueChange = { className = it },
                            label = { Text(translated("الصف والشعبة (مثال: الثالث متوسط - A)")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = null
                                    notice = null
                                    try {
                                        backend.setManagedProfileRole(session, account.id, selectedRole, className)
                                        notice = "تم تحديث الحساب بنجاح."
                                        accounts = backend.loadManagedProfiles(session)
                                    } catch (e: Exception) {
                                        error = e.message ?: "تعذر حفظ صلاحيات الحساب."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (saving) "جارٍ الحفظ..." else "حفظ الدور والشعبة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    role: SchoolRole, homeworkCount: Int, prepCount: Int, scheduleCount: Int,
    remoteItems: List<SupabaseBackend.SchoolItem>, feedError: String?, isEnglish: Boolean = false,
    onNavigate: (Int) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showAllStats by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("all") }
    val filteredItems = remember(remoteItems, search, selectedType) {
        remoteItems.filter { item ->
            val matchesSearch = search.isBlank() || item.title.contains(search, ignoreCase = true) ||
                item.body.contains(search, ignoreCase = true) ||
                (item.subjectName?.contains(search, ignoreCase = true) == true)
            val matchesType = when (selectedType) {
                "all" -> true
                "announcement" -> item.itemType !in listOf("homework", "preparation", "timetable")
                else -> item.itemType.equals(selectedType, ignoreCase = true)
            }
            matchesSearch && matchesType
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ruvenText(translated("هلا بيك 👋"), "Welcome 👋", isEnglish), color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text(ruvenText(translated("خلّ يومك الدراسي أوضح وأسهل."), "Make your school day clearer and easier.", isEnglish), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Surface(color = Color(0xFFE5F1FF), shape = RoundedCornerShape(50)) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFF20A879)))
                            Text(translated(roleLabel(role)), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(
                    Modifier.size(62.dp).clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF173D85), Blue, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.School, contentDescription = "RUVEN", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF112B54), Color(0xFF245DC4), Color(0xFF25A8C7))))
                        .padding(21.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.White.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
                                Text(ruvenText(translated("RUVEN • مدرستك بذكاء"), "RUVEN • Your school, smarter", isEnglish), color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                            }
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFFE8A3), modifier = Modifier.size(25.dp))
                        }
                        Text(translated("كل شي يخص دراستك،\nبمكان واحد."), color = Color.White, fontSize = 27.sp,
                            lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
                        Text(ruvenText(translated("تابع الواجبات، افتح الجدول، وابقَ على اطلاع بآخر إعلانات المدرسة."), "Track homework, check the timetable, and stay up to date with school announcements.", isEnglish), color = Color.White.copy(alpha = .88f),
                            fontSize = 13.sp, lineHeight = 21.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Button(
                                onClick = { onNavigate(2) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF173D85)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(ruvenText(translated("شوف واجباتك"), "View homework", isEnglish), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onNavigate(1) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .65f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(translated("الجدول"))
                            }
                        }
                    }
                }
            }
        }
        if (role == SchoolRole.STUDENT) {
            item { StudentQuickPlannerCard(isEnglish = isEnglish) }
            item { StudentFeatureHubCard(isEnglish = isEnglish) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(ruvenText(translated("اختصارات سريعة"), "Quick shortcuts", isEnglish), ruvenText(translated("اختَر شنو تحتاج هسه"), "Choose what you need", isEnglish))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(ruvenText(translated("الجدول"), "Timetable", isEnglish), ruvenText(translated("حصصك ومواعيدك"), "Classes and times", isEnglish), Icons.Rounded.CalendarMonth, Color(0xFFE7F0FF), Blue, Modifier.weight(1f)) { onNavigate(1) }
                    QuickActionCard(ruvenText(translated("الواجبات"), "Homework", isEnglish), ruvenText(translated("لا يفوتك شي"), "Stay on top of tasks", isEnglish), Icons.AutoMirrored.Rounded.MenuBook, Color(0xFFFFF0DD), Color(0xFFB66A12), Modifier.weight(1f)) { onNavigate(2) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(ruvenText(translated("التحاضير"), "Preparations", isEnglish), ruvenText(translated("محتوى الدروس"), "Lesson content", isEnglish), Icons.Rounded.Description, Color(0xFFE5F8F1), Color(0xFF168A70), Modifier.weight(1f)) { onNavigate(3) }
                    QuickActionCard(ruvenText(translated("الإعلانات"), "Announcements", isEnglish), ruvenText(translated("آخر المستجدات"), "Latest updates", isEnglish), Icons.AutoMirrored.Rounded.Announcement, Color(0xFFF0E9FF), Color(0xFF7651C9), Modifier.weight(1f)) { onNavigate(4) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(ruvenText(translated("ملخصك الدراسي"), "Your study summary", isEnglish), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(ruvenText(translated("أعداد العناصر المنشورة على الخادم"), "Locally saved item counts", isEnglish), color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showAllStats = !showAllStats }) {
                        Text(if (showAllStats) ruvenText(translated("عرض أقل"), "Show less", isEnglish) else ruvenText(translated("تفاصيل"), "Details", isEnglish), fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(ruvenText(translated("الجدول"), "Timetable", isEnglish), scheduleCount.toString(), ruvenText(translated("جداول"), "timetables", isEnglish), Icons.Rounded.CalendarMonth, Modifier.weight(1f))
                    SummaryCard(ruvenText(translated("الواجبات"), "Homework", isEnglish), homeworkCount.toString(), ruvenText(translated("واجبات"), "tasks", isEnglish), Icons.AutoMirrored.Rounded.MenuBook, Modifier.weight(1f))
                    SummaryCard(ruvenText(translated("التحاضير"), "Preparations", isEnglish), prepCount.toString(), ruvenText(translated("تحاضير"), "preparations", isEnglish), Icons.Rounded.Description, Modifier.weight(1f))
                }
                androidx.compose.animation.AnimatedVisibility(visible = showAllStats) {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ruvenText(translated("تفاصيل المساحة"), "Workspace details", isEnglish), color = Ink, fontWeight = FontWeight.Bold)
                            Text(translated("منشورات الخادم: ${remoteItems.size}"), color = Muted, fontSize = 12.sp)
                            Text(translated("الواجبات المنشورة: $homeworkCount"), color = Muted, fontSize = 12.sp)
                            Text(translated("التحاضير المنشورة: $prepCount"), color = Muted, fontSize = 12.sp)
                            Text(translated("الجداول المنشورة: $scheduleCount"), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(ruvenText(translated("آخر منشورات المدرسة"), "Latest school posts", isEnglish), ruvenText(translated("واجبات وتحاضير وإعلانات منشورة من الخادم"), "Homework, preparations and announcements from the server", isEnglish))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "الكل",
                        "homework" to "الواجبات",
                        "preparation" to "التحاضير",
                        "timetable" to "الجداول",
                        "announcement" to "الإعلانات"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(ruvenText(translated("ابحث باسم الواجب أو المادة…"), "Search homework or subject…", isEnglish)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                    trailingIcon = {
                        if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = ruvenText(translated("مسح البحث"), "Clear search", isEnglish))
                        }
                    }
                )
            }
        }
        if (feedError != null) {
            item { EmptyCard("تعذر تحميل المحتوى", feedError) }
        } else if (filteredItems.isEmpty()) {
            item {
                EmptyCard(
                    if (search.isNotBlank()) "ما لقينا نتيجة" else "ماكو منشورات بعد",
                    if (search.isNotBlank()) "جرّب كلمة ثانية أو امسح البحث." else "من تنشر المدرسة واجباً أو إعلاناً، راح يظهر هنا."
                )
            }
        } else {
            itemsIndexed(filteredItems) { index, item ->
                ContentCard(
                    title = item.title + (item.subjectName?.let { " • $it" } ?: ""),
                    subtitle = item.itemType + (item.body.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                    icon = when (item.itemType) {
                        "homework" -> Icons.AutoMirrored.Rounded.MenuBook
                        "preparation" -> Icons.Rounded.Description
                        "timetable" -> Icons.Rounded.CalendarMonth
                        else -> Icons.AutoMirrored.Rounded.Announcement
                    }
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE7F0FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Blue)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ruvenText(translated("مساحتك وصلاحياتك"), "Your space and permissions", isEnglish), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(roleLabel(role), color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(when (role) {
                            SchoolRole.STUDENT -> "تقدر تتابع الجدول والواجبات المنشورة لشعبتك."
                            SchoolRole.CLASS_MONITOR -> "تابع شؤون الشعبة وبلّغ الإدارة عند الحاجة."
                            SchoolRole.PARLIAMENT_MEMBER -> "مثّل الطلبة وارفع المقترحات ضمن صلاحياتك."
                            SchoolRole.TEACHER -> "تابع التحاضير والواجبات ضمن موادك المسندة."
                            SchoolRole.CLASS_SUPERVISOR -> "تابع الشعبة المكلّف بها."
                            SchoolRole.ASSISTANT_PRINCIPAL -> "تابع المحتوى المدرسي حسب الصلاحيات."
                            SchoolRole.PRINCIPAL -> "إشراف إداري على شؤون المدرسة."
                            SchoolRole.EDUCATIONAL_COUNSELOR -> "متابعة الشؤون التربوية مع مراعاة الخصوصية."
                            SchoolRole.MINISTRY_EDUCATION_MEMBER -> "دور رقابي تربوي ضمن الصلاحيات المعتمدة."
                            SchoolRole.OWNER -> "إدارة النظام والحسابات والإعدادات."
                        }, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        item {
            Text(ruvenText(translated("RUVEN • نسخة قيد التطوير"), "RUVEN • Development build", isEnglish), color = Muted, fontSize = 10.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String, subtitle: String, icon: ImageVector, tint: Color, iconTint: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun localizeSchoolTitle(title: String, english: Boolean): String = if (!english) title else when (title) {
    "الجدول الدراسي" -> "Timetable"
    "الواجبات" -> "Homework"
    "التحاضير" -> "Preparations"
    "الإعلانات المدرسية" -> "School announcements"
    else -> title
}

@Composable
private fun ServerSchoolItemsScreen(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend,
    remoteItems: List<SupabaseBackend.SchoolItem>,
    itemType: String,
    title: String,
    description: String,
    role: SchoolRole,
    profileClass: String?,
    onRefresh: () -> Unit,
    isEnglish: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedAttachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedAttachment = uri
        attachmentName = uri?.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
    }
    var search by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var targetClass by remember { mutableStateOf(profileClass.orEmpty()) }
    var entryTitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var composerOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val needsSubject = itemType == "homework" || itemType == "preparation"
    val needsClass = needsSubject || itemType == "timetable"
    val canCreate = when (itemType) {
        "timetable" -> role in setOf(SchoolRole.ASSISTANT_PRINCIPAL, SchoolRole.PRINCIPAL, SchoolRole.OWNER)
        "homework" -> SchoolPermissions.canCreateHomework(role, assignedSubject = subject.isNotBlank())
        "preparation" -> role in setOf(SchoolRole.ASSISTANT_PRINCIPAL, SchoolRole.PRINCIPAL, SchoolRole.OWNER) || SchoolPermissions.canCreatePreparation(role, assignedSubject = subject.isNotBlank())
        "announcement" -> SchoolPermissions.canPublishAnnouncements(role)
        else -> false
    }
    val visibleItems = remoteItems.filter { it.itemType == itemType }.filter {
        search.isBlank() || it.title.contains(search, true) || it.body.contains(search, true) ||
            (it.subjectName?.contains(search, true) == true) || (it.className?.contains(search, true) == true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader(localizeSchoolTitle(title, isEnglish), description) }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(translated("تحديث من خادم المدرسة"))
            }
        }
        if (canCreate) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(translated("إضافة محتوى رسمي"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(translated("سيُرسل إلى خادم المدرسة، وتطبّق صلاحيات قاعدة البيانات."), color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { composerOpen = !composerOpen }) { Text(translated(if (composerOpen) "إغلاق" else "إنشاء")) }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = composerOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            if (needsSubject) OutlinedTextField(subject, { subject = it }, label = { Text(translated("المادة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (needsClass) OutlinedTextField(targetClass, { targetClass = it.trim().uppercase() }, label = { Text(translated("الشعبة / الصف")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(entryTitle, { entryTitle = it }, label = { Text(translated("العنوان")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(body, { body = it }, label = { Text(translated("التفاصيل")) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
                            OutlinedButton(onClick = { attachmentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(attachmentName ?: translated("إرفاق ملف من الجهاز (اختياري)"))
                            }
                            Button(
                                enabled = !busy && canCreate && entryTitle.isNotBlank() && body.isNotBlank() &&
                                    (!needsSubject || subject.isNotBlank()) && (!needsClass || targetClass.isNotBlank()),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        message = null
                                        try {
                                            var uploadedPath: String? = null
                                            try {
                                                uploadedPath = selectedAttachment?.let { uri ->
                                                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                                    val allowedTypes = setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                                    require(mime in allowedTypes) { "نوع الملف غير مدعوم. استخدم PDF أو JPG أو PNG أو WEBP أو DOCX أو XLSX." }
                                                    val metadata = withContext(Dispatchers.IO) {
                                                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                                                            if (cursor.moveToFirst()) {
                                                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                                                (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L)
                                                            } else null
                                                        }
                                                    }
                                                    val actualName = metadata?.first?.takeIf { it.isNotBlank() } ?: attachmentName ?: "school-file"
                                                    val declaredSize = metadata?.second ?: -1L
                                                    require(declaredSize <= 20L * 1024 * 1024) { "حجم الملف يجب ألا يتجاوز 20 ميغابايت." }
                                                    val bytes = withContext(Dispatchers.IO) {
                                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                                            val output = java.io.ByteArrayOutputStream()
                                                            val buffer = ByteArray(8192)
                                                            var total = 0
                                                            while (true) {
                                                                val count = input.read(buffer)
                                                                if (count < 0) break
                                                                total += count
                                                                require(total <= 20 * 1024 * 1024) { "حجم الملف يجب ألا يتجاوز 20 ميغابايت." }
                                                                output.write(buffer, 0, count)
                                                            }
                                                            output.toByteArray()
                                                        } ?: throw java.io.IOException("تعذر قراءة الملف المحدد.")
                                                    }
                                                    backend.uploadSchoolFile(session, actualName, mime, bytes)
                                                }
                                                backend.createSchoolItem(
                                                    session = session,
                                                    itemType = itemType,
                                                    title = entryTitle.trim(),
                                                    body = body.trim(),
                                                    subjectName = subject.takeIf { needsSubject },
                                                    className = if (needsClass) targetClass.trim().uppercase() else null,
                                                    published = true,
                                                    attachmentPath = uploadedPath
                                                )
                                                uploadedPath = null
                                            } catch (failure: Exception) {
                                                uploadedPath?.let { path -> runCatching { backend.deleteSchoolFile(session, path) } }
                                                throw failure
                                            }
                                            entryTitle = ""; body = ""; subject = ""; selectedAttachment = null; attachmentName = null
                                            message = "تم إرسال المحتوى إلى الخادم."
                                            composerOpen = false
                                            onRefresh()
                                        } catch (e: Exception) {
                                            message = e.message ?: "تعذر نشر المحتوى. تحقّق من الصلاحيات والاتصال."
                                        } finally { busy = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(translated(if (busy) "جارٍ الإرسال…" else "نشر على خادم المدرسة"))
                            }
                        }
                    }
                }
            }
        }
        if (message != null) item { Text(message!!, color = if (message!!.startsWith("تم")) Color(0xFF16845B) else MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(translated("بحث")) },
                placeholder = { Text(translated("ابحث بالعنوان أو المادة أو الشعبة")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )
        }
        if (visibleItems.isEmpty()) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Muted, modifier = Modifier.size(28.dp))
                    Text(translated(if (search.isBlank()) "ماكو محتوى منشور بهذا القسم حالياً." else "ماكو نتائج مطابقة للبحث."), color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(translated("اسحب تحديث المحتوى من الخادم للتأكد من آخر البيانات."), color = Muted, fontSize = 11.sp)
                }
            }
        }
        items(visibleItems, key = { it.id }) { item ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(item.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (!item.subjectName.isNullOrBlank() || !item.className.isNullOrBlank()) {
                        Text(listOfNotNull(item.subjectName, item.className?.let { "${translated("الشعبة: ")}$it" }).joinToString(" • "), color = Blue, fontSize = 11.sp)
                    }
                    Text(item.body, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    if (!item.attachmentPath.isNullOrBlank()) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val url = backend.createSchoolFileSignedUrl(session, item.attachmentPath)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: Exception) { message = e.message ?: "تعذر فتح الملف المرفق." }
                            }
                        }) { Icon(Icons.Rounded.Download, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(translated("فتح / تنزيل الملف المرفق")) }
                    }
                    Text(item.createdAt.replace("T", " ").take(16), color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(role: SchoolRole, schedules: MutableList<String>, scheduleFiles: MutableList<String>) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showComposer by remember { mutableStateOf(true) }
    val filteredSchedules = schedules.withIndex().filter { (_, entry) ->
        search.isBlank() || entry.contains(search, ignoreCase = true)
    }
    var fileName by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val canManage = SchoolPermissions.canManageTimetable(role)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        fileName = uri?.lastPathSegment?.substringAfterLast('/')
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageHeader("الجدول الدراسي", "ارفع الجدول كـ PDF أو صورة، أو اكتبه مباشرة.") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(translated("إضافة جدول"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(translated("للمخولين بإدارة الجدول"), color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showComposer = !showComposer }) {
                            Text(if (showComposer) "إخفاء" else "إضافة")
                        }
                    }
                    if (canManage && showComposer) {
                        OutlinedTextField(title, { title = it }, label = { Text(translated("عنوان الجدول")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(details, { details = it }, label = { Text(translated("الجدول كتابةً (اختياري)")) }, placeholder = { Text(translated("الأحد: رياضيات، عربي، كيمياء...")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null); Spacer(Modifier.width(7.dp)); Text(translated("اختيار PDF أو صورة"))
                        }
                        if (fileName != null) Text(translated("الملف المختار: ") + fileName, color = Color(0xFF168A70), fontSize = 12.sp)
                        Button(onClick = {
                            val entry = (title.ifBlank { "جدول دراسي" }) +
                                (if (details.isBlank()) "" else " — " + details) +
                                (if (fileName == null) "" else " — ملف: " + fileName)
                            if (title.isNotBlank() || details.isNotBlank() || selectedUri != null) {
                                schedules.add(entry)
                                scheduleFiles.add(selectedUri?.toString().orEmpty())
                                title = ""; details = ""; fileName = null; selectedUri = null
                            }
                        }, enabled = title.isNotBlank() || details.isNotBlank() || selectedUri != null, modifier = Modifier.fillMaxWidth()) {
                            Text(translated("إضافة للمعاينة"))
                        }
                        Text(translated("حالياً نحفظ وصفاً محلياً فقط؛ الملف نفسه لا يُرفع لخادم لأن الخادم لم يُبنَ بعد."), color = Muted, fontSize = 11.sp)
                    } else Text(translated("هذه الميزة متاحة لمراقب الشعبة والمدير والمعاونين."), color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(translated("الجداول المضافة"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(translated("${schedules.size} جدول محفوظ محلياً"), color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFEAF2FF), shape = RoundedCornerShape(50)) {
                    Text(translated("الجدول"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(translated("ابحث في الجداول…")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                } }
            )
        }
        if (filteredSchedules.isEmpty()) item {
            EmptyCard(if (search.isBlank()) "ماكو جدول مضاف" else "ما لقينا جدول مطابق",
                if (search.isBlank()) "أضف جدولاً تجريبياً حتى يظهر هنا." else "جرّب كلمة ثانية أو امسح البحث.")
        }
        else itemsIndexed(filteredSchedules, key = { _, indexed -> indexed.index }) { _, indexed ->
            val i = indexed.index
            val entry = indexed.value
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp)
                    Text(translated("جدول محلي ") + (i + 1), color = Muted, fontSize = 11.sp)
                    val fileUri = scheduleFiles.getOrNull(i)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                    if (fileUri != null) {
                        TextButton(onClick = {
                            val mime = context.contentResolver.getType(fileUri) ?: "*/*"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(fileUri, mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }) { Text(translated("فتح الملف المرفق")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkScreen(role: SchoolRole, homework: MutableList<String>, reports: MutableList<String>) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showHomeworkComposer by remember { mutableStateOf(true) }
    val filteredHomework = homework.withIndex().filter { (_, entry) ->
        search.isBlank() || entry.contains(search, ignoreCase = true)
    }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingText by remember { mutableStateOf("") }
    val canCreate = SchoolPermissions.canCreateHomework(role, assignedSubject = role == SchoolRole.TEACHER && subject.isNotBlank())
    val canSeeHomeworkForm = role != SchoolRole.STUDENT
    val canModerate = SchoolPermissions.canEditOrDeleteHomework(role)
    val canReport = SchoolPermissions.canReportToPrincipal(role)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageHeader("الواجبات", "نشر حسب المادة وصلاحيات الإدارة.") }
        if (canSeeHomeworkForm) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(translated("إضافة واجب"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(translated("أنشئ الواجب ثم راجعه قبل النشر"), color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showHomeworkComposer = !showHomeworkComposer }) { Text(if (showHomeworkComposer) "طيّ النموذج" else "إضافة واجب") }
                    }
                    if (showHomeworkComposer) {
                    OutlinedTextField(subject, { subject = it }, label = { Text(translated("المادة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (role == SchoolRole.TEACHER) Text(translated("بالنسخة النهائية، الخادم يتحقق أن المادة مسندة لهذا المدرس."), color = Muted, fontSize = 11.sp)
                    OutlinedTextField(title, { title = it }, label = { Text(translated("عنوان الواجب")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(details, { details = it }, label = { Text(translated("التفاصيل")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = {
                        homework.add(subject + ": " + title + if (details.isBlank()) "" else " — " + details)
                        subject = ""; title = ""; details = ""; showHomeworkComposer = false
                    }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(translated("حفظ الواجب في المعاينة")) }
                    }
                }
            }
        } else item { EmptyCard("عرض الواجبات", "المدرس ينشر لمادته، والإدارة تدير المحتوى حسب الصلاحيات.") }
        if (canReport) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(translated("متابعة الواجبات"), color = Ink, fontWeight = FontWeight.Bold)
                    Text(translated("مراقب الشعبة يرسل بلاغاً للمدير إذا احتاج واجب إلى تعديل أو حذف، ولا يحذفه بنفسه."), color = Muted, fontSize = 12.sp)
                    Button(onClick = { reports.add("بلاغ من مراقب الشعبة: مراجعة الواجبات المنشورة") }, modifier = Modifier.fillMaxWidth()) { Text(translated("إرسال بلاغ للمدير")) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(translated("قائمة الواجبات"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(translated("${homework.size} واجب محفوظ محلياً"), color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFFFF0DD), shape = RoundedCornerShape(50)) {
                    Text(translated("واجبات"), color = Color(0xFFB66A12), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(translated("ابحث عن واجب أو مادة…")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                } }
            )
        }
        if (filteredHomework.isEmpty()) item {
            EmptyCard(if (search.isBlank()) "ماكو واجبات مضافة" else "ما لقينا واجب مطابق",
                if (search.isBlank()) "الواجبات تظهر هنا بعد إضافة بيانات تجريبية أو ربط الخادم." else "غيّر البحث أو امسحه حتى تشوف كل الواجبات.")
        }
        else itemsIndexed(filteredHomework, key = { _, indexed -> indexed.index }) { _, indexed ->
            val i = indexed.index
            val entry = indexed.value
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(entry, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(translated("واجب تجريبي ") + (i + 1), color = Muted, fontSize = 11.sp)
                    if (canModerate) {
                        if (editingIndex == i) {
                            OutlinedTextField(editingText, { editingText = it }, label = { Text(translated("تعديل الواجب")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingIndex = -1; editingText = "" }) { Text(translated("إلغاء")) }
                                TextButton(onClick = {
                                    if (editingText.isNotBlank()) homework[i] = editingText.trim()
                                    editingIndex = -1; editingText = ""
                                }) { Text(translated("حفظ التعديل")) }
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingIndex = i; editingText = entry }) { Text(translated("تعديل")) }
                            TextButton(onClick = { homework.removeAt(i); if (editingIndex == i) editingIndex = -1 }) { Text(translated("حذف")) }
                        }
                    } else if (canReport) TextButton(onClick = { reports.add("بلاغ من مراقب الشعبة عن: " + entry) }) { Text(translated("إبلاغ المدير")) }
                }
            }
        }
        if (reports.isNotEmpty()) {
            item { SectionHeading("البلاغات الإدارية", "محفوظة على هذا الجهاز") }
            itemsIndexed(reports) { i, report -> ContentCard(report, "بلاغ " + (i + 1), Icons.Rounded.Description) }
        }
        item { Text(translated("ملاحظة: قائمة الواجبات والبلاغات في هذه الشاشة تجريبية ومحلية حالياً؛ لا تُرسل إلى بقية المستخدمين."), color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
    }
}

@Composable
private fun PreparationsScreen(role: SchoolRole, preparations: MutableList<String>) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showPreparationComposer by remember { mutableStateOf(true) }
    val filteredPreparations = preparations.withIndex().filter { (_, entry) ->
        search.isBlank() || entry.contains(search, ignoreCase = true)
    }
    val canCreate = SchoolPermissions.canCreatePreparation(role, assignedSubject = subject.isNotBlank())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageHeader("التحاضير", "دفتر رقمي لتحضير الدروس وأهدافها وخطوات شرحها.") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F2FF)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Description, contentDescription = null, tint = Blue)
                            }
                            Column {
                                Text(translated("إضافة تحضير"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(translated("للمدرسين ضمن موادهم المسندة"), color = Muted, fontSize = 11.sp)
                            }
                        }
                        if (role == SchoolRole.TEACHER) TextButton(onClick = { showPreparationComposer = !showPreparationComposer }) { Text(if (showPreparationComposer) "طيّ" else "إضافة") }
                    }
                    if (role == SchoolRole.TEACHER && showPreparationComposer) {
                        OutlinedTextField(subject, { subject = it }, label = { Text(translated("المادة المسندة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(title, { title = it }, label = { Text(translated("عنوان التحضير")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(content, { content = it }, label = { Text(translated("أهداف الدرس وخطوات الشرح والواجب")) }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Button(onClick = {
                            preparations.add(subject + " — " + title + ": " + content)
                            subject = ""; title = ""; content = ""; showPreparationComposer = false
                        }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank() && content.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(translated("حفظ التحضير في المعاينة"))
                        }
                        Text(translated("يُحفظ محلياً في هذه النسخة التجريبية فقط."), color = Muted, fontSize = 11.sp)
                    } else Text(translated("إضافة التحاضير للمدرس فقط؛ والخادم مستقبلاً يتحقق من المادة المسندة إليه."), color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(translated("دفتر التحاضير"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(translated("${preparations.size} تحضير محفوظ محلياً"), color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFE5F8F1), shape = RoundedCornerShape(50)) {
                    Text(translated("التحاضير"), color = Color(0xFF168A70), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(translated("ابحث باسم التحضير أو المادة…")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                } }
            )
        }
        if (filteredPreparations.isEmpty()) item {
            EmptyCard(if (search.isBlank()) "دفتر التحاضير فارغ" else "ما لقينا تحضير مطابق",
                if (search.isBlank()) "تظهر هنا التحاضير التي أُضيفت في المعاينة." else "غيّر البحث أو امسحه حتى تشوف كل التحاضير.")
        } else itemsIndexed(filteredPreparations, key = { _, indexed -> indexed.index }) { _, indexed ->
            ContentCard(indexed.value, "تحضير " + (indexed.index + 1), Icons.Rounded.Description)
        }
    }
}

@Composable
private fun NoticesScreen(role: SchoolRole, announcements: MutableList<String>) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showComposer by remember { mutableStateOf(false) }
    val canPublish = SchoolPermissions.canPublishAnnouncements(role)
    val filteredAnnouncements = announcements.withIndex().filter { (_, announcement) ->
        search.isBlank() || announcement.contains(search, ignoreCase = true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { PageHeader("الإعلانات المدرسية", "تابع التوجيهات والتنبيهات المهمة. هذه الإعلانات محلية ولا تصل إلى أجهزة الآخرين.") }
        if (canPublish) item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Blue, Cyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(translated("لوحة إنشاء إعلان"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(translated("اكتب عنواناً واضحاً ورسالة مختصرة"), color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showComposer = !showComposer }) {
                            Text(if (showComposer) "إغلاق" else "إنشاء")
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = showComposer) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text(translated("عنوان الإعلان")) },
                                placeholder = { Text(translated("مثال: تغيير موعد الامتحان")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(15.dp)
                            )
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                label = { Text(translated("نص الإعلان")) },
                                placeholder = { Text(translated("اكتب التفاصيل المهمة هنا…")) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                shape = RoundedCornerShape(15.dp)
                            )
                            Button(
                                onClick = {
                                    announcements.add(0, title.trim() + "\n" + body.trim())
                                    title = ""
                                    body = ""
                                    showComposer = false
                                },
                                enabled = title.isNotBlank() && body.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text(translated("نشر في المعاينة"))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(translated("لوحة الإعلانات"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(translated("${announcements.size} إعلان محفوظ على هذا الجهاز"), color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFEAF2FF), shape = RoundedCornerShape(50)) {
                    Text(translated("محلي"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(translated("ابحث في الإعلانات…")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                trailingIcon = {
                    if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                    }
                }
            )
        }
        if (filteredAnnouncements.isEmpty()) item {
            EmptyCard(
                if (search.isBlank()) "ماكو إعلانات حالياً" else "ما لقينا إعلان مطابق",
                if (search.isBlank()) {
                    if (canPublish) "اضغط «إنشاء» حتى تضيف إعلاناً تجريبياً." else "ستظهر الإعلانات بعد ربط خادم المدرسة ونشرها."
                } else "غيّر كلمات البحث أو امسحها حتى تشوف كل الإعلانات."
            )
        } else {
            itemsIndexed(filteredAnnouncements, key = { _, indexed -> indexed.index }) { _, indexed ->
                val index = indexed.index
                val announcement = indexed.value
                var expanded by remember(index, announcement) { mutableStateOf(false) }
                Card(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(21.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 3.dp else 1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
                                    .background(Color(0xFFF0E9FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Announcement, contentDescription = null, tint = Color(0xFF7651C9))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(translated("إعلان مدرسي ${announcements.size - index}"), color = Color(0xFF7651C9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(announcement.substringBefore("\n"), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                            }
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "طي الإعلان" else "عرض الإعلان",
                                tint = Muted
                            )
                        }
                        Text(
                            announcement.substringAfter("\n", ""),
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 2
                        )
                        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Divider(color = Color(0xFFE8EDF5))
                                Text(translated("نص الإعلان الكامل"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                if (canPublish) {
                                    TextButton(
                                        onClick = { announcements.removeAt(index) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(translated("حذف الإعلان"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "ملاحظة: النشر الحالي تجريبي ومحلي؛ لا تعتبره إعلاناً رسمياً حتى يُربط بخادم المدرسة.",
                color = Muted, fontSize = 10.sp, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color.White, Color(0xFFE6FAFC))))
                .padding(horizontal = 17.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(49.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Blue, Cyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(translated(title), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(translated(subtitle), color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(translated(title), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(translated(subtitle), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryCard(title: String, value: String, caption: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(19.dp))
            }
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ContentCard(title: String, subtitle: String, icon: ImageVector) {
    var expanded by remember(title, subtitle) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 3.dp else 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFE3F9FA)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 21.sp)
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2
                )
                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(translated("اضغط للطي"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!expanded) {
                    Text(translated("اضغط لعرض التفاصيل"), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "طي التفاصيل" else "عرض التفاصيل",
                tint = Muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = Blue, modifier = Modifier.size(27.dp))
            }
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
    }
}
