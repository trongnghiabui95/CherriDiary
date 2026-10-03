package com.cherri.diary

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import javax.sql.DataSource

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestConfiguration {
    @Bean(destroyMethod = "close")
    fun postgres(): EmbeddedPostgres = EmbeddedPostgres.builder().setPort(0)
        .setServerConfig("max_connections", "40").start()
    @Bean
    fun dataSource(postgres: EmbeddedPostgres): DataSource = postgres.postgresDatabase
}
