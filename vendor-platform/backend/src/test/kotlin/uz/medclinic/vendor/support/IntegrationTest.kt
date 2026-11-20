package uz.medclinic.vendor.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.json.JsonMapper

/**
 * Base class for API/SQL integration tests: one PostgreSQL container and one Spring context are shared by
 * all subclasses; every test starts from empty business tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig::class, IntegrationTest.ClockConfig::class)
abstract class IntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbc: JdbcClient

    @Autowired
    protected lateinit var json: JsonMapper

    @Autowired
    protected lateinit var clock: MutableClock

    @BeforeEach
    fun resetState() {
        clock.set(DEFAULT_NOW)
        val tables = jdbc.sql(
            """
            select string_agg(format('%I.%I', schemaname, tablename), ', ')
            from pg_tables
            where schemaname = 'public' and tablename <> 'flyway_schema_history'
            """.trimIndent(),
        ).query(String::class.java).optional().orElse(null)
        if (!tables.isNullOrBlank()) jdbc.sql("truncate table $tables restart identity cascade").update()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(DEFAULT_NOW)
    }

    companion object {
        val DEFAULT_NOW: java.time.Instant = java.time.Instant.parse("2026-11-20T06:00:00Z")
    }
}
