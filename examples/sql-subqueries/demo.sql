\pset format unaligned
\pset footer off
\pset null NULL
SET statement_timeout = '5s';
SELECT current_setting('server_version') AS postgres_version;
CREATE TEMP TABLE accounts (id integer PRIMARY KEY, owner text NOT NULL);
CREATE TEMP TABLE payments (
  id integer PRIMARY KEY,
  account_id integer NOT NULL REFERENCES accounts(id),
  amount bigint NOT NULL,
  status text NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO accounts VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol');
INSERT INTO payments VALUES
  (101, 1, 100, 'PAID', '2026-01-01 10:00'),
  (102, 1, 300, 'FAILED', '2026-01-02 10:00'),
  (103, 1, 250, 'PAID', '2026-01-03 10:00'),
  (201, 2, 50, 'PAID', '2026-01-03 10:00'),
  (202, 2, 70, 'PAID', '2026-01-03 10:00');
CREATE INDEX payments_account_latest ON payments(account_id, created_at DESC, id DESC)
  WHERE status = 'PAID';
ANALYZE accounts;
ANALYZE payments;

\echo [scalar]
SELECT a.id,
       (SELECT max(p.amount) FROM payments p
        WHERE p.account_id = a.id AND p.status = 'PAID') AS max_paid,
       (SELECT p.amount FROM payments p
        WHERE p.account_id = a.id AND p.id = 103) AS amount_103
FROM accounts a ORDER BY a.id;
-- Catch only the intentional cardinality error; other errors still fail the run.
DO $$
BEGIN
  PERFORM (SELECT p.amount FROM payments p WHERE p.account_id = 1);
  RAISE EXCEPTION 'expected cardinality_violation';
EXCEPTION WHEN cardinality_violation THEN
  RAISE NOTICE 'scalar multirow SQLSTATE = %', SQLSTATE;
END $$;

\echo [exists]
SELECT a.id,
       EXISTS (SELECT 1 FROM payments p
               WHERE p.account_id = a.id AND p.status = 'PAID') AS has_paid,
       EXISTS (SELECT count(*) FROM payments p
               WHERE p.account_id = a.id AND p.status = 'PAID') AS count_row_exists
FROM accounts a ORDER BY a.id;

\echo [lateral]
SELECT a.id AS account_id, p.id AS payment_id, p.amount
FROM accounts a
LEFT JOIN LATERAL (
  SELECT p.id, p.amount, p.created_at FROM payments p
  WHERE p.account_id = a.id AND p.status = 'PAID'
  ORDER BY p.created_at DESC, p.id DESC
  LIMIT 2
) p ON true
ORDER BY a.id, p.created_at DESC, p.id DESC;

-- A larger, deterministic relation makes the optimization fence visible.
CREATE TEMP TABLE ledger (id integer PRIMARY KEY, amount integer NOT NULL);
INSERT INTO ledger SELECT n, n % 1000 FROM generate_series(1, 20000) n;
ANALYZE ledger;
\pset tuples_only on
\echo [cte folded]
EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)
WITH c AS (SELECT id, amount FROM ledger)
SELECT amount FROM c WHERE id = 42;
\echo [cte materialized]
EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)
WITH c AS MATERIALIZED (SELECT id, amount FROM ledger)
SELECT amount FROM c WHERE id = 42;
\echo [cte not materialized, two references]
EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)
WITH c AS NOT MATERIALIZED (SELECT id, amount FROM ledger)
SELECT (SELECT amount FROM c WHERE id = 42) AS first,
       (SELECT amount FROM c WHERE id = 43) AS second;
\pset tuples_only off

CREATE TEMP TABLE operation_edges (
  parent_id integer NOT NULL, child_id integer NOT NULL,
  PRIMARY KEY (parent_id, child_id)
);
INSERT INTO operation_edges VALUES (101,301), (101,302), (301,401);
\echo [recursive tree]
WITH RECURSIVE related(id, depth) AS (
  VALUES (101, 0)
  UNION ALL
  SELECT e.child_id, r.depth + 1
  FROM related r JOIN operation_edges e ON e.parent_id = r.id
)
SELECT id, depth FROM related ORDER BY depth, id;

INSERT INTO operation_edges VALUES (401,101);
\echo [cycle]
WITH RECURSIVE related(id, depth) AS (
  VALUES (101, 0)
  UNION ALL
  SELECT e.child_id, r.depth + 1
  FROM related r JOIN operation_edges e ON e.parent_id = r.id
) CYCLE id SET is_cycle USING path
SELECT id, depth, is_cycle FROM related ORDER BY depth, id;

\echo [union, id only]
WITH RECURSIVE related(id) AS (
  VALUES (101)
  UNION
  SELECT e.child_id
  FROM related r JOIN operation_edges e ON e.parent_id = r.id
)
SELECT id FROM related ORDER BY id;

\echo [union with changing depth, bounded]
WITH RECURSIVE related(id, depth) AS (
  VALUES (101, 0)
  UNION
  SELECT e.child_id, r.depth + 1
  FROM related r JOIN operation_edges e ON e.parent_id = r.id
  WHERE r.depth < 4 -- demonstration bound, not cycle detection
)
SELECT id, depth FROM related ORDER BY depth, id;
