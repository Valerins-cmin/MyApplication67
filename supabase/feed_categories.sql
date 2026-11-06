-- Recipe categories are read by everyone and managed through migrations.
create table public.feed_categories (
    id smallint primary key,
    slug text not null unique,
    name text not null unique
);

alter table public.feed_categories enable row level security;
revoke all on public.feed_categories from anon, authenticated;
grant select on public.feed_categories to anon, authenticated;
create policy feed_categories_read on public.feed_categories
    for select to anon, authenticated using (true);

insert into public.feed_categories (id, slug, name) values
    (1, 'breakfast', 'Завтраки'),
    (2, 'lunch', 'Обеды'),
    (3, 'dinner', 'Ужины'),
    (4, 'soups', 'Супы'),
    (5, 'salads', 'Салаты'),
    (6, 'appetizers', 'Закуски'),
    (7, 'main_dishes', 'Основные блюда'),
    (8, 'side_dishes', 'Гарниры'),
    (9, 'meat', 'Блюда из мяса'),
    (10, 'fish', 'Блюда из рыбы'),
    (11, 'vegetarian', 'Вегетарианское'),
    (12, 'desserts', 'Десерты'),
    (13, 'drinks', 'Напитки'),
    (14, 'sauces', 'Соусы'),
    (15, 'baking', 'Выпечка');

alter table public.feed_posts
    add column category_id smallint references public.feed_categories (id);
create index feed_posts_category_idx on public.feed_posts (category_id);
