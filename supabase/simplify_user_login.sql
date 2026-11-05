-- Permit any login of at least three Latin letters, regardless of case at input.
alter table public."User" drop constraint user_login_format;
alter table public."User"
    add constraint user_login_format check (login ~ '^[a-z]{3,}$');
