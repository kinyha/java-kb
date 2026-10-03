# URL Shortener demo

Пример к теме System Design › Case Studies › URL Shortener. JDK 21, без зависимостей; нужно около 3 ГБ памяти.

```bash
java -Xmx3g examples/sd-url-shortener/ShortenerDemo.java
```

Коллизии префикса хеша, коды из счётчика с перестановкой Фейстеля и доля попаданий LRU-кеша при чтениях по закону Ципфа. Генератор случайных чисел с фиксированным зерном — вывод повторяется.
