package iq.ruven.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Announcement
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
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
    Preparations("التحاضير"), Notices("الإعلانات"), Admin("الحسابات"), Sections("الشعب")
}

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
                Text("جارٍ استعادة جلسة الدخول…", color = Ink)
            }
        }
    } else if (session == null || profile == null) {
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
                            RuvenUpdater.downloadAndInstall(activity, release.apkUrl)
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
                        feedError = null
                    } catch (e: Exception) {
                        feedError = e.message ?: "تعذر تحديث محتوى المدرسة."
                    }
                }
            },
            onSignOut = {
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
            title = { Text("تحديث RUVEN") },
            text = { Text(updateMessage) },
            confirmButton = {
                TextButton(onClick = onDismissUpdateMessage) { Text("حسناً") }
            }
        )
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                val tabs = buildList {
                    add(RuvenTab.Home to Icons.Rounded.Home)
                    add(RuvenTab.Schedule to Icons.Rounded.CalendarMonth)
                    add(RuvenTab.Homework to Icons.AutoMirrored.Rounded.MenuBook)
                    add(RuvenTab.Preparations to Icons.Rounded.Description)
                    add(RuvenTab.Notices to Icons.AutoMirrored.Rounded.Announcement)
                    if (role == SchoolRole.OWNER) {
                        add(RuvenTab.Admin to Icons.Rounded.School)
                        add(RuvenTab.Sections to Icons.Rounded.School)
                    }
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
                            TextButton(onClick = onCheckUpdates, enabled = !checkingUpdate) {
                                if (checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("تحديث التطبيق", color = Blue, fontSize = 11.sp)
                            }
                            TextButton(onClick = onSignOut) { Text("خروج", color = Blue, fontSize = 11.sp) }
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
                AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ruven-tab-transition", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        0 -> HomeScreen(role, homework.size, preparations.size, schedules.size, remoteItems, feedError) { tab = it }
                        1 -> ScheduleScreen(role, schedules, scheduleFiles)
                        2 -> HomeworkScreen(role, homework, reports)
                        3 -> PreparationsScreen(role, preparations)
                        4 -> NoticesScreen(role, announcements)
                        5 -> if (role == SchoolRole.OWNER) OwnerAccountManagementScreen(session, backend)
                            else NoticesScreen(role, announcements)
                        else -> if (role == SchoolRole.OWNER) OwnerSectionsScreen(session, backend)
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
                            Text("إضافة شعبة", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCreateForm) "أدخل الصف ورمز الشعبة المطلوبة" else "افتح النموذج عند الحاجة فقط", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showCreateForm = !showCreateForm }) {
                            Text(if (showCreateForm) "إغلاق ▲" else "إضافة +")
                        }
                    }
                    if (showCreateForm) {
                    OutlinedTextField(gradeName, { gradeName = it }, label = { Text("الصف الدراسي") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box {
                        OutlinedButton(onClick = { sectionMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("الشعبة: $sectionCode") }
                        DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                            listOf("A", "B", "C", "D", "E", "F").forEach { code ->
                                DropdownMenuItem(text = { Text(code) }, onClick = { sectionCode = code; sectionMenu = false })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = openOnCreate, onCheckedChange = { openOnCreate = it })
                        Text("فتح الشعبة مباشرة للطلاب والموظفين", color = Ink, fontSize = 13.sp)
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
                label = { Text("بحث بالصف أو رمز الشعبة") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الشعب المسجلة (${sections.size})", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                        Text("${section.gradeName} — شعبة ${section.sectionCode}", color = Ink, fontWeight = FontWeight.Bold)
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
                label = { Text("بحث بالاسم أو البريد أو الدور") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("إنشاء حساب جديد", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (showCreateAccountForm) "أدخل بيانات الحساب الجديد" else "إنشاء حساب طالب أو موظف بصلاحيات محددة", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showCreateAccountForm = !showCreateAccountForm }) {
                            Text(if (showCreateAccountForm) "إغلاق ▲" else "إضافة +")
                        }
                    }
                    if (showCreateAccountForm) {
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
                                    showCreateAccountForm = false
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
    remoteItems: List<SupabaseBackend.SchoolItem>, feedError: String?,
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
                    Text("هلا بيك 👋", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("خلّ يومك الدراسي أوضح وأسهل.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Surface(color = Color(0xFFE5F1FF), shape = RoundedCornerShape(50)) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFF20A879)))
                            Text(roleLabel(role), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Text("RUVEN • مدرستك بذكاء", color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                            }
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFFE8A3), modifier = Modifier.size(25.dp))
                        }
                        Text("كل شي يخص دراستك،\nبمكان واحد.", color = Color.White, fontSize = 27.sp,
                            lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
                        Text("تابع الواجبات، افتح الجدول، وابقَ على اطلاع بآخر إعلانات المدرسة.", color = Color.White.copy(alpha = .88f),
                            fontSize = 13.sp, lineHeight = 21.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Button(
                                onClick = { onNavigate(2) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF173D85)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("شوف واجباتك", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onNavigate(1) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .65f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("الجدول")
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("اختصارات سريعة", "اختَر شنو تحتاج هسه")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("الجدول", "حصصك ومواعيدك", Icons.Rounded.CalendarMonth, Color(0xFFE7F0FF), Blue, Modifier.weight(1f)) { onNavigate(1) }
                    QuickActionCard("الواجبات", "لا يفوتك شي", Icons.AutoMirrored.Rounded.MenuBook, Color(0xFFFFF0DD), Color(0xFFB66A12), Modifier.weight(1f)) { onNavigate(2) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("التحاضير", "محتوى الدروس", Icons.Rounded.Description, Color(0xFFE5F8F1), Color(0xFF168A70), Modifier.weight(1f)) { onNavigate(3) }
                    QuickActionCard("الإعلانات", "آخر المستجدات", Icons.AutoMirrored.Rounded.Announcement, Color(0xFFF0E9FF), Color(0xFF7651C9), Modifier.weight(1f)) { onNavigate(4) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("ملخصك الدراسي", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("عدادات العناصر المحفوظة محلياً", color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showAllStats = !showAllStats }) {
                        Text(if (showAllStats) "عرض أقل" else "تفاصيل", fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard("الجدول", scheduleCount.toString(), "جداول", Icons.Rounded.CalendarMonth, Modifier.weight(1f))
                    SummaryCard("الواجبات", homeworkCount.toString(), "واجبات", Icons.AutoMirrored.Rounded.MenuBook, Modifier.weight(1f))
                    SummaryCard("التحاضير", prepCount.toString(), "تحاضير", Icons.Rounded.Description, Modifier.weight(1f))
                }
                androidx.compose.animation.AnimatedVisibility(visible = showAllStats) {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تفاصيل المساحة", color = Ink, fontWeight = FontWeight.Bold)
                            Text("منشورات الخادم: ${remoteItems.size}", color = Muted, fontSize = 12.sp)
                            Text("الواجبات المحلية: $homeworkCount", color = Muted, fontSize = 12.sp)
                            Text("التحاضير المحلية: $prepCount", color = Muted, fontSize = 12.sp)
                            Text("الجداول المحلية: $scheduleCount", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("آخر منشورات المدرسة", "واجبات وتحاضير وإعلانات منشورة من الخادم")
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
                    placeholder = { Text("ابحث باسم الواجب أو المادة…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted) },
                    trailingIcon = {
                        if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
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
                        Text("مساحتك وصلاحياتك", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            Text("RUVEN • نسخة قيد التطوير", color = Muted, fontSize = 10.sp,
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
                            Text("إضافة جدول", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("للمخولين بإدارة الجدول", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showComposer = !showComposer }) {
                            Text(if (showComposer) "إخفاء" else "إضافة")
                        }
                    }
                    if (canManage && showComposer) {
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("الجداول المضافة", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${schedules.size} جدول محفوظ محلياً", color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFEAF2FF), shape = RoundedCornerShape(50)) {
                    Text("الجدول", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text("ابحث في الجداول…") },
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
                            Text("إضافة واجب", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("أنشئ الواجب ثم راجعه قبل النشر", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showHomeworkComposer = !showHomeworkComposer }) { Text(if (showHomeworkComposer) "طيّ النموذج" else "إضافة واجب") }
                    }
                    if (showHomeworkComposer) {
                    OutlinedTextField(subject, { subject = it }, label = { Text("المادة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (role == SchoolRole.TEACHER) Text("بالنسخة النهائية، الخادم يتحقق أن المادة مسندة لهذا المدرس.", color = Muted, fontSize = 11.sp)
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان الواجب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(details, { details = it }, label = { Text("التفاصيل") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = {
                        homework.add(subject + ": " + title + if (details.isBlank()) "" else " — " + details)
                        subject = ""; title = ""; details = ""; showHomeworkComposer = false
                    }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("حفظ الواجب في المعاينة") }
                    }
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("قائمة الواجبات", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${homework.size} واجب محفوظ محلياً", color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFFFF0DD), shape = RoundedCornerShape(50)) {
                    Text("واجبات", color = Color(0xFFB66A12), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text("ابحث عن واجب أو مادة…") },
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
        item { Text("ملاحظة: قائمة الواجبات والبلاغات في هذه الشاشة تجريبية ومحلية حالياً؛ لا تُرسل إلى بقية المستخدمين.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
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
                                Text("إضافة تحضير", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("للمدرسين ضمن موادهم المسندة", color = Muted, fontSize = 11.sp)
                            }
                        }
                        if (role == SchoolRole.TEACHER) TextButton(onClick = { showPreparationComposer = !showPreparationComposer }) { Text(if (showPreparationComposer) "طيّ" else "إضافة") }
                    }
                    if (role == SchoolRole.TEACHER && showPreparationComposer) {
                        OutlinedTextField(subject, { subject = it }, label = { Text("المادة المسندة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(title, { title = it }, label = { Text("عنوان التحضير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(content, { content = it }, label = { Text("أهداف الدرس وخطوات الشرح والواجب") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Button(onClick = {
                            preparations.add(subject + " — " + title + ": " + content)
                            subject = ""; title = ""; content = ""; showPreparationComposer = false
                        }, enabled = canCreate && subject.isNotBlank() && title.isNotBlank() && content.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("حفظ التحضير في المعاينة")
                        }
                        Text("يُحفظ محلياً في هذه النسخة التجريبية فقط.", color = Muted, fontSize = 11.sp)
                    } else Text("إضافة التحاضير للمدرس فقط؛ والخادم مستقبلاً يتحقق من المادة المسندة إليه.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("دفتر التحاضير", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${preparations.size} تحضير محفوظ محلياً", color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFE5F8F1), shape = RoundedCornerShape(50)) {
                    Text("التحاضير", color = Color(0xFF168A70), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text("ابحث باسم التحضير أو المادة…") },
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
                            Text("لوحة إنشاء إعلان", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("اكتب عنواناً واضحاً ورسالة مختصرة", color = Muted, fontSize = 11.sp)
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
                                label = { Text("عنوان الإعلان") },
                                placeholder = { Text("مثال: تغيير موعد الامتحان") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(15.dp)
                            )
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                label = { Text("نص الإعلان") },
                                placeholder = { Text("اكتب التفاصيل المهمة هنا…") },
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
                                Text("نشر في المعاينة")
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
                    Text("لوحة الإعلانات", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${announcements.size} إعلان محفوظ على هذا الجهاز", color = Muted, fontSize = 11.sp)
                }
                Surface(color = Color(0xFFEAF2FF), shape = RoundedCornerShape(50)) {
                    Text("محلي", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
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
                placeholder = { Text("ابحث في الإعلانات…") },
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
                                Text("إعلان مدرسي ${announcements.size - index}", color = Color(0xFF7651C9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                Text("نص الإعلان الكامل", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                if (canPublish) {
                                    TextButton(
                                        onClick = { announcements.removeAt(index) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("حذف الإعلان")
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
                Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
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
                        Text("اضغط للطي", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!expanded) {
                    Text("اضغط لعرض التفاصيل", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
