-- Login remains unique and limited by the existing user_login_format constraint.
grant update (login) on public."User" to authenticated;
