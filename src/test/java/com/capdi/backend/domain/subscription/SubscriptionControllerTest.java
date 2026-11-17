package com.capdi.backend.domain.subscription;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import com.capdi.backend.domain.subscription.controller.*;
import com.capdi.backend.domain.subscription.dto.SubscriptionDtos.*;
import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.PaymentStatus;
import com.capdi.backend.domain.subscription.service.SubscriptionService;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.global.config.SecurityConfig;
import com.capdi.backend.global.exception.*;
import com.capdi.backend.global.jwt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SubscriptionControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, SubscriptionController.class, SubscriptionExceptionHandler.class,
            JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean SubscriptionService service() { return mock(SubscriptionService.class); }
        @Bean JwtUtil jwt() { return new JwtUtil(UUID.randomUUID()+"-subscription-test", 60000); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    SubscriptionService service;
    JwtUtil jwt;
    static final String BODY = """
            {"plan_id":1,"registration_id":"00000000-0000-0000-0000-000000000001","registration_token":"private-test-token"}
            """;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters((Filter)context.getBean("springSecurityFilterChain")).build();
        service = context.getBean(SubscriptionService.class); jwt = context.getBean(JwtUtil.class);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    MockHttpServletRequestBuilder route(MockHttpServletRequestBuilder request, String path, UserTypeEnum role) {
        request.contextPath("/api").servletPath(path);
        if (role != null) request.header("Authorization", "Bearer " + jwt.generateToken(1L, role));
        return request;
    }
    MockHttpServletRequestBuilder create(UserTypeEnum role, String body) {
        return route(post("/api/expert/me/subscriptions"), "/expert/me/subscriptions", role)
                .header("Idempotency-Key", "test-key").contentType("application/json").content(body);
    }
    PaymentResponse payment(PaymentStatus status) {
        return new PaymentResponse("sub_test", 1, MembershipGradeEnum.SPECIAL, 1, new BigDecimal("12000"),
                "KRW", status, null, null, null, null);
    }

    @Test void requiresExpertJwtBeforeServiceCall() throws Exception {
        assertThat(mvc.perform(create(null, BODY)).andReturn().getResponse().getStatus()).isEqualTo(401);
        for (var role : new UserTypeEnum[]{UserTypeEnum.CLIENT, UserTypeEnum.ADMIN})
            assertThat(mvc.perform(create(role, BODY)).andReturn().getResponse().getStatus()).isEqualTo(403);
        verifyNoInteractions(service);
    }

    @Test void returns201ForCompletedCreation200ForReplayAnd202ForUnknown() throws Exception {
        when(service.create(eq(1L), eq("test-key"), any()))
                .thenReturn(new CreationResult(payment(PaymentStatus.PAID), true))
                .thenReturn(new CreationResult(payment(PaymentStatus.PAID), false))
                .thenReturn(new CreationResult(payment(PaymentStatus.UNKNOWN), true));
        var response = mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        var data = new ObjectMapper().readTree(response.getContentAsString()).path("data");
        assertThat(data.path("order_id").asText()).isEqualTo("sub_test");
        assertThat(data.path("status").asText()).isEqualTo("PAID");
        assertThat(response.getContentAsString()).doesNotContain("billingKey", "billing_key", "registration_token", "private-test-token");
        assertThat(mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "not-json", "{\"plan_id\":0}",
            "{\"plan_id\":1,\"registration_id\":\"x\",\"registration_token\":\"\"}"})
    void malformedRequestIs400WithoutLeakingToken(String body) throws Exception {
        var response = mvc.perform(create(UserTypeEnum.EXPERT, body)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test void providerDisabledReturns503AndDomainErrorsKeepTheirStatus() throws Exception {
        when(service.create(eq(1L), any(), any())).thenThrow(SubscriptionException.unavailable())
                .thenThrow(new CustomException(ErrorCode.FORBIDDEN));
        var response = mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SUBSCRIPTION_PROVIDER_UNAVAILABLE");
        assertThat(mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test void unexpectedProviderExceptionCannotExposeItsMessage() throws Exception {
        when(service.create(eq(1L), any(), any())).thenThrow(new IllegalStateException("private-test-token"));
        var response = mvc.perform(create(UserTypeEnum.EXPERT, BODY)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("private-test-token", "IllegalStateException");
    }

    @Test void planListIsPublicAndReadUsesJwtOwner() throws Exception {
        assertThat(mvc.perform(route(get("/api/subscription-plans"), "/subscription-plans", null))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        String path = "/expert/me/subscription/payments/sub_test";
        when(service.payment(1, "sub_test")).thenReturn(payment(PaymentStatus.PAID));
        assertThat(mvc.perform(route(get("/api" + path), path, UserTypeEnum.EXPERT))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        verify(service).payment(1, "sub_test");
    }
}
