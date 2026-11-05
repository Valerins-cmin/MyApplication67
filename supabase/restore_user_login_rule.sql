-- Restore the original login rule; the requested minimum concerns passwords.
alter table public."User" drop constraint user_login_format;
alter table public."User"
    add constraint user_login_format check (login ~ '^[a-z0-9_]{3,30}$');
