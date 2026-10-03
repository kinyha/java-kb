\pset pager off
\pset null NULL
\echo [version]
SHOW server_version;

-- Все суммы в копейках одной валюты; paid_on — день расчёта.
CREATE TEMP TABLE payments (
  id integer PRIMARY KEY,
  merchant text NOT NULL,
  paid_on date NOT NULL,
  amount integer NOT NULL CHECK (amount > 0)
);
INSERT INTO payments VALUES
  (101, 'A', '2026-10-01', 100),
  (102, 'A', '2026-10-01', 200),
  (103, 'A', '2026-10-02', 300),
  (104, 'A', '2026-10-03', 200),
  (201, 'B', '2026-10-01', 500),
  (202, 'B', '2026-10-02', 500);

\echo [grouped]
SELECT merchant, sum(amount) AS total
FROM payments
GROUP BY merchant                  -- один результат на продавца
ORDER BY merchant;

\echo [annotated]
SELECT id, merchant, amount,
       sum(amount) OVER (PARTITION BY merchant) AS total
FROM payments                       -- каждая строка платежа остаётся
ORDER BY merchant, id;

\echo [ranking]
SELECT merchant, id, amount,
       row_number() OVER (
         PARTITION BY merchant ORDER BY amount DESC, id
       ) AS rn,                     -- id разрешает равенство для выбора строки
       rank() OVER (
         PARTITION BY merchant ORDER BY amount DESC
       ) AS rnk,                    -- одинаковые суммы делят одно место
       dense_rank() OVER (
         PARTITION BY merchant ORDER BY amount DESC
       ) AS dense
FROM payments
ORDER BY merchant, amount DESC, id;  -- порядок вывода задаётся отдельно

\echo [running]
SELECT id, paid_on, amount,
       sum(amount) OVER (
         PARTITION BY merchant ORDER BY paid_on
       ) AS by_day,                 -- default RANGE включает весь текущий день
       sum(amount) OVER (
         PARTITION BY merchant ORDER BY paid_on, id
         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       ) AS by_payment,             -- устойчивый префикс до конкретного платежа
       sum(amount) OVER (
         PARTITION BY merchant ORDER BY paid_on, id
       ) AS unique_range            -- уникальный порядок убрал равенства и у RANGE
FROM payments
WHERE merchant = 'A'
ORDER BY paid_on, id;

\echo [neighbors]
SELECT merchant, id, amount,
       sum(amount) OVER w AS frame_sum,
       lag(amount) OVER w AS prev_amount,
       lead(amount) OVER w AS next_amount,
       amount - lag(amount) OVER w AS delta
FROM payments
WINDOW w AS (
  PARTITION BY merchant ORDER BY paid_on, id
  ROWS BETWEEN CURRENT ROW AND CURRENT ROW
)                                   -- SUM видит одну строку, LAG/LEAD — соседей
ORDER BY merchant, paid_on, id;

\echo [last-value]
SELECT merchant, id,
       last_value(id) OVER (
         PARTITION BY merchant ORDER BY paid_on, id
       ) AS default_last,           -- конец default frame здесь — текущая строка
       last_value(id) OVER (
         PARTITION BY merchant ORDER BY paid_on, id
         ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
       ) AS partition_last          -- конец frame явно перенесён в конец partition
FROM payments
ORDER BY merchant, paid_on, id;

\echo [top-rows]
SELECT merchant, id, amount, rn
FROM (
  SELECT merchant, id, amount,
         row_number() OVER (
           PARTITION BY merchant ORDER BY amount DESC, id
         ) AS rn
  FROM payments
) AS numbered
WHERE rn <= 2                       -- номер уже стал обычной колонкой
ORDER BY merchant, rn;

\echo [top-ties]
SELECT merchant, id, amount, rnk
FROM (
  SELECT merchant, id, amount,
         rank() OVER (
           PARTITION BY merchant ORDER BY amount DESC
         ) AS rnk                   -- id не включён: равные суммы делят место
  FROM payments
) AS ranked
WHERE rnk <= 2
ORDER BY merchant, rnk, id;

\echo [rank-with-id]
SELECT id, amount,
       rank() OVER (ORDER BY amount DESC) AS by_amount,
       rank() OVER (ORDER BY amount DESC, id) AS with_id
FROM payments
WHERE merchant = 'A'
ORDER BY amount DESC, id;

\echo [filter-before]
SELECT id, paid_on,
       lag(id) OVER (ORDER BY paid_on, id) AS prev_id
FROM payments
WHERE merchant = 'A' AND paid_on >= DATE '2026-10-02'
ORDER BY paid_on, id;

\echo [filter-after]
SELECT id, paid_on, prev_id
FROM (
  SELECT id, paid_on,
         lag(id) OVER (ORDER BY paid_on, id) AS prev_id
  FROM payments
  WHERE merchant = 'A'
) AS history
WHERE paid_on >= DATE '2026-10-02'
ORDER BY paid_on, id;
