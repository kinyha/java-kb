\pset pager off
\pset null NULL

\echo [version]
SHOW server_version;

CREATE TABLE payments (
    id integer PRIMARY KEY,
    customer_id integer,
    amount integer NOT NULL CHECK (amount > 0)
);
CREATE TABLE refunds (
    id integer PRIMARY KEY,
    payment_id integer REFERENCES payments(id), -- NULL: ещё не сопоставлена с платежом.
    amount integer NOT NULL CHECK (amount > 0),
    state text NOT NULL CHECK (state IN ('settled', 'pending'))
);
INSERT INTO payments VALUES (1, 10, 100), (2, 10, 100), (3, 20, 300), (4, NULL, 400);
INSERT INTO refunds VALUES (101, 1, 20, 'settled'), (102, 1, 10, 'pending'),
                           (103, 3, 50, 'pending'), (104, NULL, 7, 'settled');

\echo [fanout]
SELECT count(*) AS result_rows, sum(p.amount) AS wrong_total,
       sum(DISTINCT p.amount) AS distinct_total -- Две законные сотни тоже сольются.
FROM payments p
LEFT JOIN refunds r ON r.payment_id = p.id;
SELECT sum(amount) AS actual_total FROM payments; -- Сумма до размножения строк.

\echo [inner]
SELECT p.id AS payment, p.amount, r.id AS refund
FROM payments p
INNER JOIN refunds r ON r.payment_id = p.id -- По одной строке на каждую подходящую пару.
ORDER BY p.id, r.id;
SELECT count(*) AS cross_rows
FROM payments p CROSS JOIN refunds r; -- 4 × 4, условия связи нет.

\echo [on-filter]
SELECT p.id AS payment, r.id AS refund, r.state
FROM payments p
LEFT JOIN refunds r ON r.payment_id = p.id AND r.state = 'settled' -- Отбираем совпадения.
ORDER BY p.id, r.id;

\echo [where-filter]
SELECT p.id AS payment, r.id AS refund, r.state
FROM payments p
LEFT JOIN refunds r ON r.payment_id = p.id
WHERE r.state = 'settled' -- Отбираем готовые строки: NULL тоже не проходит.
ORDER BY p.id, r.id;

\echo [full]
SELECT p.id AS payment, r.id AS refund, r.payment_id
FROM payments p
FULL JOIN refunds r ON r.payment_id = p.id -- Сохраняем несопоставленные строки с обеих сторон.
ORDER BY p.id NULLS LAST, r.id NULLS LAST;

\echo [counts]
SELECT p.id AS payment, count(*) AS rows_after_join, count(r.id) AS settled_refunds
FROM payments p
LEFT JOIN refunds r ON r.payment_id = p.id AND r.state = 'settled'
GROUP BY p.id
ORDER BY p.id;

\echo [semi]
SELECT p.id, p.amount
FROM payments p
WHERE EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.id) -- Нужен факт, а не пары.
ORDER BY p.id;

\echo [anti]
SELECT p.id, p.amount
FROM payments p
WHERE NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.id) -- Ни одной заявки.
ORDER BY p.id;

\echo [not-in]
SELECT p.id,
       p.id NOT IN (SELECT r.payment_id FROM refunds r) AS not_in,
       NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.id) AS not_exists
FROM payments p
ORDER BY p.id;
SELECT count(*) AS rows_kept
FROM payments p
WHERE p.id NOT IN (SELECT r.payment_id FROM refunds r); -- NULL справа лишает TRUE все несовпадения.

\echo [self]
SELECT a.id AS first_payment, b.id AS second_payment, a.amount
FROM payments a
JOIN payments b ON a.customer_id = b.customer_id AND a.amount = b.amount
               AND a.id < b.id -- Не соединяем строку с собой, оставляем одну ориентацию пары.
ORDER BY a.id, b.id;

\echo [preaggregate]
SELECT p.id, p.amount, coalesce(r.total, 0) AS settled_refund_amount
FROM payments p
LEFT JOIN (
    SELECT payment_id, sum(amount) AS total
    FROM refunds
    WHERE state = 'settled'
    GROUP BY payment_id -- Теперь не больше одной строки на ключ платежа.
) r ON r.payment_id = p.id
ORDER BY p.id;

\echo [check-or-null]
SELECT p.id AS payment, r.id AS refund
FROM payments p
LEFT JOIN refunds r ON r.payment_id = p.id
WHERE r.state = 'settled' OR r.id IS NULL
ORDER BY p.id, r.id;

\echo [check-empty]
SELECT NULL::integer NOT IN (SELECT payment_id FROM refunds WHERE false) AS null_not_in_empty,
       NULL::integer NOT IN (SELECT payment_id FROM refunds WHERE payment_id IS NOT NULL) AS null_not_in_nonempty;
