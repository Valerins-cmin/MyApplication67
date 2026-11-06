-- A public archive may expose only posts whose owner permits individual display.
alter table public.recipe_posts
    add column show_publicly boolean not null default true;

alter policy recipe_posts_read on public.recipe_posts
    using (
        owner_id = (select auth.uid())
        or (
            archived
            and show_publicly
            and exists (
                select 1 from public.archive_visibility visibility
                where visibility.owner_id = recipe_posts.owner_id
                  and visibility.is_public
            )
        )
    );
