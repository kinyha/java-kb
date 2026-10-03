CREATE FUNCTION pg_temp.assert_same(label text, actual regclass, expected regclass)
RETURNS text LANGUAGE plpgsql AS $$
DECLARE different boolean;
BEGIN
  EXECUTE format(
    'SELECT EXISTS ((TABLE %s EXCEPT ALL TABLE %s) UNION ALL (TABLE %s EXCEPT ALL TABLE %s))',
    actual, expected, expected, actual) INTO different;
  IF different THEN RAISE EXCEPTION '%: unexpected rows (EXCEPT ALL differs)', label; END IF;
  RETURN label || ': OK';
END $$;
CREATE TEMP TABLE expected_duplicates (account_id integer, request_id text, attempts bigint);
INSERT INTO expected_duplicates VALUES (1,'r-a',2), (2,'r-d',2);
CREATE TEMP TABLE expected_second (account_id integer, second_amount bigint);
INSERT INTO expected_second VALUES (1,100), (2,150), (3,NULL), (4,NULL), (5,NULL);
CREATE TEMP TABLE expected_top (account_id integer, payment_id integer, amount bigint, position bigint);
INSERT INTO expected_top VALUES (1,105,50,1), (1,104,300,2), (2,204,150,1),
  (2,202,200,2), (3,302,500,1), (3,301,500,2);
CREATE TEMP TABLE expected_totals (
  account_id integer, paid_count bigint, failed_count bigint,
  gross numeric, refunded numeric, net numeric
);
INSERT INTO expected_totals VALUES
  (1,4,2,750,150,600), (2,3,1,550,10,540), (3,2,0,1000,0,1000), (4,0,0,0,0,0), (5,0,1,0,0,0);
CREATE TEMP TABLE expected_latest (account_id integer, request_id text, payment_id integer, status text);
INSERT INTO expected_latest VALUES
  (1,'r-a',102,'FAILED'), (1,'r-b',103,'PAID'), (1,'r-c',104,'PAID'),
  (1,NULL,105,'PAID'), (1,NULL,106,'FAILED'),
  (2,'r-d',202,'PAID'), (2,'r-e',203,'FAILED'), (2,'r-f',204,'PAID'),
  (3,'r-a',301,'PAID'), (3,'r-g',302,'PAID'), (5,NULL,501,'FAILED');
SELECT pg_temp.assert_same('base duplicates', 'task_duplicates', 'expected_duplicates');
SELECT pg_temp.assert_same('base second', 'task_second', 'expected_second');
SELECT pg_temp.assert_same('base top', 'task_top', 'expected_top');
SELECT pg_temp.assert_same('base totals', 'task_totals', 'expected_totals');
SELECT pg_temp.assert_same('base latest', 'task_latest', 'expected_latest');

-- All accounts remain, but there are no attempts at all.
BEGIN;
TRUNCATE refunds, payments;
TRUNCATE expected_duplicates, expected_top, expected_latest;
UPDATE expected_second SET second_amount = NULL;
UPDATE expected_totals SET paid_count=0, failed_count=0, gross=0, refunded=0, net=0;
SELECT pg_temp.assert_same('empty duplicates', 'task_duplicates', 'expected_duplicates');
SELECT pg_temp.assert_same('empty second', 'task_second', 'expected_second');
SELECT pg_temp.assert_same('empty top', 'task_top', 'expected_top');
SELECT pg_temp.assert_same('empty totals', 'task_totals', 'expected_totals');
SELECT pg_temp.assert_same('empty latest', 'task_latest', 'expected_latest');

-- One populated account: zero is a real amount, equal dates have distinct ids,
-- NULL request ids must not collapse, and two refunds must not multiply gross.
INSERT INTO payments VALUES
  (601,4,'edge',0, 'PAID',  '2026-01-10 10:00'),
  (602,4,'edge',10,'PAID',  '2026-01-10 10:00'),
  (603,4,'edge',10,'PAID',  '2026-01-10 10:00'),
  (604,4,NULL,NULL,'FAILED','2026-01-11 10:00'),
  (605,4,NULL,NULL,'FAILED','2026-01-11 10:00'),
  (606,4,'edge',0,'PAID','2026-01-09 10:00'),
  (701,3,'solo',99,'PAID','2026-01-12 10:00');
INSERT INTO refunds VALUES (11,602,3), (12,602,4);
INSERT INTO expected_duplicates VALUES (4,'edge',4);
UPDATE expected_second SET second_amount=0 WHERE account_id=4;
INSERT INTO expected_top VALUES (4,603,10,1), (4,602,10,2), (3,701,99,1);
UPDATE expected_totals SET paid_count=4, failed_count=2, gross=20, refunded=7, net=13 WHERE account_id=4;
UPDATE expected_totals SET paid_count=1, gross=99, net=99 WHERE account_id=3;
INSERT INTO expected_latest VALUES (3,'solo',701,'PAID'), (4,'edge',603,'PAID'), (4,NULL,604,'FAILED'), (4,NULL,605,'FAILED');
SELECT pg_temp.assert_same('edges duplicates', 'task_duplicates', 'expected_duplicates');
SELECT pg_temp.assert_same('edges second', 'task_second', 'expected_second');
SELECT pg_temp.assert_same('edges top', 'task_top', 'expected_top');
SELECT pg_temp.assert_same('edges totals', 'task_totals', 'expected_totals');
SELECT pg_temp.assert_same('edges latest', 'task_latest', 'expected_latest');
ROLLBACK;
