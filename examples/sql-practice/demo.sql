\pset format unaligned
\pset footer off
\pset null NULL
SET statement_timeout = '5s';
SELECT current_setting('server_version') AS postgres_version;
\ir fixture.sql
\ir :solution_file
\echo [duplicates]
SELECT * FROM task_duplicates ORDER BY account_id, request_id;
\echo [second distinct amount]
SELECT * FROM task_second ORDER BY account_id;
\echo [top two recent paid]
SELECT * FROM task_top ORDER BY account_id, position;
\echo [account totals]
SELECT * FROM task_totals ORDER BY account_id;
\echo [latest per request, unknown requests kept]
SELECT * FROM task_latest ORDER BY account_id, request_id NULLS LAST, payment_id;
\echo [checks]
\pset tuples_only on
\ir checks.sql
