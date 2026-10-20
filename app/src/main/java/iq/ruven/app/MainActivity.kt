package iq.ruven.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Announcement
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF10253F)
private val Blue = Color(0xFF2867D8)
private val Cyan = Color(0xFF56C7E8)
private val Canvas = Color(0xFFF4F7FC)
private val Muted = Color(0xFF718096)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RuvenTheme { RuvenApp() } }
    }
}

@Composable
private fun RuvenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Blue,
            secondary = Cyan,
            background = Canvas,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink
        ),
        content = content
    )
}

private enum class RuvenTab(val title: String) {
    Home("الرئيسية"), Schedule("الجدول"), Homework("الواجبات"), Notices("الإعلانات")
}

@Composable
private fun RuvenApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = Blue,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    RuvenTab.Home to Icons.Rounded.Home,
                    RuvenTab.Schedule to Icons.Rounded.CalendarMonth,
                    RuvenTab.Homework to Icons.Rounded.MenuBook,
                    RuvenTab.Notices to Icons.Rounded.Announcement
                )
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.second, contentDescription = item.first.title) },
                        label = { Text(item.first.title, fontSize = 11.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ruven-tab-transition",
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { tab ->
            when (tab) {
                0 -> HomeScreen()
                1 -> PlaceholderScreen("الجدول الدراسي", "سيظهر جدول الحصص هنا عند ربط بيانات المدرسة.")
                2 -> PlaceholderScreen("الواجبات والتحاضير", "ستُعرض الواجبات حسب المادة والموعد عند توفر البيانات.")
                else -> PlaceholderScreen("الإعلانات المدرسية", "ستظهر إعلانات المدرسة الرسمية هنا.")
            }
        }
    }
    }
}

@Composable
private fun HomeScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RUVEN", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("هلا بيك", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("مساحتك المدرسية، بصورة أوضح.", color = Muted, fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Blue, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.School, contentDescription = "RUVEN", tint = Color.White, modifier = Modifier.size(27.dp))
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF174A9C), Color(0xFF328CD9), Color(0xFF55C7E8))))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("مساحتك الدراسية", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
                        Text("كل يوم دراسي\nأكثر تنظيماً", color = Color.White, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                        Text("الجدول والواجبات والإعلانات بمكان واحد.", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
        item { SectionHeading("نظرة سريعة", "معلوماتك اليومية") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("الجدول", "غير متاح", "بانتظار بيانات المدرسة",
                    { Icon(Icons.Rounded.CalendarMonth, null, tint = Blue) }, Modifier.weight(1f))
                SummaryCard("الواجبات", "لا توجد بيانات", "ستظهر عند الربط",
                    { Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF159B83)) }, Modifier.weight(1f))
            }
        }
        item { SectionHeading("آخر المستجدات", "إعلانات المدرسة") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF4FF)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Announcement, null, tint = Blue) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text("ماكو إعلانات حالياً", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("الإعلانات الرسمية راح تظهر هنا بعد ربط النظام.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        item {
            Text(
                "نسخة تأسيسية • البيانات غير مرتبطة بخادم بعد",
                color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
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
private fun SummaryCard(
    title: String,
    value: String,
    caption: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFF0F5FF)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFE7F3FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.School, contentDescription = null, tint = Blue, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(50.dp)) {
            Text("قيد التطوير", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}
