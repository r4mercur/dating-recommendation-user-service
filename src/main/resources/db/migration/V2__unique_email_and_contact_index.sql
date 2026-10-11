-- Emails are compared case-insensitively for login and registration.
update users set email = lower(trim(email)) where email is not null;

create unique index ux_users_email on users (lower(email));

create index ix_contacts_user_id on contacts (user_id);
