-- An unpublished archive setting is visible only to its owner.
drop policy archive_visibility_read on public.archive_visibility;
create policy archive_visibility_read
    on public.archive_visibility for select to anon, authenticated
    using (owner_id = (select auth.uid()) or is_public);
