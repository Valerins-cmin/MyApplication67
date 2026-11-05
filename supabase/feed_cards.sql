-- Public recipe feed. Private recipe_posts and profile details remain unchanged.
create table public.feed_posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references auth.users (id) on delete cascade,
    author_login text not null,
    title text not null check (char_length(btrim(title)) between 1 and 120),
    description text not null check (char_length(btrim(description)) between 1 and 1000),
    recipe text not null check (char_length(btrim(recipe)) between 1 and 10000),
    image_path text not null,
    created_at timestamptz not null default now()
);

create index feed_posts_created_idx on public.feed_posts (created_at desc);
create index feed_posts_author_idx on public.feed_posts (author_id);
alter table public.feed_posts enable row level security;
revoke all on public.feed_posts from anon, authenticated;
grant select on public.feed_posts to anon, authenticated;
grant insert on public.feed_posts to authenticated;

create policy feed_posts_read on public.feed_posts
    for select to anon, authenticated using (true);
create policy feed_posts_insert_own on public.feed_posts
    for insert to authenticated
    with check (
        author_id = (select auth.uid())
        and author_login = (
            select login from public."User" where id = (select auth.uid())
        )
        and image_path like (select auth.uid())::text || '/%'
    );

create table public.feed_reactions (
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    emoji text not null check (emoji in ('❤️', '🔥', '😍', '👍', '👏', '😋')),
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create index feed_reactions_user_idx on public.feed_reactions (user_id);

alter table public.feed_reactions enable row level security;
revoke all on public.feed_reactions from anon, authenticated;
grant select on public.feed_reactions to anon, authenticated;
grant insert, update, delete on public.feed_reactions to authenticated;

create policy feed_reactions_read on public.feed_reactions
    for select to anon, authenticated using (true);
create policy feed_reactions_insert_own on public.feed_reactions
    for insert to authenticated with check (user_id = (select auth.uid()));
create policy feed_reactions_update_own on public.feed_reactions
    for update to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));
create policy feed_reactions_delete_own on public.feed_reactions
    for delete to authenticated using (user_id = (select auth.uid()));

create table public.feed_comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    author_id uuid not null references auth.users (id) on delete cascade,
    author_login text not null,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);

create index feed_comments_post_created_idx
    on public.feed_comments (post_id, created_at);
create index feed_comments_author_idx on public.feed_comments (author_id);
alter table public.feed_comments enable row level security;
revoke all on public.feed_comments from anon, authenticated;
grant select on public.feed_comments to anon, authenticated;
grant insert on public.feed_comments to authenticated;

create policy feed_comments_read on public.feed_comments
    for select to anon, authenticated using (true);
create policy feed_comments_insert_own on public.feed_comments
    for insert to authenticated
    with check (
        author_id = (select auth.uid())
        and author_login = (
            select login from public."User" where id = (select auth.uid())
        )
    );

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('feed-images', 'feed-images', true, 5242880, array['image/jpeg'])
on conflict (id) do nothing;

create policy feed_images_read on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'feed-images');
create policy feed_images_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'feed-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );
