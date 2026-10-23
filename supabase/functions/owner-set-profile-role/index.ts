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
    const targetUserId = typeof body.target_user_id === "string" ? body.target_user_id : "";
    const role = typeof body.role === "string" ? body.role : "";
    const className = typeof body.class_name === "string" ? body.class_name.trim().toUpperCase() : "";
    if (!targetUserId) return json(400, { error: "معرّف الحساب مطلوب." });
    if (!allowedRoles.has(role)) return json(400, { error: "نوع الحساب غير مسموح." });
    if (className && !allowedClasses.has(className)) return json(400, { error: "الشعبة يجب أن تكون A أو B أو C أو D أو E أو F." });
    if (role === "student" && !className) return json(400, { error: "حدد شعبة الطالب من A إلى F." });
    const targetResponse = await fetch(
      `${supabaseUrl}/rest/v1/profiles?select=id,is_owner&id=eq.${encodeURIComponent(targetUserId)}&limit=1`,
      { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
    );
    if (!targetResponse.ok) return json(500, { error: "تعذر التحقق من الحساب المستهدف." });
    const targets = await targetResponse.json();
    if (!targets.length) return json(404, { error: "الحساب غير موجود." });
    if (targets[0].is_owner) return json(403, { error: "حساب المالك محمي ولا يمكن تغيير دوره." });

    const updateResponse = await fetch(
      `${supabaseUrl}/rest/v1/profiles?id=eq.${encodeURIComponent(targetUserId)}&is_owner=eq.false`,
      {
        method: "PATCH",
        headers: {
          apikey: serviceKey, Authorization: `Bearer ${serviceKey}`,
          "Content-Type": "application/json", Prefer: "return=representation",
        },
        body: JSON.stringify({ role, class_name: className || null, is_owner: false }),
      },
    );
    if (!updateResponse.ok) return json(500, { error: "تعذر حفظ صلاحيات الحساب." });
    const updated = await updateResponse.json();
    if (!Array.isArray(updated) || updated.length !== 1) return json(409, { error: "تغير الحساب أثناء التحديث؛ أعد المحاولة." });
    return json(200, { id: targetUserId, role, class_name: className || null });
  } catch (_error) {
    return json(400, { error: "تعذر معالجة الطلب. تحقق من البيانات وحاول مجدداً." });
  }
});
