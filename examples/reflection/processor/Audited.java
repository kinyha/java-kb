package audit;

import java.lang.annotation.*;

// Аннотация только для компилятора: в класс-файл не попадает.
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Audited { String value(); }
