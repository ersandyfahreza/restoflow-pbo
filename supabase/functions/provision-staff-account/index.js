const roles = new Set(["admin", "cashier", "kitchen", "waiter", "courier"]);

function error(status, message) {
  return Response.json({ message: `RF: ${message}` }, { status });
}

export async function createStaffAccount(request, { url, serviceKey }) {
  if (request.method !== "POST") return error(405, "Gunakan metode POST.");
  const authorization = request.headers.get("authorization");
  const apikey = request.headers.get("apikey");
  if (!authorization?.match(/^Bearer \S+$/i) || !apikey) {
    return error(401, "Silakan masuk kembali.");
  }
  if (!url || !serviceKey) return error(503, "Konfigurasi server pembuatan akun belum lengkap.");

  try {
    const userHeaders = { apikey, Authorization: authorization, "Content-Type": "application/json" };
    const caller = await fetch(`${url}/auth/v1/user`, { headers: userHeaders });
    if (!caller.ok) {
      return caller.status < 500 ? error(401, "Silakan masuk kembali.")
        : error(503, "Supabase Auth belum dapat memeriksa sesi.");
    }
    const { id: actor } = await caller.json();
    if (!actor) return error(401, "Silakan masuk kembali.");

    const input = await request.json().catch(() => null);
    const email = typeof input?.email === "string" ? input.email.trim().toLowerCase() : "";
    const name = typeof input?.name === "string" ? input.name.trim() : "";
    const role = input?.role;
    const password = typeof input?.password === "string" ? input.password : "";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
      return error(400, "Masukkan alamat email staf yang valid.");
    }
    if (!name || [...name].length > 100 || !roles.has(role)) {
      return error(400, "Nama atau peran akun tidak valid.");
    }
    if (!password.trim() || [...password].length < 8) {
      return error(400, "Kata sandi awal minimal 8 karakter.");
    }

    const profile = { name, role };
    const createIdentity = input?.create_identity === true;
    if (role === "kitchen" || role === "courier") {
      if (createIdentity && (input.staff_id === undefined || input.staff_id === null)) {
        profile.create_identity = true;
      } else if (!Number.isInteger(input.staff_id) || input.staff_id < 1 || input.staff_id > 2147483647) {
        return error(400, "Pilih identitas staf aktif.");
      } else {
        profile.staff_id = input.staff_id;
      }
    }
    if (role === "waiter") {
      if (typeof input.waiter_id !== "string" || !input.waiter_id.trim() || input.waiter_id.trim().length > 10) {
        return error(400, "Pilih identitas pelayan aktif.");
      }
      profile.waiter_id = input.waiter_id.trim();
      if (createIdentity) profile.create_identity = true;
    }

    // This RPC also verifies that the provisioning migration is installed before calling Auth Admin.
    const check = await fetch(`${url}/rest/v1/rpc/rf_check_staff_account`, {
      method: "POST", headers: userHeaders, body: JSON.stringify({ p: profile }),
    });
    const result = await check.json().catch(() => null);
    if (check.status === 401 || check.status === 403) return error(403, "Hanya admin aktif yang dapat membuat akun.");
    if (check.status === 404) return error(503, "Migrasi pembuatan akun belum tersedia di Supabase.");
    if (!check.ok || result !== true) {
      if (result?.message?.startsWith("RF: ")) return Response.json({ message: result.message }, { status: 400 });
      return error(503, "Supabase belum dapat memeriksa data akun.");
    }

    const created = await fetch(`${url}/auth/v1/admin/users`, {
      method: "POST",
      headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, email_confirm: true, app_metadata: {
        restoflow_staff_account: { ...profile, actor },
      } }),
    });
    const user = await created.json().catch(() => null);
    const code = user?.error_code ?? user?.code;
    const rawMessage = typeof user?.msg === "string" ? user.msg : (typeof user?.message === "string" ? user.message : "");
    if (code === "email_exists" || code === "user_already_exists"
        || (created.status === 422 && /already|exists|registered/i.test(rawMessage))) {
      return error(409, "Email sudah digunakan. Untuk akun Auth yang sudah ada, gunakan Hubungkan akun yang sudah ada.");
    }
    if (code === "weak_password") return error(400, "Kata sandi awal belum memenuhi aturan Supabase. Gunakan kata sandi yang lebih kuat.");
    const rfMatch = rawMessage.match(/RF:\s*[^\n"]+/);
    if (rfMatch) return Response.json({ message: rfMatch[0] }, { status: 400 });
    if (!created.ok || !user?.id) {
      return error(502, "Supabase tidak mengonfirmasi pembuatan akun. Muat ulang daftar akun sebelum mencoba lagi.");
    }
    return Response.json({ id: user.id }, { status: 201 });
  } catch {
    return error(502, "Koneksi server gagal. Muat ulang daftar akun sebelum mencoba lagi.");
  }
}

if (import.meta.main) {
  Deno.serve(request => createStaffAccount(request, {
    url: Deno.env.get("SUPABASE_URL"),
    serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
  }));
}
