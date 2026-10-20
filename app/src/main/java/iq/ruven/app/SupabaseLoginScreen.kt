package iq.ruven.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RUVEN", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("تسجيل دخول حساب المدرسة", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it; error = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("البريد الإلكتروني") }
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                val session = backend.signIn(email, password)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator()
                    else Text("تسجيل الدخول")
                }
                Text("يجب أن يكون الحساب مُنشأً مسبقاً في Supabase ومربوطاً بملف مدرسي من الإدارة.")
            }
        }
    }
}
