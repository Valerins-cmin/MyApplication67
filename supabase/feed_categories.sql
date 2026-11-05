-- Recipe categories are read by everyone and managed through migrations.
create table public.feed_categories (
    id smallint primary key,
    slug text not null unique,
    name text not null unique,
    sort_order smallint not null unique
);

alter table public.feed_categories enable row level security;
revoke all on public.feed_categories from anon, authenticated;
grant select on public.feed_categories to anon, authenticated;
create policy feed_categories_read on public.feed_categories
    for select to anon, authenticated using (true);

insert into public.feed_categories (id, slug, name, sort_order) values
    (1, 'breakfast', 'Завтраки', 1),
    (2, 'lunch', 'Обеды', 2),
    (3, 'dinner', 'Ужины', 3),
    (4, 'soups', 'Супы', 4),
    (5, 'salads', 'Салаты', 5),
    (6, 'appetizers', 'Закуски', 6),
    (7, 'main_dishes', 'Основные блюда', 7),
    (8, 'side_dishes', 'Гарниры', 8),
    (9, 'meat', 'Блюда из мяса', 9),
    (10, 'fish', 'Блюда из рыбы', 10),
    (11, 'vegetarian', 'Вегетарианское', 11),
    (12, 'desserts', 'Десерты', 12),
    (13, 'drinks', 'Напитки', 13),
    (14, 'sauces', 'Соусы', 14),
    (15, 'baking', 'Выпечка', 15);

alter table public.feed_posts
    add column category_id smallint references public.feed_categories (id);
create index feed_posts_category_idx on public.feed_posts (category_id);
