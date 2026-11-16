-- Existing private recipes stay private. New recipes start published.
update public.recipes set show_publicly = false;
alter table public.recipes alter column show_publicly set default true;
