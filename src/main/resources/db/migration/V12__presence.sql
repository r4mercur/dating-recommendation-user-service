-- Online status: last activity (API request or open WebSocket) and whether others may see it.
alter table users add column last_active_at timestamptz;
alter table users add column show_online_status boolean not null default true;
