import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const allowedRoles = new Set([
  "student", "class_monitor", "parliament_member", "teacher", "class_supervisor",
  "assistant_principal", "principal", "educational_counselor", "ministry_education_member",
]);
const allowedClasses = new Set(["A", "B", "C", "D", "E", "F"]);

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const json = (status: number, data: unknown) =>
    new Response(JSON.stringify(data), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  if (req.method !== "POST") return json(405, { error: "استخدم POST فقط." });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !serviceKey || !anonKey) return json(500, { error: "إعدادات الخادم غير مكتملة." });

  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return json(401, { error: "يلزم تسجيل الدخول." });

  try {
    const callerResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authorization },
    });
    if (!callerResponse.ok) return json(401, { error: "جلسة الدخول غير صالحة." });
    const caller = await callerResponse.json();

    const ownerResponse = await fetch(
      `${supabaseUrl}/rest/v1/profiles?select=id,is_owner&id=eq.${encodeURIComponent(caller.id)}&limit=1`,
      { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
    );
    if (!ownerResponse.ok) return json(500, { error: "تعذر التحقق من صلاحية المالك." });
    const ownerRows = await ownerResponse.json();
    if (!ownerRows[0]?.is_owner) return json(403, { error: "هذه العملية متاحة للمالك فقط." });

    const body = await req.json();
    const fullName = typeof body.full_name === "string" ? body.full_name.trim() : "";
    const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
    const password = typeof body.password === "string" ? body.password : "";
    const role = typeof body.role === "string" ? body.role : "student";
    const className = typeof body.class_name === "string" ? body.class_name.trim().toUpperCase() : "";

    if (fullName.length < 2 || fullName.length > 100) return json(400, { error: "الاسم يجب أن يكون بين حرفين و100 حرف." });
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) return json(400, { error: "البريد الإلكتروني غير صالح." });
    if (password.length < 8 || password.length > 72) return json(400, { error: "كلمة المرور يجب أن تكون 8 أحرف على الأقل وبحد أقصى 72." });
    if (!allowedRoles.has(role)) return json(400, { error: "نوع الحساب غير مسموح." });
    if (className && !allowedClasses.has(className)) return json(400, { error: "الشعبة يجب أن تكون A أو B أو C أو D أو E أو F." });
    if (role === "student" && !className) return json(400, { error: "حدد شعبة الطالب من A إلى F." });
    const createResponse = await fetch(`${supabaseUrl}/auth/v1/admin/users`, {
      method: "POST",
      headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        email, password, email_confirm: true,
        user_metadata: { full_name: fullName },
      }),
    });
    const created = await createResponse.json();
    if (!createResponse.ok) {
      const message = typeof created.msg === "string" ? created.msg : typeof created.message === "string" ? created.message : "";
      return json(createResponse.status === 422 ? 409 : 400, { error: message.includes("already") || message.includes("registered") ? "هذا البريد مسجل مسبقاً." : "تعذر إنشاء الحساب؛ تحقق من البريد وكلمة المرور." });
    }

    // The Auth trigger creates the profile; then apply the owner-selected non-owner role and section.
    const profileResponse = await fetch(
      `${supabaseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(created.id)}`,
      {
        method: "PATCH",
        headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json", Prefer: "return=representation" },
        body: JSON.stringify({ full_name: fullName, role, class_name: className || null, is_owner: false, email }),
      },
    );
    const configuredProfiles = profileResponse.ok ? await profileResponse.json() : [];
    if (!profileResponse.ok || !Array.isArray(configuredProfiles) || configuredProfiles.length !== 1) {
      // Do not leave a login account that was only partially configured.
      await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(created.id)}`, {
        method: "DELETE",
        headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` },
      });
      return json(500, { error: "تعذر إعداد ملف الحساب؛ أُلغي إنشاء الحساب." });
    }

    return json(201, { id: created.id, email, full_name: fullName, role, class_name: className || null });
  } catch (_error) {
    return json(400, { error: "تعذر معالجة الطلب. تحقق من البيانات وحاول مجدداً." });
  }
});
