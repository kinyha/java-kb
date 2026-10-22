package pay.nulls

import org.springframework.jdbc.core.JdbcTemplate

// Spring Framework 7 размечен JSpecify: queryForObject возвращает @Nullable T
fun countPayments(jdbc: JdbcTemplate): Long {
    val count: Long = jdbc.queryForObject("select count(*) from payment", Long::class.java)
    return count
}
