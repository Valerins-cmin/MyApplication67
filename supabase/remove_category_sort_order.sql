-- Existing recipe posts already reference feed_categories.id.
alter table public.feed_categories
    drop column if exists sort_order;
