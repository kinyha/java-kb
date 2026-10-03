-- Each view is a solution. The checker compares its bag of rows with explicit VALUES.
CREATE TEMP VIEW task_duplicates AS
SELECT account_id, request_id, count(*) AS attempts
FROM payments
WHERE request_id IS NOT NULL
GROUP BY account_id, request_id
HAVING count(*) > 1;

CREATE TEMP VIEW task_second AS
WITH amounts AS (
  SELECT DISTINCT account_id, amount
  FROM payments WHERE status = 'PAID'
), ranked AS (
  SELECT account_id, amount,
         row_number() OVER (PARTITION BY account_id ORDER BY amount DESC) AS rn
  FROM amounts
)
SELECT a.id AS account_id, r.amount AS second_amount
FROM accounts a LEFT JOIN ranked r ON r.account_id = a.id AND r.rn = 2;

CREATE TEMP VIEW task_top AS
WITH ranked AS (
  SELECT account_id, id AS payment_id, amount,
         row_number() OVER (
           PARTITION BY account_id ORDER BY created_at DESC, id DESC
         ) AS position
  FROM payments WHERE status = 'PAID'
)
SELECT account_id, payment_id, amount, position
FROM ranked WHERE position <= 2;

CREATE TEMP VIEW task_totals AS
WITH refunds_per_payment AS (
  SELECT payment_id, sum(amount) AS refunded
  FROM refunds GROUP BY payment_id
), totals AS (
  SELECT p.account_id,
         count(*) FILTER (WHERE p.status = 'PAID') AS paid_count,
         count(*) FILTER (WHERE p.status = 'FAILED') AS failed_count,
         sum(p.amount) FILTER (WHERE p.status = 'PAID') AS gross,
         sum(coalesce(r.refunded, 0)) FILTER (WHERE p.status = 'PAID') AS refunded
  FROM payments p LEFT JOIN refunds_per_payment r ON r.payment_id = p.id
  GROUP BY p.account_id
)
SELECT a.id AS account_id,
       coalesce(t.paid_count, 0) AS paid_count,
       coalesce(t.failed_count, 0) AS failed_count,
       coalesce(t.gross, 0) AS gross,
       coalesce(t.refunded, 0) AS refunded,
       coalesce(t.gross, 0) - coalesce(t.refunded, 0) AS net
FROM accounts a LEFT JOIN totals t ON t.account_id = a.id;

CREATE TEMP VIEW task_latest AS
WITH known_requests AS (
  SELECT p.*,
         row_number() OVER (
           PARTITION BY account_id, request_id
           ORDER BY created_at DESC, id DESC
         ) AS rn
  FROM payments p WHERE request_id IS NOT NULL
)
SELECT account_id, request_id, id AS payment_id, status
FROM known_requests WHERE rn = 1
UNION ALL
SELECT account_id, request_id, id AS payment_id, status
FROM payments WHERE request_id IS NULL;
