package com.capdi.backend.domain.payment.config;

import com.capdi.backend.domain.payment.controller.PortOneWebhookController;
import com.capdi.backend.domain.payment.entity.PaymentEnvironment;
import com.capdi.backend.domain.payment.entity.PaymentMethod;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.gateway.portone.PortOneSettings;
import com.capdi.backend.domain.payment.gateway.portone.PortOneTransport;
import com.capdi.backend.domain.payment.gateway.portone.PortOneV2Gateway;
import com.capdi.backend.domain.payment.service.PaymentWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;

/** Boot conditions and real OS-name property resolution; no actual environment mutation or network. */
class PortOneConfigurationBindingTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path secretDirectory;
    private static final String PREFIX="PAYMENT_PORTONE_";

    private static String signingKey(int bytes) {
        return "whsec_"+Base64.getEncoder().encodeToString(new byte[bytes]);
    }

    private static Map<String,Object> sandbox() {
        Map<String,Object> env=new LinkedHashMap<>();
        env.put(PREFIX+"ENABLED","true");
        env.put(PREFIX+"SANDBOX_APPROVED","true");
        env.put(PREFIX+"ENVIRONMENT","SANDBOX");
        env.put(PREFIX+"STORE_ID","store-step5-test");
        env.put(PREFIX+"CHANNEL_KEY","channel-step5-test");
        // Only generated synthetic values; never use developer credentials in these tests.
        env.put(PREFIX+"API_SECRET","synthetic-"+"x".repeat(24));
        env.put(PREFIX+"WEBHOOK_SECRET",signingKey(32));
        env.put(PREFIX+"WEBHOOK_ENABLED","true");
        env.put(PREFIX+"TIMEOUT_MS","1234");
        env.put(PREFIX+"EGRESS_PROXY_HOST","127.0.0.1");
        env.put(PREFIX+"EGRESS_PROXY_PORT","3128");
        return env;
    }

    private void run(Map<String,Object> variables,ContextConsumer<AssertableApplicationContext> assertions) {
        var webhook=mock(PaymentWebhookService.class);
        try(var transports=mockConstruction(PortOneTransport.Jdk.class)) {
            new ApplicationContextRunner()
                .withInitializer(context->{
                    var environment=context.getEnvironment();
                    // Do not inherit an operator's secrets, -D flags or enabled provider settings.
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,new LinkedHashMap<>(variables)));
                    ConfigurationPropertySources.attach(environment);
                })
                .withBean(ObjectMapper.class,ObjectMapper::new)
                .withBean(PaymentWebhookService.class,()->webhook)
                .withUserConfiguration(PaymentProcessingConfig.class,PortOneWebhookController.class)
                .run(assertions);
            transports.constructed().forEach(t->verifyNoInteractions(t));
        }
        verifyNoInteractions(webhook);
    }

    private void rejected(Map<String,Object> env) {
        run(env,context->{
            assertThat(context).hasFailed();
            Throwable cause=context.getStartupFailure();
            while(cause.getCause()!=null)cause=cause.getCause();
            assertThat(cause).isInstanceOf(IllegalArgumentException.class);
            assertThat(cause.getMessage()).satisfies(message -> assertThat(message).matches(
                "PortOne sandbox configuration invalid(?:: [A-Z_]+)?|PortOne webhook configuration invalid|"
                    + "PortOne egress proxy configuration required|PortOne egress proxy configuration invalid"));
        });
    }

    @ParameterizedTest
    @CsvSource({
        "ENABLED,enabled", "SANDBOX_APPROVED,sandbox-approved", "ENVIRONMENT,environment",
        "STORE_ID,store-id", "CHANNEL_KEY,channel-key",
        "API_SECRET,api-secret", "WEBHOOK_SECRET,webhook-secret",
        "WEBHOOK_ENABLED,webhook-enabled", "TIMEOUT_MS,timeout-ms",
        "EGRESS_PROXY_HOST,egress-proxy.host", "EGRESS_PROXY_PORT,egress-proxy.port"
    })
    void tenOsNamesResolveThroughSpringEnvironment(String osSuffix,String propertySuffix) {
        var env=sandbox();
        run(env,context->{
            assertThat(context).hasNotFailed();
            // Boolean assertion avoids printing even synthetic secret values in failure messages.
            assertThat(env.get(PREFIX+osSuffix).equals(context.getEnvironment()
                .getProperty("payment.portone."+propertySuffix))).isTrue();
        });
    }

    @Test void providerAndReceiverAreDisabledByDefault() {
        run(Map.of(),context->{
            assertThat(context).hasNotFailed().doesNotHaveBean(PortOneWebhookController.class);
            var gateway=context.getBean(PaymentGateway.class);
            assertThat(gateway.enabled()).isFalse();
            assertThat(gateway.identity().environment()).isEqualTo(PaymentEnvironment.DISABLED);
            assertThat(gateway.supports(PaymentGateway.Capability.LOOKUP)).isFalse();
        });
    }

    @Test void secretFilesBindThroughOsNamesWithoutInlineSecrets() throws Exception {
        var env=sandbox();
        for(String name:new String[]{"API_SECRET","WEBHOOK_SECRET"}) {
            var p=secretDirectory.resolve(name);
            java.nio.file.Files.writeString(p,(String)env.remove(PREFIX+name));
            env.put(PREFIX+name+"_FILE",p.toString());
        }
        run(env,context->assertThat(context).hasNotFailed().hasSingleBean(PortOneWebhookController.class));
    }

    @Test void conflictingSecretSourcesPreventGatewayCreation() throws Exception {
        var env=sandbox();var p=secretDirectory.resolve("api");
        java.nio.file.Files.writeString(p,"synthetic-only");env.put(PREFIX+"API_SECRET_FILE",p.toString());
        run(env,context->{
            assertThat(context).hasFailed();
            Throwable cause=context.getStartupFailure();while(cause.getCause()!=null)cause=cause.getCause();
            assertThat(cause.getMessage()).isEqualTo("PortOne secret file configuration invalid");
        });
    }

    @Test void approvedSandboxUsesTheBoundIdentifiersAndLocalPreparationOnly() {
        run(sandbox(),context->{
            assertThat(context).hasNotFailed().hasSingleBean(PortOneWebhookController.class);
            var gateway=context.getBean(PortOneV2Gateway.class);
            assertThat(gateway.enabled()).isTrue();
            assertThat(gateway.identity().merchant()).isNull();
            assertThat(gateway.identity().providerAccountId()).isEqualTo("provider:PORTONE_V2:store:store-step5-test");
            assertThat(gateway.identity().environment()).isEqualTo(PaymentEnvironment.SANDBOX);
            var reference=new PaymentGateway.Reference(gateway.identity(),"order-step5-test",
                "00000000-0000-0000-0000-000000000001",null,new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
            var checkout=gateway.checkout(reference);
            assertThat(checkout.getStoreId()).isEqualTo("store-step5-test");
            assertThat(checkout.getChannelKey()).isEqualTo("channel-step5-test");
            assertThat(gateway.prepare(reference).state()).isEqualTo(PaymentGateway.State.PREPARED);
            assertThat(gateway.supports(PaymentGateway.Capability.VERIFY_WEBHOOK)).isTrue();
            var settings=(PortOneSettings)ReflectionTestUtils.getField(gateway,"settings");
            assertThat(settings.timeout()).isEqualTo(Duration.ofMillis(1234));
        });
    }

    @ParameterizedTest @ValueSource(strings={"PRODUCTION","DISABLED","sandbox"})
    void enabledProviderRejectsNonSandboxEnvironment(String environment) {
        var env=sandbox();env.put(PREFIX+"ENVIRONMENT",environment);rejected(env);
    }

    @ParameterizedTest @ValueSource(strings={"SANDBOX_APPROVED","STORE_ID","CHANNEL_KEY","API_SECRET","EGRESS_PROXY_HOST","EGRESS_PROXY_PORT"})
    void enabledProviderRejectsMissingRequiredSetting(String suffix) {
        var env=sandbox();env.remove(PREFIX+suffix);rejected(env);
    }

    @Test void merchantIdIsAnOptionalExpectedResponsePin() {
        var env=sandbox();
        run(env,context->{
            assertThat(context).hasNotFailed();
            var gateway=context.getBean(PortOneV2Gateway.class);
            assertThat(gateway.identity().merchant()).isNull();
            assertThat(gateway.identity().providerAccountId()).isEqualTo("provider:PORTONE_V2:store:store-step5-test");
        });
    }

    @Test void blankMerchantIdIsTreatedAsAnUnconfiguredOptionalPin() {
        var env=sandbox();env.put(PREFIX+"MERCHANT_ID","");
        run(env,context->{
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PortOneV2Gateway.class).identity().merchant()).isNull();
            assertThat(context.getBean(PortOneV2Gateway.class).identity().providerAccountId())
                    .isEqualTo("provider:PORTONE_V2:store:store-step5-test");
        });
    }

    @Test void configuredMerchantIdRemainsAnExactExpectedResponsePin() {
        var env=sandbox();env.put(PREFIX+"MERCHANT_ID","merchant-step5-test");
        run(env,context->{
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PortOneV2Gateway.class).identity().merchant()).isEqualTo("merchant-step5-test");
        });
    }

    @Test void explicitApprovalFalseIsRejected() {
        var env=sandbox();env.put(PREFIX+"SANDBOX_APPROVED","false");rejected(env);
    }

    @ParameterizedTest @ValueSource(strings={"STORE_ID","CHANNEL_KEY"})
    void malformedIdentifiersAreRejected(String suffix) {
        var env=sandbox();env.put(PREFIX+suffix,"not an identifier");rejected(env);
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","30001"})
    void outOfRangeTimeoutIsRejected(String timeout) {
        var env=sandbox();env.put(PREFIX+"TIMEOUT_MS",timeout);rejected(env);
    }

    @Test void nonNumericTimeoutIsRejectedWithoutUsingASecretAsInput() {
        var env=sandbox();env.put(PREFIX+"TIMEOUT_MS","not-a-number");
        run(env,context->assertThat(context).hasFailed());
    }

    @ParameterizedTest @ValueSource(strings={"1","30000"})
    void timeoutBoundaryValuesAreAccepted(String timeout) {
        var env=sandbox();env.put(PREFIX+"TIMEOUT_MS",timeout);
        run(env,context->assertThat(context).hasNotFailed());
    }

    @Test void omittedTimeoutUsesTheExistingTenSecondDefault() {
        var env=sandbox();env.remove(PREFIX+"TIMEOUT_MS");
        run(env,context->{
            assertThat(context).hasNotFailed();
            var settings=(PortOneSettings)ReflectionTestUtils.getField(context.getBean(PortOneV2Gateway.class),"settings");
            assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test void receiverMayRemainDisabledWithoutAWebhookSecret() {
        var env=sandbox();env.remove(PREFIX+"WEBHOOK_ENABLED");env.remove(PREFIX+"WEBHOOK_SECRET");
        run(env,context->{
            assertThat(context).hasNotFailed().doesNotHaveBean(PortOneWebhookController.class);
            assertThat(context.getBean(PaymentGateway.class).enabled()).isTrue();
            assertThat(context.getBean(PaymentGateway.class).supports(PaymentGateway.Capability.VERIFY_WEBHOOK)).isFalse();
        });
    }

    @Test void explicitReceiverFalseDoesNotCreateTheController() {
        var env=sandbox();env.put(PREFIX+"WEBHOOK_ENABLED","false");
        run(env,context->assertThat(context).hasNotFailed().doesNotHaveBean(PortOneWebhookController.class));
    }

    @Test void enabledReceiverRequiresAnEnabledProvider() {
        var env=sandbox();env.put(PREFIX+"ENABLED","false");rejected(env);
    }

    @Test void enabledReceiverRequiresASecret() {
        var env=sandbox();env.remove(PREFIX+"WEBHOOK_SECRET");rejected(env);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","not-base64!","whsec_"})
    void invalidWebhookSecretIsRejected(String value) {
        var env=sandbox();env.put(PREFIX+"WEBHOOK_SECRET",value);rejected(env);
    }

    @ParameterizedTest @ValueSource(ints={0,23,65})
    void webhookDecodedKeyLengthIsEnforced(int bytes) {
        var env=sandbox();env.put(PREFIX+"WEBHOOK_SECRET",signingKey(bytes));rejected(env);
    }

    @ParameterizedTest @ValueSource(ints={24,64})
    void webhookDecodedKeyBoundariesAreAccepted(int bytes) {
        var env=sandbox();env.put(PREFIX+"WEBHOOK_SECRET",signingKey(bytes));
        run(env,context->assertThat(context).hasNotFailed().hasSingleBean(PortOneWebhookController.class));
    }

    @Test void prefixlessWebhookKeyRemainsCompatibleWithTheExistingVerifier() {
        var env=sandbox();env.put(PREFIX+"WEBHOOK_SECRET",signingKey(32).substring(6));
        run(env,context->assertThat(context).hasNotFailed());
    }

    @ParameterizedTest @ValueSource(strings={""," ","with space","with\nnewline"})
    void invalidApiSecretIsRejectedWithoutLeakingIt(String value) {
        var env=sandbox();env.put(PREFIX+"API_SECRET",value);rejected(env);
    }

    @Test void oversizedSecretsAreRejectedWithGenericMessages() {
        var env=sandbox();env.put(PREFIX+"API_SECRET","x".repeat(4097));rejected(env);
        env=sandbox();env.put(PREFIX+"WEBHOOK_SECRET","x".repeat(129));rejected(env);
    }

    @Test void settingsDiagnosticsRemainRedacted() {
        var env=sandbox();
        var settings=new PortOneSettings(true,true,"SANDBOX","merchant-step5-test","store-step5-test",
            "channel-step5-test",(String)env.get(PREFIX+"API_SECRET"),(String)env.get(PREFIX+"WEBHOOK_SECRET"),Duration.ofSeconds(1));
        assertThat(settings.toString()).isEqualTo("PortOneSettings[REDACTED]");
        assertThatCode(()->settings.validateWebhookConfiguration(true)).doesNotThrowAnyException();
    }
}
