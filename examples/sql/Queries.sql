\pset format unaligned
\pset tuples_only on
\pset fieldsep '|'
\pset null 'NULL'
\set QUIET on
BEGIN;
CREATE TEMP TABLE payments (
    id integer PRIMARY KEY,
    merchant text,
    status text,
    amount_minor bigint,
    fee_minor bigint,
    created_at timestamp NOT NULL
);
INSERT INTO payments VALUES
    (1, 'A', 'paid',     100, NULL, '2026-10-01 09:00'),
    (2, 'A', 'paid',     100,    5, '2026-10-01 09:01'),
    (3, 'A', 'pending',  300, NULL, '2026-10-01 09:02'),
    (4, 'B', 'paid',     200,   10, '2026-10-01 09:00'),
    (5, 'B', 'refunded',  50, NULL, '2026-10-01 09:03'),
    (6, 'B', NULL,        70, NULL, '2026-10-01 09:04'),
    (7, NULL, 'paid',  NULL, NULL, '2026-10-01 09:05');

-- Возвращает код ожидаемой SQL-ошибки, не останавливая остальные опыты.
CREATE FUNCTION pg_temp.query_state(q text) RETURNS text LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE q;
    RETURN 'OK';
EXCEPTION WHEN OTHERS THEN
    RETURN SQLSTATE;
END;
$$;

\echo [report]
SELECT merchant, count(*) AS paid_count, sum(amount_minor) AS total_minor
FROM payments
WHERE status = 'paid'
GROUP BY merchant
HAVING sum(amount_minor) >= 200
ORDER BY total_minor DESC, merchant ASC NULLS LAST;
SELECT 'alias in WHERE', pg_temp.query_state(
    'SELECT amount_minor * 2 AS doubled FROM payments WHERE doubled > 200');
SELECT 'ungrouped id', pg_temp.query_state(
    'SELECT merchant, id, sum(amount_minor) FROM payments GROUP BY merchant');

\echo [unknown]
SELECT NULL::bigint = NULL::bigint AS eq_null,
       NULL::text <> 'paid' AS not_paid,
       NOT (NULL::text = 'paid') AS negated,
       TRUE OR NULL::boolean AS true_or_unknown,
       FALSE AND NULL::boolean AS false_and_unknown;
SELECT 'status <> paid', string_agg(id::text, ',' ORDER BY id)
FROM payments WHERE status <> 'paid';
SELECT 'status DISTINCT FROM paid', string_agg(id::text, ',' ORDER BY id)
FROM payments WHERE status IS DISTINCT FROM 'paid';
SELECT 'status IS NULL', string_agg(id::text, ',' ORDER BY id)
FROM payments WHERE status IS NULL;

\echo [groups]
SELECT merchant, count(*) AS rows, count(amount_minor) AS known_amounts,
       sum(amount_minor) AS total_minor, round(avg(amount_minor), 2) AS average_minor
FROM payments WHERE status = 'paid'
GROUP BY merchant ORDER BY merchant ASC NULLS LAST;
SELECT count(*) AS rows, count(amount_minor) AS known_amounts,
       sum(amount_minor) AS total_minor, coalesce(sum(amount_minor), 0) AS chosen_zero
FROM payments WHERE status = 'missing';
SELECT merchant, count(*) AS all_count,
       count(*) FILTER (WHERE status = 'paid') AS paid_count,
       sum(amount_minor) FILTER (WHERE status = 'paid') AS paid_total
FROM payments GROUP BY merchant ORDER BY merchant ASC NULLS LAST;

\echo [distinct]
SELECT count(*) AS paid_rows, sum(amount_minor) AS paid_total,
       sum(DISTINCT amount_minor) AS distinct_amount_total
FROM payments WHERE status = 'paid';
SELECT DISTINCT merchant FROM payments WHERE status = 'paid'
ORDER BY merchant ASC NULLS LAST;
SELECT count(DISTINCT merchant) AS known_merchants FROM payments WHERE status = 'paid';
-- DISTINCT ON оставляет первый платёж каждой группы в данном ORDER BY.
SELECT DISTINCT ON (merchant) merchant, id, status
FROM payments WHERE status = 'paid'
ORDER BY merchant ASC NULLS LAST, created_at DESC, id DESC;

\echo [order]
SELECT id, amount_minor FROM payments WHERE status = 'paid'
ORDER BY amount_minor DESC NULLS LAST, id ASC LIMIT 3;
SELECT id, amount_minor FROM payments WHERE status = 'paid'
ORDER BY amount_minor DESC, id ASC LIMIT 1;
ROLLBACK;
