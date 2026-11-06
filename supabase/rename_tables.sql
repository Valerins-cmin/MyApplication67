-- Apply after the earlier schema scripts. Existing rows, grants, RLS policies,
-- indexes, and foreign keys stay attached to the renamed tables.
alter table public."User" rename to users;
alter table public.feed_categories rename to categories;
alter table public.feed_posts rename to posts;
alter table public.feed_comments rename to comments;
alter table public.feed_reactions rename to reactions;
alter table public.recipe_posts rename to recipes;
alter table public.archive_visibility rename to archive_settings;

-- PL/pgSQL function bodies store table names as text and need explicit updates.
create or replace function app_private.create_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    normalized_login text;
begin
    normalized_login := lower(btrim(new.raw_user_meta_data ->> 'login'));
    if new.email is null or normalized_login is null or normalized_login = '' then
        return new;
    end if;

    insert into public.users (id, login, gmail, date_reg)
    values (new.id, normalized_login, lower(new.email), new.created_at);
    return new;
end;
$function$;

create or replace function app_private.sync_feed_login()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
    if new.login is distinct from old.login then
        if (select auth.uid()) is not null and new.id <> (select auth.uid()) then
            raise exception 'Cannot change another profile';
        end if;
        update public.posts set author_login = new.login where author_id = new.id;
        update public.comments set author_login = new.login where author_id = new.id;
    end if;
    return new;
end;
$function$;

create or replace function public.sync_feed_comment_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
    if tg_op = 'INSERT' then
        update public.posts set comment_count = comment_count + 1 where id = new.post_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.posts set comment_count = comment_count - 1 where id = old.post_id;
        return old;
    elsif new.post_id is distinct from old.post_id then
        update public.posts set comment_count = comment_count - 1 where id = old.post_id;
        update public.posts set comment_count = comment_count + 1 where id = new.post_id;
    end if;
    return new;
end;
$function$;

notify pgrst, 'reload schema';
