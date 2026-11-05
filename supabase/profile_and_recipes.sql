-- Private account details. Only the owner may read and change these columns.
alter table public."User"
    add column if not exists about text not null default '',
    add column if not exists phone text,
    add column if not exists birthday date;

alter table public."User"
    add constraint user_about_length check (char_length(about) <= 500),
    add constraint user_phone_length check (phone is null or char_length(phone) <= 40);

grant update (about, phone, birthday) on public."User" to authenticated;

create policy user_update_own_profile
    on public."User" for update to authenticated
    using (id = (select auth.uid()))
    with check (id = (select auth.uid()));

-- Public visibility is one setting per owner. Missing rows mean private.
create table public.archive_visibility (
    owner_id uuid primary key references auth.users (id) on delete cascade,
    is_public boolean not null default false
);

alter table public.archive_visibility enable row level security;
revoke all on public.archive_visibility from anon, authenticated;
grant select on public.archive_visibility to anon, authenticated;
grant insert, update on public.archive_visibility to authenticated;

create policy archive_visibility_read
    on public.archive_visibility for select to anon, authenticated
    using (true);
create policy archive_visibility_insert_own
    on public.archive_visibility for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy archive_visibility_update_own
    on public.archive_visibility for update to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));

create table public.recipe_posts (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users (id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 120),
    body text not null default '' check (char_length(body) <= 10000),
    archived boolean not null default false,
    created_at timestamptz not null default now()
);

create index recipe_posts_owner_section_created_idx
    on public.recipe_posts (owner_id, archived, created_at desc);

alter table public.recipe_posts enable row level security;
revoke all on public.recipe_posts from anon, authenticated;
grant select on public.recipe_posts to anon, authenticated;
grant insert, update, delete on public.recipe_posts to authenticated;

create policy recipe_posts_read
    on public.recipe_posts for select to anon, authenticated
    using (
        owner_id = (select auth.uid())
        or (
            archived
            and exists (
                select 1 from public.archive_visibility visibility
                where visibility.owner_id = recipe_posts.owner_id
                  and visibility.is_public
            )
        )
    );
create policy recipe_posts_insert_own
    on public.recipe_posts for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy recipe_posts_update_own
    on public.recipe_posts for update to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));
create policy recipe_posts_delete_own
    on public.recipe_posts for delete to authenticated
    using (owner_id = (select auth.uid()));
