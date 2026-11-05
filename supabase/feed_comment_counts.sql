-- Keep the displayed count in sync without downloading every comment.
alter table public.feed_posts
    add column comment_count integer not null default 0 check (comment_count >= 0);

update public.feed_posts as post
set comment_count = (
    select count(*)::integer
    from public.feed_comments as comment
    where comment.post_id = post.id
);

create function public.sync_feed_comment_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        update public.feed_posts set comment_count = comment_count + 1 where id = new.post_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.feed_posts set comment_count = comment_count - 1 where id = old.post_id;
        return old;
    elsif new.post_id is distinct from old.post_id then
        update public.feed_posts set comment_count = comment_count - 1 where id = old.post_id;
        update public.feed_posts set comment_count = comment_count + 1 where id = new.post_id;
    end if;
    return new;
end;
$$;

revoke all on function public.sync_feed_comment_count() from public, anon, authenticated;
create trigger feed_comments_sync_count
    after insert or delete or update of post_id on public.feed_comments
    for each row execute function public.sync_feed_comment_count();
