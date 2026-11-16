-- Archive visibility is one flag per user, so it lives on the user row.
alter table public.users add column if not exists archive_public boolean not null default false;
update public.users u set archive_public = a.is_public
  from public.archive_settings a where a.owner_id = u.id;
drop table public.archive_settings;
