-- Browsery: skema sinkronisasi (prefix browsery_ agar terpisah dari proyek lain di project ini).
-- Semua tabel: RLS aktif, hanya role authenticated, kepemilikan lewat auth.uid() = user_id.
-- Soft delete (deleted_at) untuk tombstone; updated_at UTC untuk delta sync.

create or replace function public.browsery_touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create table public.browsery_profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  display_name text check (display_name is null or char_length(display_name) <= 100),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.browsery_devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  platform text not null default 'android' check (char_length(platform) <= 30),
  last_sync_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.browsery_bookmarks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  url text not null check (char_length(url) between 1 and 2048),
  title text check (title is null or char_length(title) <= 500),
  folder text check (folder is null or char_length(folder) <= 200),
  position integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.browsery_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  url text not null check (char_length(url) between 1 and 2048),
  title text check (title is null or char_length(title) <= 500),
  visited_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.browsery_open_tabs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  device_id uuid references public.browsery_devices (id) on delete cascade,
  url text not null check (char_length(url) between 1 and 2048),
  title text check (title is null or char_length(title) <= 500),
  position integer not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.browsery_settings (
  user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  data jsonb not null default '{}'::jsonb check (pg_column_size(data) <= 65536),
  updated_at timestamptz not null default now()
);

create index browsery_devices_user_idx on public.browsery_devices (user_id);
create index browsery_bookmarks_user_updated_idx on public.browsery_bookmarks (user_id, updated_at);
create index browsery_history_user_visited_idx on public.browsery_history (user_id, visited_at desc);
create index browsery_history_user_updated_idx on public.browsery_history (user_id, updated_at);
create index browsery_open_tabs_user_updated_idx on public.browsery_open_tabs (user_id, updated_at);
create index browsery_open_tabs_device_idx on public.browsery_open_tabs (device_id);

create trigger browsery_profiles_touch before update on public.browsery_profiles
  for each row execute function public.browsery_touch_updated_at();
create trigger browsery_devices_touch before update on public.browsery_devices
  for each row execute function public.browsery_touch_updated_at();
create trigger browsery_bookmarks_touch before update on public.browsery_bookmarks
  for each row execute function public.browsery_touch_updated_at();
create trigger browsery_history_touch before update on public.browsery_history
  for each row execute function public.browsery_touch_updated_at();
create trigger browsery_open_tabs_touch before update on public.browsery_open_tabs
  for each row execute function public.browsery_touch_updated_at();
create trigger browsery_settings_touch before update on public.browsery_settings
  for each row execute function public.browsery_touch_updated_at();

alter table public.browsery_profiles enable row level security;
alter table public.browsery_devices enable row level security;
alter table public.browsery_bookmarks enable row level security;
alter table public.browsery_history enable row level security;
alter table public.browsery_open_tabs enable row level security;
alter table public.browsery_settings enable row level security;

revoke all on public.browsery_profiles, public.browsery_devices, public.browsery_bookmarks,
  public.browsery_history, public.browsery_open_tabs, public.browsery_settings from anon;
revoke all on public.browsery_profiles, public.browsery_devices, public.browsery_bookmarks,
  public.browsery_history, public.browsery_open_tabs, public.browsery_settings from public;
grant select, insert, update, delete on public.browsery_profiles, public.browsery_devices,
  public.browsery_bookmarks, public.browsery_history, public.browsery_open_tabs,
  public.browsery_settings to authenticated;

create policy "profiles_select_own" on public.browsery_profiles for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "profiles_insert_own" on public.browsery_profiles for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "profiles_update_own" on public.browsery_profiles for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "profiles_delete_own" on public.browsery_profiles for delete to authenticated
  using ((select auth.uid()) = user_id);

create policy "settings_select_own" on public.browsery_settings for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "settings_insert_own" on public.browsery_settings for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "settings_update_own" on public.browsery_settings for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "settings_delete_own" on public.browsery_settings for delete to authenticated
  using ((select auth.uid()) = user_id);

create policy "devices_select_own" on public.browsery_devices for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "devices_insert_own" on public.browsery_devices for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "devices_update_own" on public.browsery_devices for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "devices_delete_own" on public.browsery_devices for delete to authenticated
  using ((select auth.uid()) = user_id);

create policy "bookmarks_select_own" on public.browsery_bookmarks for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "bookmarks_insert_own" on public.browsery_bookmarks for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "bookmarks_update_own" on public.browsery_bookmarks for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "bookmarks_delete_own" on public.browsery_bookmarks for delete to authenticated
  using ((select auth.uid()) = user_id);

create policy "history_select_own" on public.browsery_history for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "history_insert_own" on public.browsery_history for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "history_update_own" on public.browsery_history for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "history_delete_own" on public.browsery_history for delete to authenticated
  using ((select auth.uid()) = user_id);

create policy "open_tabs_select_own" on public.browsery_open_tabs for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "open_tabs_insert_own" on public.browsery_open_tabs for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "open_tabs_update_own" on public.browsery_open_tabs for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "open_tabs_delete_own" on public.browsery_open_tabs for delete to authenticated
  using ((select auth.uid()) = user_id);
