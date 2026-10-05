#!/usr/bin/env bash
# Учебный репозиторий для прогона процесса: спека, короткий файл правил, заготовка класса, скрипт тестов.
set -eu
D=$(mktemp -d /tmp/workflow-demo.XXXX)
cd "$D"
cat > CLAUDE.md <<'MD'
# Правила проекта
- Java 21, без зависимостей и без пакетов: все классы в корне.
- Тесты: `./test.sh` (компилирует всё и запускает RefundsTest с -ea). Код возврата 0 — зелёный.
- Деньги — long в копейках. Никаких double.
- Не менять SPEC.md и test.sh.
MD
cat > SPEC.md <<'MD'
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
MD
cat > Refunds.java <<'JAVA'
public class Refunds {
    public static long refund(long paid, long fee, long alreadyRefunded, long requested) {
        throw new UnsupportedOperationException("not implemented");
    }
}
JAVA
cat > test.sh <<'SH'
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
rm -rf out && javac -d out *.java && java -ea -cp out RefundsTest
SH
chmod +x test.sh
git init -q && git add -A && git -c user.name=demo -c user.email=demo@example.com commit -qm "spec and stub"
echo "$D"
