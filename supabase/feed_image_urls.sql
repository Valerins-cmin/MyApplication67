-- A feed image may be an owner's Storage path or a public HTTPS image URL.
alter policy feed_posts_insert_own on public.feed_posts
    with check (
        author_id = (select auth.uid())
        and author_login = (
            select login from public."User" where id = (select auth.uid())
        )
        and (
            image_path like (select auth.uid())::text || '/%'
            or (
                char_length(image_path) between 12 and 2048
                and image_path ~* '^https://[^[:space:]/?#]+([/?#][^[:space:]]*)?$'
            )
        )
    );
