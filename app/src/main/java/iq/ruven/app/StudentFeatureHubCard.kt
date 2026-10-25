package iq.ruven.app

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RuvenStudentTool(
    val id: Int,
    val ar: String,
    val en: String,
    val promptAr: String,
    val promptEn: String,
    val hintAr: String = "",
    val hintEn: String = ""
)

private val ruvenStudentTools = listOf(
    RuvenStudentTool(1, "قائمة الجنطة", "Bag checklist", "اكتب الكتب والأغراض المطلوبة", "Enter books and items", "مثال: رياضيات، دفتر عربي", "Example: math book, Arabic notebook"),
    RuvenStudentTool(2, "تقسيم الواجب", "Break down homework", "اكتب الواجب حتى أقسمه إلى خطوات", "Enter homework to split into steps"),
    RuvenStudentTool(3, "مراجعة متباعدة", "Spaced review", "اكتب اسم الدرس", "Enter lesson name"),
    RuvenStudentTool(4, "وضع التركيز", "Focus mode", "اكتب المهمة التي تريد التركيز عليها", "Enter the task you want to focus on"),
    RuvenStudentTool(5, "سُلّم التلميحات", "Hint ladder", "اكتب السؤال أو الموضوع", "Enter the question or topic"),
    RuvenStudentTool(6, "تقدير وقت الدراسة", "Study time estimate", "اكتب عدد الدقائق المتوقعة", "Enter estimated minutes"),
    RuvenStudentTool(7, "ورقة مراجعة", "Revision sheet", "اكتب نقاط المراجعة", "Enter revision points"),
    RuvenStudentTool(8, "قراءة بصوت", "Read aloud", "اكتب النص المراد قراءته", "Enter text to read aloud"),
    RuvenStudentTool(9, "موعد امتحان", "Exam deadline", "اكتب المادة والتاريخ", "Enter subject and date"),
    RuvenStudentTool(10, "حاسبة الدرجات", "Grade calculator", "اكتب درجتك والدرجة الكلية، مثل 42/50", "Enter your score and total, e.g. 42/50"),
    RuvenStudentTool(13, "المحفوظات", "Saved content", "اكتب محتوى تريد حفظه على هذا الجهاز", "Enter content to save"),
    RuvenStudentTool(14, "آخر ما فتحته", "Recently viewed", "اكتب اسم الصفحة أو الموضوع", "Enter page or topic name"),
    RuvenStudentTool(15, "ملخص الأسبوع", "Weekly recap", "اكتب أبرز إنجازاتك هذا الأسبوع", "Enter your main accomplishments this week"),
    RuvenStudentTool(16, "الحصة القادمة", "Next lesson", "اكتب اسم الحصة وموعدها", "Enter lesson name and time"),
    RuvenStudentTool(17, "استراحة ذكية", "Break planner", "اكتب مدة الاستراحة بالدقائق", "Enter break duration in minutes"),
    RuvenStudentTool(18, "خطة الصباح", "Morning plan", "اكتب أهم شي لازم تنجزه قبل المدرسة", "Enter what you need to do before school"),
    RuvenStudentTool(20, "تحضير باچر", "Prepare for tomorrow", "اكتب جدول باچر أو واجباته", "Enter tomorrow's timetable or homework"),
    RuvenStudentTool(31, "تسجيل المزاج", "Mood check-in", "سجّل شعورك بكلمات بسيطة (خاص على جهازك)", "Describe your mood briefly (private on this device)"),
    RuvenStudentTool(32, "دقيقة هدوء", "Quiet minute", "اكتب شنو تريد تتركه مؤقتًا حتى ترتاح", "Write what you want to pause and set aside"),
    RuvenStudentTool(36, "رسالة شكر", "Thank-you helper", "اكتب شنو الشخص ساعدك بيه", "Describe how the person helped you")
)

@Composable
fun StudentFeatureHubCard(isEnglish: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ruven_student_tools", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf<RuvenStudentTool?>(null) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var focusEnabled by remember { mutableStateOf(false) }
    var focusTask by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (isEnglish) "Student toolkit · 20 tools" else "صندوق أدوات الطالب · 20 ميزة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (isEnglish) "Personal tools are saved locally on this device." else "الأدوات الشخصية تُحفظ محليًا على هذا الجهاز.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (focusEnabled) {
                Text(
                    if (isEnglish) "FOCUS MODE" else "وضع التركيز",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isEnglish) "Current task: $focusTask" else "المهمة الحالية: $focusTask",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (isEnglish) "Other tools are hidden until you exit focus mode." else "بقية الأدوات مخفية إلى أن تطلع من وضع التركيز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { focusEnabled = false; focusTask = "" }) {
                    Text(if (isEnglish) "Exit focus mode" else "إيقاف وضع التركيز")
                }
            } else {
                ruvenStudentTools.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { tool ->
                            Button(
                                onClick = {
                                    selected = tool
                                    input = ""
                                    output = prefs.getString("result_${tool.id}", "") ?: ""
                                    prefs.edit().putString("recent_${System.currentTimeMillis()}", tool.ar).apply()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(if (isEnglish) tool.en else tool.ar, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    selected?.let { tool ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (isEnglish) tool.en else tool.ar) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isEnglish) tool.promptEn else tool.promptAr)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(4000) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isEnglish) tool.hintEn.ifBlank { tool.promptEn } else tool.hintAr.ifBlank { tool.promptAr }) },
                        minLines = 2,
                        maxLines = 6
                    )
                    if (output.isNotBlank()) {
                        Text(output, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = if (tool.id == 14 && input.isBlank()) {
                        val recent = prefs.all.entries
                            .filter { it.key.startsWith("recent_") }
                            .sortedByDescending { it.key.removePrefix("recent_").toLongOrNull() ?: 0L }
                            .take(8)
                            .mapNotNull { it.value as? String }
                        if (recent.isEmpty()) {
                            if (isEnglish) "Nothing viewed in this toolkit yet." else "ماكو عناصر حديثة بعد."
                        } else {
                            (if (isEnglish) "Recently viewed:\n" else "آخر العناصر:\n") + recent.joinToString("\n")
                        }
                    } else buildToolResult(tool.id, input.trim(), isEnglish)
                    output = result
                    prefs.edit()
                        .putString("result_${tool.id}", result)
                        .putString("input_${tool.id}", input.trim())
                        .putLong("updated_${tool.id}", System.currentTimeMillis())
                        .apply()
                    if (tool.id == 4 && input.isNotBlank()) {
                        focusTask = input.trim()
                        focusEnabled = true
                    }
                    if (tool.id == 8 && input.isNotBlank()) speakWithSystemTts(context, input.trim())
                }) { Text(if (isEnglish) "Generate / Save" else "إنشاء / حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text(if (isEnglish) "Close" else "إغلاق") }
            }
        )
    }
}


private fun buildHomeworkBreakdown(value: String, isEnglish: Boolean): String {
    val customSteps = value.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val steps = if (customSteps.size > 1) {
        customSteps
    } else {
        val task = value.trim()
        if (isEnglish) listOf(
            "Read the full instructions for: $task",
            "Gather the required books, notes, and materials",
            "Complete the first part of the assignment",
            "Complete the remaining questions or sections",
            "Review answers and check the teacher's requirements"
        ) else listOf(
            "اقرأ تعليمات الواجب كاملة: $task",
            "جهّز الكتاب والدفتر والملاحظات المطلوبة",
            "أنجز الجزء الأول من الواجب",
            "كمّل بقية الأسئلة أو الأجزاء",
            "راجع الإجابات وطابقها ويا المطلوب من المدرّس"
        )
    }
    val numbered = steps.mapIndexed { index, step ->
        val number = index + 1
        if (isEnglish) "$number. ☐ $step (about 10 min)" else "$number. ☐ $step (حوالي ١٠ دقائق)"
    }.joinToString("\n")
    val totalMinutes = steps.size * 10
    return if (isEnglish) {
        "HOMEWORK PLAN\n$numbered\n\nEstimated total: about $totalMinutes minutes. This is a flexible estimate, not a timer.\nTip: enter one custom step per line to replace the suggested steps."
    } else {
        "خطة الواجب\n$numbered\n\nالوقت التقريبي الكلي: حوالي $totalMinutes دقيقة. هذا تقدير قابل للتعديل وليس مؤقتًا فعليًا.\nنصيحة: اكتب كل خطوة بسطر مستقل حتى تستخدم خطواتك الخاصة."
    }
}

private fun speakWithSystemTts(context: Context, text: String) {
    runCatching {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale("ar")
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ruven_read_aloud")
            }
        }
    }
}

private fun buildToolResult(id: Int, value: String, isEnglish: Boolean): String {
    if (value.isBlank()) return if (isEnglish) "Enter something first." else "اكتب شي أولًا."
    return when (id) {
        1 -> if (isEnglish) "Bag checklist:\n" + value.split(',', '،', '\n').map { "☐ ${it.trim()}" }.filter { it != "☐ " }.joinToString("\n") else "قائمة الجنطة:\n" + value.split(',', '،', '\n').map { "☐ ${it.trim()}" }.filter { it != "☐ " }.joinToString("\n")
        2 -> buildHomeworkBreakdown(value, isEnglish)
        3 -> if (isEnglish) "Suggested reviews for “$value”: today, tomorrow, in 3 days, and in 7 days. These are suggestions, not scheduled notifications." else "مواعيد مراجعة مقترحة لـ«$value»: اليوم، باچر، بعد 3 أيام، وبعد 7 أيام. هاي مقترحات وليست إشعارات مجدولة."
        4 -> if (isEnglish) "Focus task: $value\nKeep only this task in front of you; take a break when needed." else "مهمة التركيز: $value\nخلّ تركيزك على هاي المهمة وخذ استراحة عند الحاجة."
        5 -> if (isEnglish) "Hint 1: Identify what the question asks.\nHint 2: List the facts or rules you know.\nHint 3: Try solving one step at a time, then verify with your textbook.\nTopic: $value" else "التلميح الأول: حدّد شنو المطلوب بالسؤال.\nالتلميح الثاني: اكتب المعطيات أو القواعد اللي تعرفها.\nالتلميح الثالث: حل خطوة خطوة وتأكد من الكتاب.\nالموضوع: $value"
        6 -> runCatching { val mins = value.toInt(); if (isEnglish) "Plan about ${(mins * 0.8).toInt()}–${(mins * 1.2).toInt()} minutes, with a short break if needed. Estimate only." else "خطط تقريبًا لـ${(mins * 0.8).toInt()}–${(mins * 1.2).toInt()} دقيقة، وخذ استراحة قصيرة إذا تحتاج. هذا تقدير فقط." }.getOrElse { if (isEnglish) "Enter minutes as a number." else "اكتب عدد الدقائق بالأرقام." }
        7 -> if (isEnglish) "REVISION SHEET\n$value\n\nCheck each point against your teacher's material." else "ورقة مراجعة\n$value\n\nطابق كل نقطة ويا مادة المدرّس."
        8 -> if (isEnglish) "Read-aloud requested. If your device's speech engine is available, playback will start." else "تم طلب القراءة الصوتية. إذا محرّك النطق متوفر بالجهاز، يبدأ التشغيل."
        9 -> if (isEnglish) "Exam/deadline saved on this device:\n$value\nAdd it to your calendar too; this tool does not send reminders yet." else "تم حفظ موعد الامتحان/التسليم على هذا الجهاز:\n$value\nأضفه للتقويم أيضًا؛ الأداة حاليًا ما ترسل تذكيرات."
        10 -> runCatching { val parts = value.split('/'); val score = parts[0].trim().toDouble(); val total = parts[1].trim().toDouble(); require(total > 0 && score >= 0 && score <= total); val pct = score / total * 100; if (isEnglish) "Estimated percentage: ${"%.1f".format(Locale.US, pct)}%. This is a calculation, not an official grade." else "النسبة الحسابية: ${"%.1f".format(Locale.US, pct)}٪. هاي عملية حسابية وليست درجة رسمية." }.getOrElse { if (isEnglish) "Use the format score/total, for example 42/50." else "اكتبها بهالشكل: درجتك/الدرجة الكلية، مثل 42/50." }
        13 -> if (isEnglish) "Saved locally:\n$value" else "انحفظ محليًا:\n$value"
        14 -> if (isEnglish) "Recent item recorded:\n$value" else "تم تسجيل العنصر ضمن آخر ما فتحته:\n$value"
        15 -> if (isEnglish) "WEEKLY RECAP\n$value\nChoose one small priority for next week." else "ملخص الأسبوع\n$value\nاختار هدف صغير واحد للأسبوع الجاي."
        16 -> if (isEnglish) "Next lesson note saved:\n$value\nThis is a note, not a live timetable countdown." else "تم حفظ ملاحظة الحصة القادمة:\n$value\nهاي ملاحظة وليست عدّادًا مباشرًا للجدول."
        17 -> runCatching { val mins = value.toInt(); require(mins in 1..180); if (isEnglish) "Take a $mins-minute break. Set a device timer manually; this tool does not run in the background." else "خذ استراحة $mins دقيقة. اضبط مؤقت الجهاز يدويًا؛ الأداة ما تشتغل بالخلفية." }.getOrElse { if (isEnglish) "Enter a duration from 1 to 180 minutes." else "اكتب مدة من دقيقة إلى 180 دقيقة." }
        18 -> if (isEnglish) "MORNING PLAN\n☐ Get ready\n☐ Check bag\n☐ Check timetable\n☐ Review: $value" else "خطة الصباح\n☐ جهّز نفسك\n☐ افحص الجنطة\n☐ راجع الجدول\n☐ راجع: $value"
        20 -> if (isEnglish) "TOMORROW PREP\n☐ Prepare books\n☐ Pack required items\n☐ Review deadlines\n☐ Check alarm\nDetails: $value" else "تحضير باچر\n☐ جهّز الكتب\n☐ حضّر الأغراض المطلوبة\n☐ راجع الواجبات\n☐ تأكد من المنبّه\nالتفاصيل: $value"
        31 -> if (isEnglish) "Private mood note saved on this device. You can replace it or clear app data in Android settings." else "انحفظت ملاحظة المزاج بشكل خاص على هذا الجهاز. تگدر تستبدلها أو تمسح بيانات التطبيق من إعدادات أندرويد."
        32 -> if (isEnglish) "Pause for a moment, relax your shoulders, and take a few comfortable breaths. This is a simple pause, not medical care." else "وقف لحظة، ريّح كتافك وخذ كم نفس مريح. هاي استراحة بسيطة وليست علاجًا طبيًا."
        36 -> if (isEnglish) "Thank-you message draft:\nThank you for helping me with $value. I appreciate your time and support." else "مسودة شكر:\nشكرًا لأنك ساعدتني بـ$value. أقدّر وقتك ومساعدتك."
        else -> value
    }
}
