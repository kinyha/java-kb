CREATE TEMP TABLE accounts (id integer PRIMARY KEY, owner text NOT NULL);
CREATE TEMP TABLE payments (
  id integer PRIMARY KEY,
  account_id integer NOT NULL REFERENCES accounts(id),
  request_id text,
  amount bigint CHECK (amount >= 0),
  status text NOT NULL CHECK (status IN ('PAID', 'FAILED')),
  created_at timestamp NOT NULL,
  CHECK (status <> 'PAID' OR amount IS NOT NULL)
);
CREATE TEMP TABLE refunds (
  id integer PRIMARY KEY,
  payment_id integer NOT NULL REFERENCES payments(id),
  amount bigint NOT NULL CHECK (amount > 0)
);
INSERT INTO accounts VALUES
  (1,'Alice'), (2,'Bob'), (3,'Carol'), (4,'Dave'), (5,'Eve');
INSERT INTO payments VALUES
  (101,1,'r-a',100,'PAID',  '2026-01-01 10:00'),
  (102,1,'r-a',100,'FAILED','2026-01-02 10:00'),
  (103,1,'r-b',300,'PAID',  '2026-01-03 10:00'),
  (104,1,'r-c',300,'PAID',  '2026-01-03 10:00'),
  (105,1,NULL,  50,'PAID',  '2026-01-04 10:00'),
  (106,1,NULL,  80,'FAILED','2026-01-04 11:00'),
  (201,2,'r-d',200,'PAID',  '2026-01-01 10:00'),
  (202,2,'r-d',200,'PAID',  '2026-01-02 10:00'),
  (203,2,'r-e',NULL,'FAILED','2026-01-03 10:00'),
  (204,2,'r-f',150,'PAID',  '2026-01-03 09:00'),
  (301,3,'r-a',500,'PAID',  '2026-01-05 10:00'),
  (302,3,'r-g',500,'PAID',  '2026-01-05 10:00'),
  (501,5,NULL,NULL,'FAILED','2026-01-02 12:00');
INSERT INTO refunds VALUES (1,101,30), (2,101,20), (3,103,100), (4,201,10);
