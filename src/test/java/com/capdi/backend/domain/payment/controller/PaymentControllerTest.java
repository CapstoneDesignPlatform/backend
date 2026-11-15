package com.capdi.backend.domain.payment.controller;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.PaymentStatus;
import com.capdi.backend.domain.payment.service.PaymentOrderService;
import com.capdi.backend.domain.bid.controller.BidController;
import com.capdi.backend.domain.bid.service.BidService;
import com.capdi.backend.domain.bid.dto.ExpertSelectResponse;
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
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PaymentControllerTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,PaymentController.class,PaymentExceptionHandler.class,BidController.class,
            JwtAuthenticationEntryPoint.class,JwtAccessDeniedHandler.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean PaymentOrderService paymentOrderService(){return mock(PaymentOrderService.class);}
        @Bean BidService bidService(){return mock(BidService.class);}
        @Bean JwtUtil jwtUtil(){return new JwtUtil(UUID.randomUUID()+"-payment-test-only",60000);}
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;PaymentOrderService service;JwtUtil jwt;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters((Filter)context.getBean("springSecurityFilterChain")).build();
        service=context.getBean(PaymentOrderService.class);jwt=context.getBean(JwtUtil.class);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    MockHttpServletRequestBuilder route(MockHttpServletRequestBuilder r,String path,UserTypeEnum role) {
        r.contextPath("/api").servletPath(path);
        if(role!=null)r.header("Authorization","Bearer "+jwt.generateToken(1L,role));
        return r;
    }
    MockHttpServletRequestBuilder create(UserTypeEnum role,String body) {
        String path="/announcements/SYNTHETIC-57/payments";
        return route(post("/api"+path),path,role).header("Idempotency-Key","synthetic-key")
                .contentType("application/json").content(body);
    }
    PaymentResponse response() {
        return PaymentResponse.builder().paymentId(10L).orderId("pay_synthetic").announcementCode("SYNTHETIC-57")
                .bidId(20L).amount(new BigDecimal("1000000")).currency("KRW").status(PaymentStatus.CREATED)
                .createdAt(LocalDateTime.of(2026,1,1,0,0)).build();
    }
    @Test void creates201AndReplays200WithExactResponseContract() throws Exception {
        when(service.create(eq(1L),eq("SYNTHETIC-57"),eq("synthetic-key"),any()))
                .thenReturn(new PaymentOrderService.OrderResult(response(),true))
                .thenReturn(new PaymentOrderService.OrderResult(response(),false));
        var first=mvc.perform(create(UserTypeEnum.CLIENT,"{\"bid_id\":20,\"method\":\"CARD\"}")).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(201);
        var data=new ObjectMapper().readTree(first.getContentAsString()).path("data");
        assertThat(data.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("payment_id","order_id","announcement_code","bid_id","amount","currency","status","created_at");
        assertThat(data.path("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(mvc.perform(create(UserTypeEnum.CLIENT,"{\"bid_id\":20,\"method\":\"CARD\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
    @Test void unauthenticatedCreateIs401BeforeService() throws Exception {
        assertThat(mvc.perform(create(null,"{\"bid_id\":20,\"method\":\"CARD\"}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"EXPERT","ADMIN"})
    void wrongRoleCreateIs403(String role) throws Exception {
        assertThat(mvc.perform(create(UserTypeEnum.valueOf(role),"{\"bid_id\":20,\"method\":\"CARD\"}")).andReturn().getResponse().getStatus()).isEqualTo(403);
        verifyNoInteractions(service);
    }
    @Test void unauthenticatedReadIs401() throws Exception {
        assertThat(mvc.perform(route(get("/api/payments/10"),"/payments/10",null)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
    @Test void expertReadIs403() throws Exception {
        assertThat(mvc.perform(route(get("/api/payments/10"),"/payments/10",UserTypeEnum.EXPERT)).andReturn().getResponse().getStatus()).isEqualTo(403);
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={
            "{}","{\"bid_id\":20}","{\"method\":\"CARD\"}","{\"bid_id\":0,\"method\":\"CARD\"}",
            "{\"bid_id\":-1,\"method\":\"CARD\"}","{\"bid_id\":20,\"method\":null}",
            "{\"bid_id\":20,\"method\":\"INVALID\"}","not-json"})
    void invalidRequestIsSafe400(String body) throws Exception {
        var r=mvc.perform(create(UserTypeEnum.CLIENT,body)).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(400);assertThat(r.getContentAsString()).doesNotContain("stackTrace","Exception");
        verifyNoInteractions(service);
    }
    @Test void ownerReadDelegatesAuthenticatedUserAndId() throws Exception {
        when(service.get(1L,10L)).thenReturn(response());
        assertThat(mvc.perform(route(get("/api/payments/10"),"/payments/10",UserTypeEnum.CLIENT))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        verify(service).get(1L,10L);
    }
    @Test void ownershipErrorIs403() throws Exception {
        when(service.get(1L,10L)).thenThrow(new CustomException(ErrorCode.FORBIDDEN));
        assertThat(mvc.perform(route(get("/api/payments/10"),"/payments/10",UserTypeEnum.CLIENT))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
    @Test void malformedPaymentIdIs400() throws Exception {
        assertThat(mvc.perform(route(get("/api/payments/wrong"),"/payments/wrong",UserTypeEnum.CLIENT))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }
    @Test void originalSelectionContractStillAcceptsPositiveFinalAmount() throws Exception {
        BidService bid=context.getBean(BidService.class);
        when(bid.selectExpert(eq(1L),eq("SYNTHETIC-57"),eq(20L),any()))
                .thenReturn(ExpertSelectResponse.builder().bidId(20L).finalAmount(new BigDecimal("1000000")).build());
        String path="/announcements/SYNTHETIC-57/bids/20/select";
        var r=mvc.perform(route(patch("/api"+path),path,UserTypeEnum.CLIENT)
                .contentType("application/json").content("{\"final_amount\":1000000}")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(r.getContentAsString()).at("/data/final_amount").decimalValue()).isEqualByComparingTo("1000000");
    }
    @Test void originalSelectionStillRejectsNullFinalAmount() throws Exception {
        String path="/announcements/SYNTHETIC-57/bids/20/select";
        assertThat(mvc.perform(route(patch("/api"+path),path,UserTypeEnum.CLIENT).contentType("application/json")
                .content("{\"final_amount\":null}")).andReturn().getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(context.getBean(BidService.class));
    }
}
