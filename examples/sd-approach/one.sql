\set c random(1, 20000000)
insert into op values (:c, now(), md5(random()::text), -1250, 'HOLD', 'Coffee Lab');
