package com.noom.interview.fullstack.sleep

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.containers.PostgreSQLContainer
import java.net.InetSocketAddress
import java.net.Socket

@SpringBootTest
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    companion object {
        private const val REACHABILITY_TIMEOUT_MS = 2000

        @JvmStatic
        val POSTGRESQL_CONTAINER =
            PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
                withDatabaseName("postgres")
                withUsername("user")
                withPassword("password")
                start()
            }
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            TestPropertyValues
                .of(
                    "spring.datasource.url=${resolveJdbcUrl()}",
                    "spring.datasource.username=${POSTGRESQL_CONTAINER.username}",
                    "spring.datasource.password=${POSTGRESQL_CONTAINER.password}",
                ).applyTo(configurableApplicationContext.environment)
        }

        /**
         * Resolves the JDBC URL for the Postgres container.
         *
         * On a normal host (e.g. Docker Desktop) the published port on [PostgreSQLContainer.getHost]
         * is reachable, so we use the default JDBC URL. Inside a Docker-in-Docker dev container the
         * dynamically mapped port on the bridge gateway is not routable, so we fall back to
         * connecting directly to the container's bridge-network IP on 5432.
         */
        private fun resolveJdbcUrl(): String {
            if (isReachable(POSTGRESQL_CONTAINER.host, POSTGRESQL_CONTAINER.firstMappedPort)) {
                return POSTGRESQL_CONTAINER.jdbcUrl
            }
            val bridgeIp =
                POSTGRESQL_CONTAINER.containerInfo.networkSettings.networks["bridge"]
                    ?.ipAddress
            return if (bridgeIp != null) {
                "jdbc:postgresql://$bridgeIp:5432/${POSTGRESQL_CONTAINER.databaseName}"
            } else {
                POSTGRESQL_CONTAINER.jdbcUrl
            }
        }

        private fun isReachable(
            host: String,
            port: Int,
        ): Boolean =
            runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), REACHABILITY_TIMEOUT_MS) }
                true
            }.getOrDefault(false)
    }
}
