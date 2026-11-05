import { withSupabase } from "npm:@supabase/server@1.9.1";

const noStore = { "Cache-Control": "no-store" };

export default {
  fetch: withSupabase({ auth: "publishable" }, async (request, context) => {
    if (request.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405, headers: noStore });
    }

    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return Response.json({ error: "Invalid request" }, { status: 400, headers: noStore });
    }

    if (typeof body !== "object" || body === null) {
      return Response.json({ error: "Invalid request" }, { status: 400, headers: noStore });
    }

    const fields = body as Record<string, unknown>;
    const login = typeof fields.login === "string" ? fields.login.trim().toLowerCase() : "";
    const password = fields.password;
    if (!/^[a-z0-9_]{3,30}$/.test(login) || typeof password !== "string" ||
        password.length === 0 || password.length > 1024) {
      return Response.json({ error: "Invalid credentials" }, { status: 401, headers: noStore });
    }

    const { data: profile, error: lookupError } = await context.supabaseAdmin
      .from("User")
      .select("gmail")
      .eq("login", login)
      .maybeSingle();

    if (lookupError) {
      return Response.json({ error: "Login unavailable" }, { status: 503, headers: noStore });
    }

    // An Auth request is made for unknown logins too, so failed attempts follow
    // the same Auth rate limits and do not reveal whether a login exists.
    const email = profile?.gmail ?? "missing-user@example.invalid";
    const { data, error } = await context.supabase.auth.signInWithPassword({
      email,
      password,
    });

    if (!profile || error || !data.session) {
      return Response.json({ error: "Invalid credentials" }, { status: 401, headers: noStore });
    }

    const session = data.session;
    return Response.json({
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      token_type: session.token_type,
    }, { headers: noStore });
  }),
};
