# I/O & NIO

JDK 21, без зависимостей. Из корня репозитория:

```bash
java examples/io/ChunkDemo.java
java examples/io/BufferingDemo.java
java examples/io/FilesDemo.java
java examples/io/BufferDemo.java
java examples/io/GraphDemo.java
python3 examples/io/verify_evolution.py
```

Все программы проверены на JBR 21.0.11. FilesDemo использует собственный
временный каталог и удаляет его. ATOMIC_MOVE поддержан на локальной файловой
системе этого запуска; другая FS вправе его отклонить. Вызов force(true) выполнен,
но отключение питания и crash recovery не проверялись.

Chunks и PartialSink — управляемые реализации контрактов InputStream и
WritableByteChannel: максимум два байта за вызов. Это не замер производительности
файловой системы и не утверждение, что настоящий файл всегда читает по два байта.
Sink считает вызовы Java OutputStream; это не счётчик системных вызовов.
Граф и UID проверены настоящими Object streams. Verify_evolution компилирует
writer/два readers в разных classpaths, запускает отдельные JVM и сверяет вывод.

## Карта покрытия и подача

Центральный вопрос: почему экспорт теряет байты, символы или объекты, хотя
вызовы read/write успешны? Сквозной пример — перенос отчёта через границы
потока, декодера, буфера и файла. Тип — концепция, семь шагов:

- Incident: partial read, EOF -1, available не длина/EOF, только первые n bytes
  валидны, цикл и transferTo/readNBytes — incident, ChunkDemo, streams.
- Bytes vs chars, UTF-8 split, stateful Reader, malformed input policy,
  UTF-16 char vs code point, charset defaults — decoding, ChunkDemo, схема
  трёх chunks и декодера, streams; Unicode подробно в strings/data-repr.
- Decorator/ownership, buffering не кодировка, flush/close, ошибки PrintWriter,
  try-with-resources/suppressed — buffering, BufferingDemo, buffers-files;
  suppressed глубже в exceptions. Размер 4 выбран для демо, без default quota.
- Path vs File, lexical normalize/resolve/absolute vs real path, Files streaming
  и resource streams, default truncate/append/CREATE_NEW, symlink/TOCTOU —
  files, FilesDemo, buffers-files; hardening path sandbox не реализуется.
- Channel + ByteBuffer position/limit/capacity, flip/clear/compact, partial write,
  missing flip, direct/map, blocking vs selector readiness — channels,
  BufferDemo, пошаговая схема шести состояний, nio-state. Event loop и mmap
  tuning вне scope; связь с virtual-threads указана.
- Object graph handles/alias/cycle, Serializable fields/transient/static,
  first nonserializable superclass constructor, default values, UID evolution,
  readObject/writeObject/readResolve/Externalizable/records границы — objects,
  GraphDemo, evolution files, java-serialization; records отданы records.
- Publish completed file vs durable storage, force vs flush, ATOMIC_MOVE,
  native deserialization hooks + bounded filter, formats/schema migration —
  boundaries, FilesDemo/GraphDemo, senior cards; formats в serialization,
  HTTP/networking и XML parsing отданы соседним темам.

## Исследование источников

Полностью прочитан Cay Horstmann, Core Java Advanced LiveLessons, Lesson 3
Processing Input and Output, включая раздел Serialization. Основа понятной
подачи: source/destination bytes → bridge Reader/Writer → обёртки → Path/Files
→ каналы → объектные ссылки, сохранённые по handles. Сохраняем этот порядок
собственных опытов и добавляем ошибочные границы read и UTF-8. Не переносим
старый incubator HttpClient и утверждение, что закрытие channel само гарантирует
сохранность mmap: по API 21 для mapped updates используется MappedByteBuffer.force.
Default UID не описываем только как hash полей: спецификация учитывает также
структуру класса и подходящие методы/конструкторы.

Java Interview Handbook, I/O/NIO/Serialization, использован для отбора
byte/char, buffering, File/Path, UID, transient/static, конструкторов,
Externalizable и singleton readResolve. Его советы про обязательный 8 KB buffer,
автоматическую скорость NIO и «UTF-8 везде» не переносим как контракт:
конфигурация, реализация, консоль и workload меняют условия. Технические факты
сверены с Java 21 API и Object Serialization Specification 1/3/4/5/6.

Проверенные отрицательные случаи: chunk decoding ломает €, strict decoder
отвергает оборванный UTF-8, PrintWriter фиксирует ошибку без thrown IOException,
CREATE_NEW отказывается перезаписать файл, без flip отправляется 00 вместо ABC,
несериализуемый объект и depth filter отклоняются, UID mismatch даёт
InvalidClassException. Совместимая v2 получает отсутствующий label как null,
несмотря на initializer; алиасы и цикл графа сохраняются, исходная identity нет.
