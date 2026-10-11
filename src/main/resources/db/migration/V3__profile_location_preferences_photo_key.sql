-- Profile: bio and birth date (age is derived from the birth date from now on)
alter table users add column bio varchar(1000);
alter table users add column birth_date date;
update users set birth_date = current_date - make_interval(years => age) where age is not null;
alter table users drop column age;

-- Location resolved from the user's address (postal code -> coordinates)
alter table users add column latitude double precision;
alter table users add column longitude double precision;

-- Search preferences
alter table users add column min_age integer not null default 18;
alter table users add column max_age integer not null default 99;
alter table users add column max_distance_km integer not null default 50;

create table user_seeking_genders
(
    user_id bigint      not null references users (id) on delete cascade,
    gender  varchar(20) not null,
    primary key (user_id, gender)
);

insert into user_seeking_genders (user_id, gender)
select u.id, g.gender
from users u
         cross join (values ('MALE'), ('FEMALE'), ('DIVERSE')) as g(gender);

-- Photos: store the MinIO object key instead of an expiring presigned URL.
-- Old values look like http(s)://host/<bucket>/<object-key>?X-Amz-...
alter table users add column photo_key varchar(512);
update users
set photo_key = replace(substring(photo from '^https?://[^/]+/[^/]+/([^?]+)'), '%20', ' ')
where photo is not null;
alter table users drop column photo;
