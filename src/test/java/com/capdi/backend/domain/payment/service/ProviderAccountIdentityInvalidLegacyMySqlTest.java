package com.capdi.backend.domain.payment.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves malformed legacy webhook identity fails closed on an isolated disposable V29 schema. */
@Testcontainers
@Tag("mysql-integration")
class ProviderAccountIdentityInvalidLegacyMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_provider_identity_invalid_test")
            .withUsername("provider_identity_invalid_test")
            .withPassword(UUID.randomUUID().toString())
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withLabel("capdi.test.scope", "provider-account-identity-invalid-legacy-isolated");

    @Test
    void v30RejectsWebhookWhoseAttemptHasNoLegacyIdentity() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("29").load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("""
                    INSERT INTO payments(id,order_id,bid_id,announcement_id,announcement_code,client_user_id,
                        expert_profile_id,amount,currency,method,fee_policy_version,platform_fee_rate,expert_share_rate,
                        status,version,selection_round,created_at,updated_at)
                    VALUES(8101,'invalid-legacy-order',8101,8101,'INVALID-LEGACY-8101',8101,8101,1000,'KRW','CARD',
                        'legacy-v1',0.1000,0.9000,'FAILED',0,1,NOW(6),NOW(6))
                    """);
            statement.execute("""
                    INSERT INTO payment_attempts(id,payment_id,attempt_key,attempt_number,status,provider,version,
                        created_at,updated_at,method,charge_amount,currency,order_id,provider_code,merchant_id,
                        environment,provider_transaction_id,failure)
                    VALUES(8101,8101,'00000000-0000-0000-0000-000000000811',1,'FAILED','ADAPTER',0,NOW(6),NOW(6),
                        'CARD',1000,'KRW','invalid-legacy-order','PORTONE_V2',NULL,'SANDBOX',NULL,'NONE')
                    """);
            statement.execute("""
                    INSERT INTO payment_webhook_events(id,attempt_id,provider_code,merchant_id,environment,event_key_hash,
                        payload_hash,state,version,created_at,updated_at)
                    VALUES(8101,8101,'PORTONE_V2','synthetic-webhook-merchant','SANDBOX',REPEAT('a',64),REPEAT('b',64),
                        'RECEIVED',0,NOW(6),NOW(6))
                    """);
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }

        var latest = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load();
        assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class);

        // MySQL DDL may have committed earlier V30 steps. Do not repair or retry here;
        // verify only that the migration did not report V30 as successfully applied.
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isZero();
        }
    }
}
