package iq.ruven.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.launch

@Composable
fun SupabaseLoginScreen(onAuthenticated: (SupabaseBackend.Session, SupabaseBackend.Profile) -> Unit) {
    val backend = remember { SupabaseBackend() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFEAF2FF), Color(0xFFF5F8FD), Color(0xFFE7F8FB)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF132F63), Color(0xFF2867D8), Color(0xFF37B7D2))))
                        .padding(horizontal = 24.dp, vertical = 25.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            Modifier.size(66.dp).clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            "RUVEN",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "كل منظومتك المدرسية بمكان واحد",
                            color = Color.White.copy(alpha = .94f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "سجّل الدخول لمتابعة الجدول والواجبات وإعلانات المدرسة.",
                            color = Color.White.copy(alpha = .82f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(17.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(27.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "أهلاً بعودتك",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10253F)
                            )
                            Text(
                                "استخدم بيانات الحساب التي وفّرتها إدارة المدرسة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF718096)
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("البريد الإلكتروني") },
                            leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("كلمة المرور") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                        )
                        if (error != null) {
                            Surface(
                                color = Color(0xFFFFEEEE),
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    error!!,
                                    color = Color(0xFFB3261E),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(
                            onClick = {
                                loading = true
                                error = null
                                scope.launch {
                                    try {
                                        val session = backend.signIn(email.trim(), password)
                                        val profile = backend.loadMyProfile(session)
                                        onAuthenticated(session, profile)
                                    } catch (e: Exception) {
                                        error = e.message ?: "تعذر تسجيل الدخول."
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading && email.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(21.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(9.dp))
                                Text("جارٍ التحقق من الحساب…")
                            } else Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF718096), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "الدخول مخصص للحسابات المعتمدة من المدرسة",
                                color = Color(0xFF718096),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "RUVEN • منظومة المدرسة العراقية الثانوية الثانية للطلاب المتميزين",
                    color = Color(0xFF718096),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
