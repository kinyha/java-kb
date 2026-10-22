rootProject.name = "shop"
// Каждый модуль сборки — отдельный classpath. Чего нет в зависимостях, того компилятор не видит.
include("app", "payments", "payments-persistence")
