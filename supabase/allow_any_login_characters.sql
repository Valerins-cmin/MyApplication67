-- Keep login length limits while allowing Cyrillic, emoji, punctuation and spaces.
alter table public."User" drop constraint user_login_format;
alter table public."User"
    add constraint user_login_length check (char_length(login) between 3 and 30);

create function app_private.normalize_profile_login()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.login := lower(btrim(new.login));
    return new;
end;
$$;
revoke all on function app_private.normalize_profile_login() from public, anon, authenticated;
create trigger normalize_profile_login
    before insert or update of login on public."User"
    for each row execute function app_private.normalize_profile_login();

-- Feed cards and comments keep a copy of the author's login for display.
create function app_private.sync_feed_login()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.login is distinct from old.login then
        if (select auth.uid()) is not null and new.id <> (select auth.uid()) then
            raise exception 'Cannot change another profile';
        end if;
        update public.feed_posts set author_login = new.login where author_id = new.id;
        update public.feed_comments set author_login = new.login where author_id = new.id;
    end if;
    return new;
end;
$$;
revoke all on function app_private.sync_feed_login() from public, anon, authenticated;
create trigger sync_feed_login
    after update of login on public."User"
    for each row execute function app_private.sync_feed_login();
