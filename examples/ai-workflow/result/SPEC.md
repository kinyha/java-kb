# Слайс: частичный возврат платежа

Метод `long Refunds.refund(long paid, long fee, long alreadyRefunded, long requested)` возвращает
сумму, которую можно вернуть клиенту сейчас. Все суммы в копейках.

## Критерии приёмки
1. Возвращается не больше остатка: `paid - fee - alreadyRefunded`. Комиссия не возвращается.
2. Если `requested` больше остатка — `IllegalArgumentException` с текстом, содержащим `exceeds`.
3. `requested <= 0` — `IllegalArgumentException` с текстом, содержащим `positive`.
4. Возврат ровно на весь остаток разрешён: paid=1000, fee=15, already=0, requested=985 → 985.

## Вне объёма
Валюты, хранение, повторы запросов.
