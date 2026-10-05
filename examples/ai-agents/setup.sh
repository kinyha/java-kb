#!/usr/bin/env bash
# Создаёт маленький учебный репозиторий для прогонов агента и печатает его путь.
set -eu
D=$(mktemp -d /tmp/agent-demo.XXXX)
mkdir -p "$D/src/pay"
cat > "$D/src/pay/FeeCalculator.java" <<'JAVA'
package pay;

public class FeeCalculator {
    // Комиссия 1.5% в копейках, округление вниз
    public long fee(long amountMinor) {
        return amountMinor * 15 / 1000;
    }
}
JAVA
cat > "$D/src/pay/RefundService.java" <<'JAVA'
package pay;

public class RefundService {
    private final FeeCalculator fees = new FeeCalculator();
    public long refundable(long paidMinor) {
        return paidMinor - fees.fee(paidMinor);
    }
}
JAVA
(cd "$D" && git init -q && git add -A && git -c user.name=demo -c user.email=demo@example.com commit -qm init)
echo "$D"
