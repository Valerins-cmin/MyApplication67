-- Supabase Auth stores password hashes. This table stores only public profile fields.
create table public."User" (
    id uuid primary key references auth.users (id) on delete cascade,
    login text not null unique,
    gmail text not null unique,
    date_reg timestamptz not null default now(),
    constraint user_login_format check (login ~ '^[a-z0-9_]{3,30}$')
);

alter table public."User" enable row level security;
revoke all on public."User" from anon, authenticated;
grant select on public."User" to authenticated;

create policy user_select_own_profile
    on public."User"
    for select
    to authenticated
    using (id = (select auth.uid()));

create schema if not exists app_private;
revoke all on schema app_private from public, anon, authenticated;

create function app_private.create_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_login text;
begin
    normalized_login := lower(btrim(new.raw_user_meta_data ->> 'login'));
    if new.email is null or normalized_login is null or normalized_login = '' then
        return new;
    end if;

    insert into public."User" (id, login, gmail, date_reg)
    values (new.id, normalized_login, lower(new.email), new.created_at);
    return new;
end;
$$;

revoke all on function app_private.create_user_profile() from public, anon, authenticated;

create trigger on_auth_user_created_create_profile
    after insert on auth.users
    for each row execute function app_private.create_user_profile();
