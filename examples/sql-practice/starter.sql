-- Replace each empty SELECT with your solution, keeping the view and column names.
CREATE TEMP VIEW task_duplicates AS
SELECT NULL::integer AS account_id, NULL::text AS request_id, NULL::bigint AS attempts WHERE false;

CREATE TEMP VIEW task_second AS
SELECT NULL::integer AS account_id, NULL::bigint AS second_amount WHERE false;

CREATE TEMP VIEW task_top AS
SELECT NULL::integer AS account_id, NULL::integer AS payment_id,
       NULL::bigint AS amount, NULL::bigint AS position WHERE false;

CREATE TEMP VIEW task_totals AS
SELECT NULL::integer AS account_id, NULL::bigint AS paid_count, NULL::bigint AS failed_count,
       NULL::numeric AS gross, NULL::numeric AS refunded, NULL::numeric AS net WHERE false;

CREATE TEMP VIEW task_latest AS
SELECT NULL::integer AS account_id, NULL::text AS request_id,
       NULL::integer AS payment_id, NULL::text AS status WHERE false;
