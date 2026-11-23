package com.capdi.backend.global.jwt;

import com.capdi.backend.domain.auth.controller.AuthController;
import com.capdi.backend.domain.auth.dto.*;
import com.capdi.backend.domain.auth.service.AuthService;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.service.ExpertProfileProvisioningService;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.domain.auth.repository.RefreshTokenRepository;
import com.capdi.backend.global.config.SecurityConfig;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Actual SecurityConfig/JWT filter/DTOs/controller; mocked auth service, no HTTP server or DB. */
class AuthPublicEndpointContractTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,AuthController.class,JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,GlobalExceptionHandler.class,Markers.class})
    static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean AuthService authService(){return mock(AuthService.class);}
        @Bean JwtUtil jwtUtil(){return new JwtUtil(UUID.randomUUID()+"-isolated-test-only",60000);}
    }
    @RestController static class Markers {
        @GetMapping("/files/test-protected") Map<String,Boolean> protectedMarker(){return Map.of("allowed",true);}
        @GetMapping("/v3/api-docs") Map<String,String> docsMarker(){return Map.of("openapi","3.0.1");}
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;ObjectMapper mapper;AuthService service;JwtUtil jwt;
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "isolated-auth-test",Map.of("jwt.refresh-expiration","60000")));
        context.register(Config.class);context.refresh();mapper=context.getBean(ObjectMapper.class);
        service=context.getBean(AuthService.class);jwt=context.getBean(JwtUtil.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters((Filter)context.getBean("springSecurityFilterChain")).build();
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();context.close();}
    MockHttpServletRequestBuilder path(MockHttpServletRequestBuilder request,String servlet){
        return request.contextPath("/api").servletPath(servlet);
    }
    MvcResult send(String route,Object body) throws Exception {
        return mvc.perform(path(post("/api"+route),route).contentType("application/json")
                .content(mapper.writeValueAsBytes(body))).andReturn();
    }
    Map<String,Object> signup(){
        var body=new HashMap<String,Object>();body.put("userType","EXPERT");body.put("name","Synthetic Contract Expert");
        body.put("email","contract52@example.test");body.put("phone","01000000000");
        body.put("password","Synthetic52!Password");body.put("businessName","Synthetic Company");return body;
    }
    @Test void signupWithoutAuthorizationReachesControllerAndReturns201() throws Exception {
        var result=send("/auth/signup",signup());assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var capture=org.mockito.ArgumentCaptor.forClass(SignupRequest.class);verify(service).signup(capture.capture());
        assertThat(capture.getValue().getUserType()).isEqualTo(UserTypeEnum.EXPERT);
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("success").asBoolean()).isTrue();
    }
    @Test void loginWithoutAuthorizationUsesExactDtoAndResponseFields() throws Exception {
        when(service.login(any())).thenReturn(new AuthTokens("opaque-test-access","opaque-test-refresh","EXPERT",null));
        var result=send("/auth/login",Map.of("email","contract52@example.test","password","Synthetic52!Password"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var data=mapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.hasNonNull("accessToken")&&data.hasNonNull("refreshToken")).isTrue();
        assertThat(data.path("role").asText()).isEqualTo("EXPERT");verify(service).login(any(LoginRequest.class));
    }
    @Test void emptyLoginIsValidation400Not401() throws Exception {
        assertThat(send("/auth/login",Map.of()).getResponse().getStatus()).isEqualTo(400);verifyNoInteractions(service);
    }
    @Test void emptySignupIsValidation400Not401() throws Exception {
        assertThat(send("/auth/signup",Map.of()).getResponse().getStatus()).isEqualTo(400);verifyNoInteractions(service);
    }
    @ParameterizedTest(name="signup invalid case {index}")
    @ValueSource(strings={"PHONE_LETTERS","PHONE_SHORT","PASSWORD_NO_SPECIAL","PASSWORD_NO_LETTER"})
    void signupUsesRealBeanValidation(String variant) throws Exception {
        var body=signup();
        switch(variant){
            case "PHONE_LETTERS"->body.put("phone","not-a-phone");
            case "PHONE_SHORT"->body.put("phone","123");
            case "PASSWORD_NO_SPECIAL"->body.put("password","Synthetic52Password");
            default->body.put("password","12345678!");
        }
        assertThat(send("/auth/signup",body).getResponse().getStatus()).isEqualTo(400);verifyNoInteractions(service);
    }
    @Test void protectedEndpointWithoutJwtUsesCapdiEntryPoint() throws Exception {
        var response=mvc.perform(path(get("/api/files/test-protected"),"/files/test-protected")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(mapper.readTree(response.getContentAsString()).path("success").asBoolean()).isFalse();
    }
    @Test void expertJwtGrantsProtectedEndpoint() throws Exception {
        var response=mvc.perform(path(get("/api/files/test-protected"),"/files/test-protected")
                .header("Authorization","Bearer "+jwt.generateToken(1L,UserTypeEnum.EXPERT))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
    }
    @Test void clientJwtCannotUseExpertFiles() throws Exception {
        var response=mvc.perform(path(get("/api/files/test-protected"),"/files/test-protected")
                .header("Authorization","Bearer "+jwt.generateToken(2L,UserTypeEnum.CLIENT))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void docsPathIsPublicWithApiContextPath() throws Exception {
        assertThat(mvc.perform(path(get("/api/v3/api-docs"),"/v3/api-docs")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
    @Test void expertBusinessNameRequiredByRealServiceBeforeDatabaseAccess() throws Exception {
        var users=mock(UserRepository.class);var tokens=mock(RefreshTokenRepository.class);var clients=mock(ClientRepository.class);
        var provisioning=mock(ExpertProfileProvisioningService.class);var encoder=mock(PasswordEncoder.class);
        var actual=new AuthService(users,tokens,clients,provisioning,encoder,jwt);
        var body=signup();body.put("businessName"," ");
        var dto=mapper.convertValue(body,SignupRequest.class);
        var failure=catchThrowableOfType(()->actual.signup(dto),CustomException.class);
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.EXPERT_BUSINESS_NAME_REQUIRED);
        verifyNoInteractions(users,tokens,clients,provisioning,encoder);
    }
}
