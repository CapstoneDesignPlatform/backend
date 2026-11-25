package com.capdi.backend.domain.payment.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies V30 backfills against disposable V29 rows without touching a persistent database. */
@Testcontainers
@Tag("mysql-integration")
class ProviderAccountIdentityMigrationMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_provider_identity_migration_test")
            .withUsername("provider_identity_test")
            .withPassword(UUID.randomUUID().toString())
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withLabel("capdi.test.scope", "provider-account-identity-migration-isolated");

    @Test
    void v30PreservesMerchantValuesAndBackfillsNamespacedAccountIdentity() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("29").load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("""
                    INSERT INTO payments(id,order_id,bid_id,announcement_id,announcement_code,client_user_id,
                        expert_profile_id,amount,currency,method,fee_policy_version,platform_fee_rate,expert_share_rate,
                        status,version,selection_round,created_at,updated_at)
                    VALUES(7001,'legacy-order',7001,7001,'LEGACY-7001',7001,7001,1000,'KRW','CARD','legacy-v1',0.1000,0.9000,
                        'PAID',0,1,NOW(6),NOW(6))
                    """);
            statement.execute("""
                    INSERT INTO payment_attempts(id,payment_id,attempt_key,attempt_number,status,provider,version,
                        created_at,updated_at,method,charge_amount,currency,order_id,provider_code,merchant_id,
                        environment,provider_transaction_id,failure)
                    VALUES(7001,7001,'00000000-0000-0000-0000-000000000701',1,'SUCCEEDED','ADAPTER',0,NOW(6),NOW(6),
                        'CARD',1000,'KRW','legacy-order','PORTONE_V2','legacy-merchant','SANDBOX','legacy-transaction','NONE')
                    """);
            statement.execute("""
                    INSERT INTO payment_webhook_events(id,attempt_id,provider_code,merchant_id,environment,event_key_hash,
                        payload_hash,state,version,created_at,updated_at)
                    VALUES(7001,7001,'PORTONE_V2','legacy-merchant','SANDBOX',REPEAT('a',64),REPEAT('b',64),
                        'RECEIVED',0,NOW(6),NOW(6))
                    """);
            statement.execute("""
                    INSERT INTO financial_operations(id,reference,kind,provider_code,provider_merchant_id,provider_store_id,
                        provider_environment,seller_draft_id,account_id,source_version,key_hash,request_hash,encrypted_payload,
                        state,nts_state,next_lookup_at,submit_claimed,lookup_count,failure_code,version,created_at,updated_at)
                    VALUES(7001,'legacy-finance','PARTNER','PORTONE_V2','legacy-merchant','legacy-store','SANDBOX',7001,7001,1,
                        REPEAT('c',64),REPEAT('d',64),'synthetic-encrypted-fixture','READY','NOT_SENT',NOW(6),0,0,'NONE',0,NOW(6),NOW(6))
                    """);
            statement.execute("""
                    INSERT INTO financial_operations(id,reference,kind,provider_code,provider_merchant_id,provider_store_id,
                        provider_environment,payment_id,invoice_id,source_version,key_hash,request_hash,encrypted_payload,external_id,
                        state,nts_state,next_lookup_at,submit_claimed,lookup_count,failure_code,version,created_at,updated_at)
                    VALUES(7002,'legacy-invoice','TAX_INVOICE','PORTONE_V2','legacy-merchant','legacy-store','SANDBOX',7001,7002,1,
                        REPEAT('f',64),REPEAT('e',64),'synthetic-encrypted-fixture','legacy-invoice-external',
                        'READY','NOT_SENT',NOW(6),0,0,'NONE',0,NOW(6),NOW(6))
                    """);
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }

        var latest = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("33");
        latest.migrate(); // Applying the current schema again must be a no-op.
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT merchant_id,provider_account_id FROM payment_attempts WHERE id=7001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("merchant_id")).isEqualTo("legacy-merchant");
                assertThat(rows.getString("provider_account_id")).isEqualTo("provider:PORTONE_V2:legacy-merchant:legacy-merchant");
            }
            try (var rows = statement.executeQuery("SELECT merchant_id,provider_account_id FROM payment_webhook_events WHERE id=7001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("merchant_id")).isEqualTo("legacy-merchant");
                assertThat(rows.getString("provider_account_id")).isEqualTo("provider:PORTONE_V2:legacy-merchant:legacy-merchant");
            }
            try (var rows = statement.executeQuery("SELECT provider_merchant_id,provider_store_id,provider_account_id FROM financial_operations WHERE id=7001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("provider_merchant_id")).isEqualTo("legacy-merchant");
                assertThat(rows.getString("provider_store_id")).isEqualTo("legacy-store");
                assertThat(rows.getString("provider_account_id")).isEqualTo("provider:PORTONE_V2:store:legacy-store");
            }
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO payment_webhook_events(attempt_id,provider_code,merchant_id,provider_account_id,environment,
                        event_key_hash,payload_hash,state,version,created_at,updated_at)
                    VALUES(7001,'PORTONE_V2','legacy-merchant','provider:PORTONE_V2:legacy-merchant:legacy-merchant',
                        'SANDBOX',REPEAT('a',64),REPEAT('e',64),'RECEIVED',0,NOW(6),NOW(6))
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO payment_attempts(payment_id,attempt_key,attempt_number,status,provider,version,created_at,updated_at,
                        method,charge_amount,currency,order_id,provider_code,merchant_id,provider_account_id,environment,
                        provider_transaction_id,failure)
                    VALUES(7001,'00000000-0000-0000-0000-000000000702',2,'FAILED','ADAPTER',0,NOW(6),NOW(6),
                        'CARD',1000,'KRW','legacy-order','PORTONE_V2','legacy-merchant',
                        'provider:PORTONE_V2:legacy-merchant:legacy-merchant','SANDBOX','legacy-transaction','NONE')
                    """)).isInstanceOf(SQLException.class);
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO financial_operations(reference,kind,provider_code,provider_merchant_id,provider_store_id,
                        provider_environment,payment_id,invoice_id,source_version,key_hash,request_hash,encrypted_payload,external_id,
                        state,nts_state,next_lookup_at,submit_claimed,lookup_count,failure_code,version,created_at,updated_at)
                    VALUES('legacy-invoice-duplicate','TAX_INVOICE','PORTONE_V2','another-legacy-merchant','legacy-store',
                        'SANDBOX',7001,7003,2,REPEAT('1',64),REPEAT('2',64),'synthetic-encrypted-fixture',
                        'legacy-invoice-external','READY','NOT_SENT',NOW(6),0,0,'NONE',0,NOW(6),NOW(6))
                    """)).isInstanceOf(SQLException.class);
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }
}
