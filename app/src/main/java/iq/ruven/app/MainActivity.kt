package iq.ruven.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Announcement
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
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
        setContent { RuvenTheme { RuvenApp() } }
    }
}

@Composable
private fun RuvenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = Blue, secondary = Cyan, background = Canvas, surface = Color.White,
        onPrimary = Color.White, onBackground = Ink, onSurface = Ink
    ), content = content)
}

private enum class RuvenTab(val title: String) {
    Home("الرئيسية"), Schedule("الجدول"), Homework("الواجبات"),
    Preparations("التحاضير"), Notices("الإعلانات"), Admin("الحسابات")
}

@Composable
private fun RuvenApp() {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var session by remember { mutableStateOf<SupabaseBackend.Session?>(null) }
    var profile by remember { mutableStateOf<SupabaseBackend.Profile?>(null) }
    var remoteItems by remember { mutableStateOf<List<SupabaseBackend.SchoolItem>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    val backend = remember { SupabaseBackend() }
    val scope = rememberCoroutineScope()
    if (session == null || profile == null) {
        SupabaseLoginScreen { newSession, newProfile ->
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
                } catch (e: Exception) {
                    feedError = e.message ?: "تعذر تحميل محتوى المدرسة."
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
            onRefresh = {
                scope.launch {
                    try {
                        val loaded = backend.loadPublishedItems(activeSession, activeProfile.className)
                        remoteItems = loaded
                        RuvenNotifications.processSync(context, activeProfile.id, loaded)
                        feedError = null
                    } catch (e: Exception) {
                        feedError = e.message ?: "تعذر تحديث محتوى المدرسة."
                    }
                }
            },
            onSignOut = {
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
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val role = remember(profile.role) { schoolRoleFromDatabase(profile.role) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ruven_local_data", Context.MODE_PRIVATE) }
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                val tabs = buildList {
                    add(RuvenTab.Home to Icons.Rounded.Home)
                    add(RuvenTab.Schedule to Icons.Rounded.CalendarMonth)
                    add(RuvenTab.Homework to Icons.AutoMirrored.Rounded.MenuBook)
                    add(RuvenTab.Preparations to Icons.Rounded.Description)
                    add(RuvenTab.Notices to Icons.AutoMirrored.Rounded.Announcement)
                    if (role == SchoolRole.OWNER) add(RuvenTab.Admin to Icons.Rounded.School)
                }
                tabs.forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(item.second, contentDescription = item.first.title) },
                        label = { Text(item.first.title, fontSize = 10.sp, maxLines = 1) }, alwaysShowLabel = true)
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("RUVEN", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("منظومتك المدرسية", color = Muted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(profile.fullName, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(roleLabel(role), color = Blue, fontSize = 11.sp)
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "تحديث المحتوى والإشعارات", tint = Blue)
                            }
                            TextButton(onClick = onSignOut) { Text("خروج", color = Blue, fontSize = 11.sp) }
                        }
                    }
                }
                Surface(color = Color(0xFFFFF4D8), modifier = Modifier.fillMaxWidth()) {
                    Text("معاينة تجريبية: اختيار الدور لا يمنح صلاحيات حقيقية، والبيانات محلية.",
                        color = Color(0xFF795A16), fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp))
                }
                AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ruven-tab-transition", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        0 -> HomeScreen(role, homework.size, preparations.size, schedules.size, remoteItems, feedError)
                        1 -> ScheduleScreen(role, schedules, scheduleFiles)
                        2 -> HomeworkScreen(role, homework, reports)
                        3 -> PreparationsScreen(role, preparations)
                        4 -> NoticesScreen(role, announcements)
                        else -> if (role == SchoolRole.OWNER) OwnerAccountManagementScreen(session, backend)
                            else NoticesScreen(role, announcements)
                    }
                }
            }
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
private fun OwnerAccountManagementScreen(
    session: SupabaseBackend.Session,
    backend: SupabaseBackend
) {
    var accounts by remember { mutableStateOf<List<SupabaseBackend.ManagedProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var newFullName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("student") }
    var newClassName by remember { mutableStateOf("") }
    var newRoleMenu by remember { mutableStateOf(false) }
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
        item { PageHeader("إدارة الحسابات", "تعيين الأدوار والشعب للحسابات المسجلة في RUVEN.") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إنشاء حساب جديد", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("ينشئ حساب دخول حقيقياً. لا يمكن إنشاء مالك آخر، وكلمة المرور لا تُعرض ضمن قائمة الحسابات.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    OutlinedTextField(newFullName, { newFullName = it }, label = { Text("اسم الطالب أو الموظف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newEmail, { newEmail = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("كلمة مرور مؤقتة (8 أحرف على الأقل)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box {
                        OutlinedButton(onClick = { newRoleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("نوع الحساب: " + (managedRoleOptions.firstOrNull { it.value == newRole }?.label ?: newRole))
                        }
                        DropdownMenu(expanded = newRoleMenu, onDismissRequest = { newRoleMenu = false }) {
                            managedRoleOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { newRole = option.value; newRoleMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        newClassName, { newClassName = it.uppercase().filter { ch -> ch in 'A'..'F' || ch.isWhitespace() || ch == '-' } },
                        label = { Text("الشعبة (A–F)، إلزامية للطالب") },
                        supportingText = { Text("اكتب A أو B أو C أو D أو E أو F؛ يمكن تركها فارغة للموظفين.") },
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
                                } catch (e: Exception) {
                                    error = e.message ?: "تعذر إنشاء الحساب."
                                } finally { creating = false }
                            }
                        },
                        enabled = !creating && newFullName.isNotBlank() && newEmail.isNotBlank() && newPassword.length >= 8 &&
                            (newRole != "student" || newClassName.trim().uppercase() in setOf("A", "B", "C", "D", "E", "F")),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (creating) "جارٍ إنشاء الحساب..." else "إنشاء الحساب") }
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
        itemsIndexed(accounts, key = { _, account -> account.id }) { _, account ->
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
                    Text("الدور الحالي: " + (managedRoleOptions.firstOrNull { it.value == account.role }?.label ?: account.role), color = Blue, fontSize = 12.sp)
                    if (account.role == "owner") {
                        Text("حساب المالك محمي ولا يمكن تغيير دوره من هذه اللوحة.", color = Muted, fontSize = 12.sp)
                    } else {
                        Box {
                            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("الدور الجديد: " + (managedRoleOptions.firstOrNull { it.value == selectedRole }?.label ?: selectedRole))
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
                            label = { Text("الصف والشعبة (مثال: الثالث متوسط - A)") },
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
    remoteItems: List<SupabaseBackend.SchoolItem>, feedError: String?
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("هلا بيك", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("كل تفاصيل يومك الدراسي بمكان واحد.", color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Blue, Cyan))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.School, contentDescription = "RUVEN", tint = Color.White, modifier = Modifier.size(29.dp))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF163C80), Blue, Cyan))).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("مساحتك الدراسية", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
                        Text("دراستك،\nبترتيب أذكى.", color = Color.White, fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                        Text("الجدول والواجبات والتحاضير والإعلانات.", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            SectionHeading("محتوى المدرسة من الخادم", "المنشورات المنشورة في قاعدة بيانات RUVEN")
        }
        if (feedError != null) {
            item { EmptyCard("تعذر تحميل المحتوى", feedError) }
        } else if (remoteItems.isEmpty()) {
            item { EmptyCard("ماكو منشورات منشورة بعد", "تسجيل الدخول نجح، لكن لا توجد عناصر منشورة مرئية لهذا الحساب.") }
        } else {
            itemsIndexed(remoteItems) { index, item ->
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
        item { SectionHeading("ملخص المساحة", "أعداد المعاينة المحلية فقط") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("الجدول", scheduleCount.toString(), "عناصر محلية", Icons.Rounded.CalendarMonth, Modifier.weight(1f))
                SummaryCard("الواجبات", homeworkCount.toString(), "واجبات محلية", Icons.AutoMirrored.Rounded.MenuBook, Modifier.weight(1f))
                SummaryCard("التحاضير", prepCount.toString(), "تحاضير محلية", Icons.Rounded.Description, Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("دورك الحالي", color = Muted, fontSize = 12.sp)
                    Text(roleLabel(role), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(when (role) {
                        SchoolRole.STUDENT -> "عرض الجدول والواجبات والتحاضير المنشورة لشعبتك."
                        SchoolRole.CLASS_MONITOR -> "متابعة شؤون الشعبة وإرسال البلاغات للإدارة."
                        SchoolRole.PARLIAMENT_MEMBER -> "تمثيل الطلبة ورفع المقترحات والبلاغات؛ لا تمنح العضوية صلاحيات إدارية."
                        SchoolRole.TEACHER -> "نشر واجبات وتحاضير المواد المسندة إليك فقط."
                        SchoolRole.CLASS_SUPERVISOR -> "متابعة الشعبة المكلّف بها بعد تحديد نطاق صلاحياتها."
                        SchoolRole.ASSISTANT_PRINCIPAL -> "إدارة المحتوى المدرسي وفق الصلاحيات المعتمدة."
                        SchoolRole.PRINCIPAL -> "الإشراف الإداري على شؤون المدرسة."
                        SchoolRole.EDUCATIONAL_COUNSELOR -> "متابعة الشؤون التربوية ضمن صلاحيات تحمي خصوصية الطلبة."
                        SchoolRole.MINISTRY_EDUCATION_MEMBER -> "دور رقابي تربوي مستقل؛ لا تُمنح صلاحيات الإدارة تلقائياً."
                        SchoolRole.OWNER -> "إدارة النظام والحسابات والإعدادات بصلاحيات المالك."
                    }, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
        item { Text("نسخة تأسيسية • ماكو اتصال بخادم المدرسة بعد", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ScheduleScreen(role: SchoolRole, schedules: MutableList<String>, scheduleFiles: MutableList<String>) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
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
                    Text("إضافة جدول", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("الصلاحية حسب التصميم: مراقب الشعبة والمدير والمعاونون.", color = Muted, fontSize = 12.sp)
                    if (canManage) {
                        OutlinedTextField(title, { title = it }, label = { Text("عنوان الجدول") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(details, { details = it }, label = { Text("الجدول كتابةً (اختياري)") }, placeholder = { Text("الأحد: رياضيات، عربي، كيمياء...") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("اختيار PDF أو صورة")
                        }
                        if (fileName != null) Text("الملف المختار: " + fileName, color = Color(0xFF168A70), fontSize = 12.sp)
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
                            Text("إضافة للمعاينة")
                        }
                        Text("حالياً نحفظ وصفاً محلياً فقط؛ الملف نفسه لا يُرفع لخادم لأن الخادم لم يُبنَ بعد.", color = Muted, fontSize = 11.sp)
                    } else Text("هذه الميزة متاحة لمراقب الشعبة والمدير والمعاونين.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item { SectionHeading("الجداول المضافة", "محفوظة على هذا الجهاز") }
        if (schedules.isEmpty()) item { EmptyCard("ماكو جدول مضاف", "أضف جدولاً تجريبياً حتى يظهر هنا.") }
        else itemsIndexed(schedules) { i, entry ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp)
                    Text("جدول محلي " + (i + 1), color = Muted, fontSize = 11.sp)
                    val fileUri = scheduleFiles.getOrNull(i)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                    if (fileUri != null) {
                        TextButton(onClick = {
                            val mime = context.contentResolver.getType(fileUri) ?: "*/*"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(fileUri, mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }) { Text("فتح الملف المرفق") }
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
                    Text("إضافة واجب", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    OutlinedTextField(subject, { subject = it }, label = { Text("المادة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (role == SchoolRole.TEACHER) Text("بالنسخة النهائية، الخادم يتحقق أن المادة مسندة لهذا المدرس.", color = Muted, fontSize = 11.sp)
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان الواجب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(details, { details = it }, label = { Text("التفاصيل") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = {
                        homework.add(subject + ": " + title + if (details.isBlank()) "" else " — " + details)
                        subject = ""; title = ""; details = ""
                    }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("نشر في المعاينة") }
                }
            }
        } else item { EmptyCard("عرض الواجبات", "المدرس ينشر لمادته، والإدارة تدير المحتوى حسب الصلاحيات.") }
        if (canReport) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("متابعة الواجبات", color = Ink, fontWeight = FontWeight.Bold)
                    Text("مراقب الشعبة يرسل بلاغاً للمدير إذا احتاج واجب إلى تعديل أو حذف، ولا يحذفه بنفسه.", color = Muted, fontSize = 12.sp)
                    Button(onClick = { reports.add("بلاغ من مراقب الشعبة: مراجعة الواجبات المنشورة") }, modifier = Modifier.fillMaxWidth()) { Text("إرسال بلاغ للمدير") }
                }
            }
        }
        item { SectionHeading("قائمة الواجبات", "المحتوى المحلي المحفوظ على هذا الجهاز") }
        if (homework.isEmpty()) item { EmptyCard("ماكو واجبات مضافة", "الواجبات تظهر هنا بعد إضافة بيانات تجريبية أو ربط الخادم.") }
        else itemsIndexed(homework) { i, entry ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(entry, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("واجب تجريبي " + (i + 1), color = Muted, fontSize = 11.sp)
                    if (canModerate) {
                        if (editingIndex == i) {
                            OutlinedTextField(editingText, { editingText = it }, label = { Text("تعديل الواجب") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingIndex = -1; editingText = "" }) { Text("إلغاء") }
                                TextButton(onClick = {
                                    if (editingText.isNotBlank()) homework[i] = editingText.trim()
                                    editingIndex = -1; editingText = ""
                                }) { Text("حفظ التعديل") }
                            }
                        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingIndex = i; editingText = entry }) { Text("تعديل") }
                            TextButton(onClick = { homework.removeAt(i); if (editingIndex == i) editingIndex = -1 }) { Text("حذف") }
                        }
                    } else if (canReport) TextButton(onClick = { reports.add("بلاغ من مراقب الشعبة عن: " + entry) }) { Text("إبلاغ المدير") }
                }
            }
        }
        if (reports.isNotEmpty()) {
            item { SectionHeading("البلاغات الإدارية", "محفوظة على هذا الجهاز") }
            itemsIndexed(reports) { i, report -> ContentCard(report, "بلاغ " + (i + 1), Icons.Rounded.Description) }
        }
        item { Text("الأدوار هنا للمعاينة فقط؛ التطبيق لا يملك تسجيل دخول أو خادماً بعد.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun PreparationsScreen(role: SchoolRole, preparations: MutableList<String>) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val canCreate = SchoolPermissions.canCreatePreparation(role, assignedSubject = subject.isNotBlank())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageHeader("التحاضير", "دفتر رقمي لتحضير الدروس وأهدافها وخطوات شرحها.") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F2FF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = Blue)
                        }
                        Column {
                            Text("إضافة تحضير", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("للمدرسين ضمن موادهم المسندة", color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (role == SchoolRole.TEACHER) {
                        OutlinedTextField(subject, { subject = it }, label = { Text("المادة المسندة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(title, { title = it }, label = { Text("عنوان التحضير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(content, { content = it }, label = { Text("أهداف الدرس وخطوات الشرح والواجب") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Button(onClick = {
                            preparations.add(subject + " — " + title + ": " + content)
                            subject = ""; title = ""; content = ""
                        }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank() && content.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("حفظ التحضير في المعاينة")
                        }
                        Text("يُحفظ محلياً في هذه النسخة التجريبية فقط.", color = Muted, fontSize = 11.sp)
                    } else Text("إضافة التحاضير للمدرس فقط؛ والخادم مستقبلاً يتحقق من المادة المسندة إليه.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item { SectionHeading("دفتر التحاضير", "التحاضير المحفوظة على هذا الجهاز") }
        if (preparations.isEmpty()) item { EmptyCard("دفتر التحاضير فارغ", "تظهر هنا التحاضير التي أُضيفت في المعاينة.") }
        else itemsIndexed(preparations) { i, entry -> ContentCard(entry, "تحضير " + (i + 1), Icons.Rounded.Description) }
    }
}

@Composable
private fun NoticesScreen(role: SchoolRole, announcements: MutableList<String>) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val canPublish = SchoolPermissions.canPublishAnnouncements(role)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageHeader("الإعلانات المدرسية", "إعلانات محلية تجريبية؛ لا تصل إلى أجهزة الطلاب الآخرين.") }
        if (canPublish) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إنشاء إعلان", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان الإعلان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("نص الإعلان") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Button(onClick = {
                        announcements.add(0, title.trim() + "\n" + body.trim())
                        title = ""; body = ""
                    }, enabled = title.isNotBlank() && body.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("نشر الإعلان محلياً")
                    }
                }
            }
        }
        item { SectionHeading("الإعلانات المنشورة", "محفوظة على هذا الجهاز فقط") }
        if (announcements.isEmpty()) item {
            EmptyCard("ماكو إعلانات حالياً", if (canPublish) "أنشئ إعلاناً تجريبياً ليظهر هنا." else "ستظهر الإعلانات بعد إضافتها في المعاينة أو ربط خادم المدرسة.")
        } else itemsIndexed(announcements) { index, announcement ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إعلان مدرسي " + (index + 1), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(announcement.substringBefore("\n"), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(announcement.substringAfter("\n", ""), color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    if (canPublish) TextButton(onClick = { announcements.removeAt(index) }, modifier = Modifier.align(Alignment.End)) { Text("حذف الإعلان") }
                }
            }
        }
        item { Text("تنبيه: دور المستخدم قابل للتغيير من واجهة المعاينة، لذلك هذا ليس نظام صلاحيات آمناً.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
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
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Blue)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
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
