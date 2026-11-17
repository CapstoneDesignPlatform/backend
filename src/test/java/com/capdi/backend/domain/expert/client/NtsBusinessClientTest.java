package com.capdi.backend.domain.expert.client;

import com.capdi.backend.domain.expert.config.NtsBusinessProperties;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.capdi.backend.domain.expert.util.BusinessInputValidator;
import com.capdi.backend.domain.expert.util.BusinessRegistrationExtractor;
import com.capdi.backend.global.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NtsBusinessClientTest {
    NtsBusinessProperties properties;
    NtsHttpTransport transport;
    NtsBusinessClient client;
    ObjectMapper mapper = new ObjectMapper();
    LocalDate date = LocalDate.of(2024,2,29);
    static final String NUMBER = "1234567891";

    @BeforeEach void setup() {
        properties = new NtsBusinessProperties();
        properties.setEnabled(true);
        properties.setApiKey("fake+/=%key");
        transport = mock(NtsHttpTransport.class);
        client = new NtsBusinessClient(properties,transport,mapper,new BusinessInputValidator(new BusinessRegistrationExtractor()));
    }
    String status(String code) {
        return "{\"status_code\":\"OK\",\"request_cnt\":1,\"match_cnt\":1,\"data\":[{\"b_no\":\""+NUMBER+"\",\"b_stt_cd\":\""+code+"\"}]}";
    }
    String validation(String code, String businessState) {
        return "{\"status_code\":\"OK\",\"request_cnt\":1,\"valid_cnt\":"+(code.equals("01")?1:0)
                +",\"data\":[{\"b_no\":\""+NUMBER+"\",\"valid\":\""+code+"\""
                +(businessState == null ? "" : ",\"status\":{\"b_no\":\""+NUMBER+"\",\"b_stt_cd\":\""+businessState+"\"}")+"}]}";
    }
    void reply(int status,String json) throws Exception {
        when(transport.post(any(),anyString(),any())).thenReturn(new NtsHttpTransport.Response(status,json));
    }
    @Test void normalizesValidateRequestAndEncodesRawKeyExactlyOnce() throws Exception {
        reply(200,validation("01","01"));
        assertThat(client.validate("123-45-67891",date," Representative ",null).authenticity()).isEqualTo(Authenticity.MATCH);
        var uri = org.mockito.ArgumentCaptor.forClass(URI.class);
        var json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(transport).post(uri.capture(),json.capture(),eq(properties.getResponseTimeout()));
        assertThat(uri.getValue().getScheme()).isEqualTo("https");
        assertThat(uri.getValue().getHost()).isEqualTo("api.odcloud.kr");
        assertThat(uri.getValue().getPath()).endsWith("/validate");
        assertThat(uri.getValue().getRawQuery()).isEqualTo("serviceKey=fake%2B%2F%3D%25key&returnType=JSON");
        var body = mapper.readTree(json.getValue()).get("businesses").get(0);
        assertThat(body.get("b_no").asText()).isEqualTo(NUMBER);
        assertThat(body.get("start_dt").asText()).isEqualTo("20240229");
        assertThat(body.get("p_nm").asText()).isEqualTo("Representative");
        assertThat(body.size()).isEqualTo(3); // No empty optional fields, including p_nm2.
    }
    @Test void sendsConfirmedCompanyOnlyWhenCollected() throws Exception {
        reply(200,validation("01","01"));
        client.validate(NUMBER,date,"Representative"," Company ");
        verify(transport).post(any(),argThat(json -> json.contains("\"b_nm\":\"Company\"")),any());
    }
    @ParameterizedTest @CsvSource({"01,ACTIVE","02,SUSPENDED","03,CLOSED"})
    void mapsBusinessStateSeparatelyFromAuthenticity(String code,BusinessState state) throws Exception {
        reply(200,status(code));
        var result = client.lookupStatus("123 45 67891");
        assertThat(result.businessState()).isEqualTo(state);
        assertThat(result.authenticity()).isEqualTo(Authenticity.NOT_REQUESTED);
        verify(transport).post(argThat(uri -> uri.getPath().endsWith("/status")),eq("{\"b_no\":[\"1234567891\"]}"),any());
        reply(200,validation("01",code));
        assertThat(client.validate(NUMBER,date,"Representative",null).businessState()).isEqualTo(state);
    }
    @Test void valid02IsNotProviderFailureAndDoesNotPretendToIdentifyMismatchReason() throws Exception {
        reply(200,validation("02",null));
        var result = client.validate(NUMBER,date,"Representative",null);
        assertThat(result.authenticity()).isEqualTo(Authenticity.NOT_CONFIRMED);
        assertThat(result.failure()).isEqualTo(Failure.NONE);
    }
    @Test void handlesExplicitUnregisteredOrDeletedMessage() throws Exception {
        reply(200,"{\"status_code\":\"OK\",\"request_cnt\":1,\"match_cnt\":0,\"data\":[{\"b_no\":\"1234567891\",\"b_stt_cd\":\"\",\"tax_type\":\"국세청에 등록되지 않은 사업자등록번호입니다.\"}]}");
        assertThat(client.lookupStatus(NUMBER).businessState()).isEqualTo(BusinessState.NOT_FOUND_OR_DELETED);
    }
    @ParameterizedTest @ValueSource(strings = {"bad-json", "null", "{}", "[]", "{\"status_code\":\"OK\",\"request_cnt\":1,\"data\":[]}"})
    void rejectsInvalidJsonAndIncompleteResponse(String json) throws Exception {
        reply(200,json);
        assertThat(client.lookupStatus(NUMBER).authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
    }
    @Test void rejectsUnknownCodesDifferentNumbersAndTrailingJson() throws Exception {
        for (String body : new String[]{status("99"),status("01").replace(NUMBER,"9876543215"),status("01")+"{}"}) {
            reply(200,body);
            assertThat(client.lookupStatus(NUMBER).authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
        }
        for (String body : new String[]{validation("99",null),validation("01","01").replace(NUMBER,"9876543215"),
                validation("01","99"),validation("01","01").replace("\"valid_cnt\":1","\"valid_cnt\":0")}) {
            reply(200,body);
            assertThat(client.validate(NUMBER,date,"Representative",null).authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
        }
    }
    @ParameterizedTest @CsvSource({"400,REQUEST_REJECTED","401,AUTHENTICATION","403,AUTHENTICATION","411,REQUEST_REJECTED","413,REQUEST_REJECTED","429,RATE_LIMIT","500,PROVIDER_ERROR","503,PROVIDER_ERROR"})
    void httpErrorsNeverBecomeMismatchOrExposeBody(int http,Failure reason) throws Exception {
        reply(http,"private-provider-message fake+/=%key Representative 1234567891");
        var result = client.lookupStatus(NUMBER);
        assertThat(result.authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
        assertThat(result.failure()).isEqualTo(reason);
        assertThat(result.toString()).doesNotContain(NUMBER,"Representative","fake","private-provider");
    }
    @Test void timeoutAndNetworkExceptionsAreSanitized() throws Exception {
        when(transport.post(any(),anyString(),any())).thenThrow(new HttpTimeoutException("secret URI"));
        assertThat(client.lookupStatus(NUMBER).failure()).isEqualTo(Failure.TIMEOUT);
        doThrow(new java.io.IOException("secret URI")).when(transport).post(any(),anyString(),any());
        assertThat(client.lookupStatus(NUMBER).failure()).isEqualTo(Failure.NETWORK);
    }
    @Test void disabledOrMissingKeyNeverCallsTransport() {
        properties.setEnabled(false);
        assertThat(client.lookupStatus(NUMBER).failure()).isEqualTo(Failure.DISABLED);
        properties.setEnabled(true); properties.setApiKey("");
        assertThat(client.lookupStatus(NUMBER).failure()).isEqualTo(Failure.CONFIGURATION);
        verifyNoInteractions(transport);
    }
    @Test void providerBusinessErrorIsUnavailableAndNotMismatch() throws Exception {
        reply(200,"{\"status_code\":\"INTERNAL_ERROR\"}");
        assertThat(client.validate(NUMBER,date,"Representative",null).failure()).isEqualTo(Failure.PROVIDER_ERROR);
        assertThat(client.lookupStatus(NUMBER).authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
    }
    @Test void nestedMismatchedNumberCannotPassEvenForValid02() throws Exception {
        reply(200,validation("02","01").replace("\"status\":{\"b_no\":\"1234567891\"",
                "\"status\":{\"b_no\":\"9876543215\""));
        assertThat(client.validate(NUMBER,date,"Representative",null).authenticity()).isEqualTo(Authenticity.UNAVAILABLE);
    }
    @Test void interruptionPreservesThreadSignalWithoutExposingException() throws Exception {
        when(transport.post(any(),anyString(),any())).thenThrow(new InterruptedException("private URI"));
        try {
            assertThat(client.lookupStatus(NUMBER).failure()).isEqualTo(Failure.INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
    @Test void invalidInputsAreRejectedBeforeNetworkAndDoNotLeakSubmittedValues() {
        assertThatThrownBy(() -> client.lookupStatus("1234567890")).isInstanceOf(CustomException.class)
                .hasMessageNotContaining("1234567890");
        assertThatThrownBy(() -> client.validate(NUMBER,null,"Representative",null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> client.validate(NUMBER,date," ",null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> client.validate(NUMBER,LocalDate.of(0,1,1),"Representative",null)).isInstanceOf(CustomException.class);
        verifyNoInteractions(transport);
    }
}
