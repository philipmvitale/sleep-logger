package com.noom.interview.fullstack.sleep

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    companion object {
        @JvmStatic
        val POSTGRESQL_CONTAINER = PostgreSQLContainer<Nothing>("postgres:13-alpine").apply {
            withDatabaseName("postgres")
            withUsername("user")
            withPassword("password")
            start()
        }
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "spring.datasource.url=${POSTGRESQL_CONTAINER.jdbcUrl}",
                "spring.datasource.username=${POSTGRESQL_CONTAINER.username}",
                "spring.datasource.password=${POSTGRESQL_CONTAINER.password}"
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}
