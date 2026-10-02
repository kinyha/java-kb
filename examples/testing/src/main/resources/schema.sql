create table if not exists payment (
  id bigserial primary key,
  account varchar(64) not null,
  cents bigint not null,
  status varchar(32) not null
);
