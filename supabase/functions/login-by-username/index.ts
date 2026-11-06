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
    const identifier = typeof fields.login === "string" ? fields.login.trim().toLowerCase() : "";
    const password = fields.password;
    const loginLength = Array.from(identifier).length;
    const validLogin = loginLength >= 3 && loginLength <= 30;
    const looksLikeEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identifier);
    if ((!validLogin && !looksLikeEmail) || identifier.length > 254 || typeof password !== "string" ||
        password.length === 0 || password.length > 1024) {
      return Response.json({ error: "Invalid credentials" }, { status: 401, headers: noStore });
    }

    let profile: { gmail: string } | null = null;
    if (validLogin) {
      const lookup = await context.supabaseAdmin
        .from("users")
        .select("gmail")
        .eq("login", identifier)
        .maybeSingle();
      if (lookup.error) {
        return Response.json({ error: "Login unavailable" }, { status: 503, headers: noStore });
      }
      profile = lookup.data;
    }

    const emails = profile ? [profile.gmail] : [];
    if (looksLikeEmail && !emails.includes(identifier)) emails.push(identifier);
    // Unknown logins still make an Auth request to avoid revealing account existence.
    if (emails.length === 0) emails.push("missing-user@example.invalid");

    let session = null;
    for (const email of emails) {
      const result = await context.supabase.auth.signInWithPassword({ email, password });
      if (!result.error && result.data.session) {
        session = result.data.session;
        break;
      }
    }
    if (!session) {
      return Response.json({ error: "Invalid credentials" }, { status: 401, headers: noStore });
    }

    return Response.json({
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      token_type: session.token_type,
    }, { headers: noStore });
  }),
};
