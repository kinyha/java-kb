\set c random(1, 20000000)
insert into op select :c + g, now(), md5(random()::text), -1250, 'HOLD', 'Coffee Lab' from generate_series(1, 100) g;
