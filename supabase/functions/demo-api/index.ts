import { createClient } from "npm:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(url, serviceKey, { auth: { persistSession: false } });
const headers = { "Content-Type": "application/json; charset=utf-8" };

function reply(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers });
}
function fail(message: string, status = 400): never {
  throw { message, status };
}
function str(value: unknown): string { return typeof value === "string" ? value.trim() : ""; }
function id(value: unknown): number {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 1) fail("Некорректный ID");
  return number;
}
function check(error: { message: string } | null): void {
  if (error) throw error;
}
function hex(bytes: Uint8Array): string {
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
async function currentUser(userId: number, password: string) {
  const account = await db.from("users")
    .select("id,login,gmail,password,about,phone,birthday,avatar_path,archive_public")
    .eq("id", userId).maybeSingle();
  check(account.error);
  if (!account.data || account.data.password !== password) fail("Войдите заново", 401);
  const { password: _password, ...user } = account.data;
  return user;
}
function imagePath(path: string, userId: number): string {
  if (path.startsWith(`${userId}/`)) return path;
  if (/^https:\/\/[^\s/?#]+([/?#][^\s]*)?$/i.test(path) && path.length <= 2048) return path;
  fail("Некорректная ссылка на фото");
}
async function upload(bucket: string, userId: number, encoded: string): Promise<string> {
  if (encoded.length > 8_000_000 || !/^[A-Za-z0-9+/=]+$/.test(encoded)) fail("Фото слишком большое или повреждено");
  const bytes = Uint8Array.from(atob(encoded), (char) => char.charCodeAt(0));
  if (bytes.length > 5_242_880 || bytes[0] !== 0xff || bytes[1] !== 0xd8) fail("Выберите JPEG до 5 МБ");
  const suffix = hex(crypto.getRandomValues(new Uint8Array(8)));
  const path = `${userId}/${Date.now()}-${suffix}.jpg`;
  check((await db.storage.from(bucket).upload(path, bytes, { contentType: "image/jpeg" })).error);
  return path;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ error: "Метод не поддерживается" }, 405);
  try {
    const body = await req.json();
    const action = str(body.action);
    if (action === "register") {
      const login = str(body.login).toLowerCase();
      const gmail = str(body.email).toLowerCase();
      const password = typeof body.password === "string" ? body.password : "";
      if ([...login].length < 3 || [...login].length > 30) fail("Логин: от 3 до 30 символов");
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(gmail)) fail("Укажите корректную почту");
      if (password.length < 6 || password.length > 128 || !/[a-z]/i.test(password))
        fail("Пароль: 6–128 символов, хотя бы одна латинская буква");
      const created = await db.from("users").insert({ login, gmail, password }).select("id,login,gmail").single();
      if (created.error?.code === "23505") fail("Логин или почта уже заняты");
      check(created.error);
      return reply({ id: created.data.id, login: created.data.login, email: created.data.gmail });
    }
    if (action === "login") {
      const identifier = str(body.identifier).toLowerCase();
      const password = typeof body.password === "string" ? body.password : "";
      const field = identifier.includes("@") ? "gmail" : "login";
      let account = await db.from("users").select("id,login,gmail,password").eq(field, identifier).maybeSingle();
      check(account.error);
      if (!account.data && field === "gmail") {
        account = await db.from("users").select("id,login,gmail,password").eq("login", identifier).maybeSingle();
        check(account.error);
      }
      if (!account.data || account.data.password !== password) fail("Неверный логин или пароль", 401);
      return reply({ id: account.data.id, login: account.data.login, email: account.data.gmail });
    }
    if (action === "published_recipes") {
      const rows = await db.from("recipes")
        .select("id,owner_id,title,body,created_at")
        .eq("show_publicly", true)
        .order("created_at", { ascending: false })
        .limit(100);
      check(rows.error);
      const recipes = rows.data ?? [];
      if (recipes.length === 0) return reply({ recipes: [] });
      const owners = await db.from("users").select("id,login")
        .in("id", [...new Set(recipes.map((recipe) => recipe.owner_id))]);
      check(owners.error);
      const logins = new Map((owners.data ?? []).map((owner) => [owner.id, owner.login]));
      return reply({ recipes: recipes.map((recipe) => ({
        id: recipe.id,
        author_id: recipe.owner_id,
        author_login: logins.get(recipe.owner_id) ?? "",
        title: recipe.title,
        body: recipe.body,
        created_at: recipe.created_at,
      })) });
    }

    const password = typeof body.password === "string" ? body.password : "";
    const user = await currentUser(id(body.user_id), password);
    switch (action) {
      case "account": return reply({ id: user.id, login: user.login, email: user.gmail });
      case "profile": {
        const recipes = await db.from("recipes").select("*").eq("owner_id", user.id).order("created_at", { ascending: false });
        check(recipes.error);
        return reply({ user, archive_public: user.archive_public, recipes: recipes.data ?? [] });
      }
      case "profile_save": {
        const login = str(body.login).toLowerCase();
        if ([...login].length < 3 || [...login].length > 30) fail("Логин: от 3 до 30 символов");
        const saved = await db.from("users").update({
          login, about: str(body.about).slice(0, 1000), phone: str(body.phone) || null,
          birthday: str(body.birthday) || null,
        }).eq("id", user.id);
        if (saved.error?.code === "23505") fail("Логин уже занят");
        check(saved.error);
        return reply({ ok: true });
      }
      case "archive_visibility": {
        check((await db.from("users").update({ archive_public: body.enabled === true }).eq("id", user.id)).error);
        return reply({ ok: true });
      }
      case "recipe_add": {
        const title = str(body.title);
        if (!title) fail("Введите название");
        check((await db.from("recipes").insert({
          owner_id: user.id, title, body: str(body.body), show_publicly: true,
        })).error);
        return reply({ ok: true });
      }
      case "recipe_archive":
      case "recipe_public":
      case "recipe_delete": {
        const recipeId = id(body.id);
        const recipe = await db.from("recipes").select("id,archived").eq("id", recipeId).eq("owner_id", user.id).maybeSingle();
        check(recipe.error);
        if (!recipe.data) fail("Публикация не найдена", 404);
        if (action === "recipe_delete") {
          if (!recipe.data.archived) fail("Сначала перенесите публикацию в архив");
          check((await db.from("recipes").delete().eq("id", recipeId)).error);
        } else if (action === "recipe_archive") {
          check((await db.from("recipes").update({ archived: body.enabled === true }).eq("id", recipeId)).error);
        } else {
          if (!recipe.data.archived) fail("Публикация не в архиве");
          check((await db.from("recipes").update({ show_publicly: body.enabled === true }).eq("id", recipeId)).error);
        }
        return reply({ ok: true });
      }
      case "image_upload": return reply({ path: await upload("feed-images", user.id, str(body.image)) });
      case "image_delete": {
        const path = str(body.path);
        if (!path.startsWith(`${user.id}/`)) fail("Чужое фото");
        check((await db.storage.from("feed-images").remove([path])).error);
        return reply({ ok: true });
      }
      case "post_create": {
        const categoryId = id(body.category_id);
        const row = {
          author_id: user.id, author_login: user.login, title: str(body.title),
          description: str(body.description), recipe: str(body.recipe),
          category_id: categoryId, image_path: imagePath(str(body.image_path), user.id),
        };
        if (!row.title || !row.recipe) fail("Заполните название и рецепт");
        check((await db.from("posts").insert(row)).error);
        return reply({ ok: true });
      }
      case "post_update":
      case "post_delete": {
        const postId = id(body.id);
        const existing = await db.from("posts").select("id,image_path").eq("id", postId).eq("author_id", user.id).maybeSingle();
        check(existing.error);
        if (!existing.data) fail("Пост не найден", 404);
        if (action === "post_delete") {
          check((await db.from("posts").delete().eq("id", postId)).error);
          if (existing.data.image_path.startsWith(`${user.id}/`))
            await db.storage.from("feed-images").remove([existing.data.image_path]);
        } else {
          const image = imagePath(str(body.image_path), user.id);
          if (!str(body.title) || !str(body.recipe)) fail("Заполните название и рецепт");
          check((await db.from("posts").update({
            author_login: user.login, title: str(body.title), description: str(body.description),
            recipe: str(body.recipe), category_id: id(body.category_id), image_path: image,
          }).eq("id", postId)).error);
          if (existing.data.image_path !== image && existing.data.image_path.startsWith(`${user.id}/`))
            await db.storage.from("feed-images").remove([existing.data.image_path]);
        }
        return reply({ ok: true });
      }
      case "reaction_toggle": {
        const postId = id(body.post_id);
        const emoji = str(body.emoji);
        if (!emoji || emoji.length > 16) fail("Некорректная реакция");
        const existing = await db.from("reactions").select("emoji").eq("post_id", postId).eq("user_id", user.id).maybeSingle();
        check(existing.error);
        if (existing.data?.emoji === emoji) {
          check((await db.from("reactions").delete().eq("post_id", postId).eq("user_id", user.id)).error);
        } else {
          check((await db.from("reactions").upsert({ post_id: postId, user_id: user.id, emoji })).error);
        }
        return reply({ ok: true });
      }
      case "comment_add": {
        const text = str(body.body);
        if (!text || text.length > 2000) fail("Комментарий: от 1 до 2000 символов");
        check((await db.from("comments").insert({
          post_id: id(body.post_id), author_id: user.id, author_login: user.login, body: text,
        })).error);
        return reply({ ok: true });
      }
      case "avatar_download": {
        if (!user.avatar_path) return reply({ image: null });
        const file = await db.storage.from("profile-avatars").download(user.avatar_path);
        check(file.error);
        const bytes = new Uint8Array(await file.data.arrayBuffer());
        let encoded = "";
        for (let i = 0; i < bytes.length; i += 8192)
          encoded += String.fromCharCode(...bytes.subarray(i, i + 8192));
        return reply({ image: btoa(encoded) });
      }
      case "avatar_upload": {
        const path = await upload("profile-avatars", user.id, str(body.image));
        const saved = await db.from("users").update({ avatar_path: path }).eq("id", user.id);
        if (saved.error) {
          await db.storage.from("profile-avatars").remove([path]);
          check(saved.error);
        }
        if (user.avatar_path?.startsWith(`${user.id}/`))
          await db.storage.from("profile-avatars").remove([user.avatar_path]);
        return reply({ path });
      }
      default: fail("Неизвестное действие", 404);
    }
  } catch (error) {
    const e = error as { message?: string; status?: number };
    return reply({ error: e.message ?? "Ошибка сервера" }, e.status ?? 500);
  }
});
