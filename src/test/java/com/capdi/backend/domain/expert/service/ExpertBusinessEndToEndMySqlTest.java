package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.client.NtsHttpTransport;
import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.domain.expert.repository.*;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real loopback HTTP -> security/controller/service/JPA -> isolated MySQL and temporary files. */
@Tag("mysql-integration")
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "server.address=127.0.0.1",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=false", "nts.business.enabled=true",
        "nts.business.api-key=SYNTHETIC_E2E_NOT_A_REAL_KEY"})
class ExpertBusinessEndToEndMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_issue52_http_e2e").withUsername("issue52_http_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withLabel("capdi.test.scope","issue52-http-e2e");
    @TempDir static Path uploadRoot;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("file.storage.upload-dir",()->uploadRoot.toString());
        registry.add("jwt.secret",()->UUID.randomUUID()+"-synthetic-test-only");
    }
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired ExpertFileRepository files;
    @Autowired ExpertProfileRepository profiles;
    @Autowired BusinessRegistrationInfoRepository infos;
    @Autowired BusinessVerificationRepository history;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean GoogleVisionOcrClient vision;
    @MockitoBean NtsHttpTransport transport;
    final AtomicInteger providerCalls=new AtomicInteger();
    static final String NUMBER="1234567891";
    static final String TEXT="SYNTHETIC TEST ONLY - NOT A GOVERNMENT CERTIFICATE\n사업자등록번호: 123-45-67891\n대표자: Synthetic Owner\n상호: Synthetic Company\n개업연월일: 2024-02-29";
    record Session(long userId,String token) { @Override public String toString(){return "SyntheticSession[redacted]";} }
    static class Flow {
        Session session; long file,info,ocr,input;
        @Override public String toString(){return "SyntheticFlow[redacted]";}
    }

    @BeforeEach void isolatedMocks() throws Exception {
        assertThat(mockingDetails(vision).isMock()).isTrue();
        assertThat(mockingDetails(transport).isMock()).isTrue();
        try(var connection=dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("capdi_issue52_http_e2e");
            assertThat(connection.getMetaData().getURL()).startsWith(MYSQL.getJdbcUrl());
        }
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((MimeTypeEnum)call.getArgument(1)).isEqualTo(MimeTypeEnum.PDF);
            try(var pdf=Loader.loadPDF(Path.of((String)call.getArgument(0)).toFile())) {
                assertThat(pdf.getNumberOfPages()).isEqualTo(1);
                assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("SYNTHETIC TEST ONLY");
                assertThat(pdf.getDocumentCatalog().getOpenAction()).isNull();
                assertThat(pdf.getPage(0).getAnnotations()).isEmpty();
            }
            return TEXT;
        }).when(vision).requestOcr(anyString(),any());
        doAnswer(call->{
            providerCalls.incrementAndGet();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var uri=(java.net.URI)call.getArgument(0);
            var request=mapper.readTree((String)call.getArgument(1));
            if(uri.getPath().endsWith("/validate")) {
                assertThat(request.path("businesses").size()).isEqualTo(1);
                assertThat(request.at("/businesses/0/b_no").asText()).isEqualTo(NUMBER);
                assertThat(request.at("/businesses/0/start_dt").asText()).isEqualTo("20240229");
                return new NtsHttpTransport.Response(200,validation(true,"01"));
            }
            assertThat(request.path("b_no").size()).isEqualTo(1);
            return new NtsHttpTransport.Response(200,status("01"));
        }).when(transport).post(any(),anyString(),any());
    }

    String base(){return "http://127.0.0.1:"+port+"/api";}
    HttpHeaders headers(Session s) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(s!=null)headers.setBearerAuth(s.token());return headers;
    }
    JsonNode json(Session s,HttpMethod method,String path,Object body,int expected) throws Exception {
        var response=http.exchange(base()+path,method,new HttpEntity<>(body,headers(s)),String.class);
        // Never print a response/request/token on failure.
        assertThat(response.getStatusCode().value()).as("fixed E2E HTTP stage status").isEqualTo(expected);
        var root=mapper.readTree(response.getBody());
        assertThat(root.path("success").asBoolean()).isEqualTo(expected>=200&&expected<300);
        return root;
    }
    Session signup() throws Exception {
        String email=UUID.randomUUID()+"@example.test",password=UUID.randomUUID()+"!a9";
        json(null,HttpMethod.POST,"/auth/signup",Map.of("userType","EXPERT","name","Synthetic E2E Expert",
                "email",email,"phone","01000000000","password",password,"businessName","Synthetic Company"),201);
        var login=json(null,HttpMethod.POST,"/auth/login",Map.of("email",email,"password",password),200).path("data");
        String token=login.path("accessToken").asText();
        if(token.isBlank())throw new AssertionError("Missing synthetic session token");
        var temporary=new Session(0,token);
        long id=json(temporary,HttpMethod.GET,"/expert/me/profile",null,200).at("/data/user/id").asLong();
        return new Session(id,token);
    }
    byte[] pdf(int pages) throws Exception {
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("SYNTHETIC TEST ONLY");
            for(int i=0;i<pages;i++)doc.addPage(new PDPage());
            try(var content=new PDPageContentStream(doc,doc.getPage(0))) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);
                content.newLineAtOffset(40,740);
                for(String line:List.of("SYNTHETIC TEST ONLY - NOT A GOVERNMENT CERTIFICATE",
                        "Business number: 123-45-67891","Representative: Synthetic Owner",
                        "Company: Synthetic Company","Opening date: 2024-02-29")) {
                    content.showText(line);content.newLineAtOffset(0,-18);
                }
                content.endText();
            }
            doc.save(bytes);return bytes.toByteArray();
        }
    }
    long upload(Session s,byte[] bytes,int expected) throws Exception {
        return upload(s,bytes,expected,"BUSINESS_REGISTRATION");
    }
    long upload(Session s,byte[] bytes,int expected,String purpose) throws Exception {
        var body=new LinkedMultiValueMap<String,Object>();
        var resource=new ByteArrayResource(bytes){@Override public String getFilename(){return "synthetic-test-only.pdf";}};
        var part=new HttpHeaders();part.setContentType(MediaType.APPLICATION_PDF);
        body.add("file",new HttpEntity<>(resource,part));body.add("purpose",purpose);
        var headers=headers(s);headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var response=http.exchange(base()+"/files",HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
        return expected==200 ? mapper.readTree(response.getBody()).at("/data/file/id").asLong() : 0;
    }
    void ocr(Flow f) throws Exception {
        json(f.session,HttpMethod.POST,"/files/"+f.file+"/ocr",null,200);
        var state=json(f.session,HttpMethod.GET,"/files/"+f.file+"/ocr",null,200).path("data");
        assertThat(state.path("status").asText()).isEqualTo("SUCCEEDED");f.ocr=state.path("revision").asLong();
    }
    Map<String,Object> business(long file,String company) {
        return Map.of("file_id",file,"business_number",NUMBER,"representative_name","Synthetic Owner","company_name",company,"opening_date","2024-02-29");
    }
    void confirm(Flow f,String company) throws Exception {
        var body=new HashMap<>(business(f.file,company));body.put("ocr_revision",f.ocr);
        f.input=json(f.session,HttpMethod.PUT,"/expert/me/business-registration-info/"+f.info+"/confirmation",body,200).at("/data/input_revision").asLong();
    }
    Flow unconfirmedFlow() throws Exception {
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);ocr(f);
        // Deliberately different user input demonstrates extraction and confirmation are separate.
        var created=json(f.session,HttpMethod.POST,"/expert/me/business-registration-info",business(f.file,"Initial Company"),200).path("data");
        f.info=created.path("id").asLong();f.input=created.path("input_revision").asLong();
        return f;
    }
    Flow flow() throws Exception {var f=unconfirmedFlow();confirm(f,"Synthetic Company");return f;}
    Map<String,Object> request(Flow f,String kind) {
        return Map.of("kind",kind,"file_id",f.file,"ocr_revision",f.ocr,"input_revision",f.input);
    }
    String verificationPath(Flow f){return "/expert/me/business-registration-info/"+f.info+"/verifications";}
    JsonNode verifyFlow(Flow f,String kind) throws Exception {
        return json(f.session,HttpMethod.POST,verificationPath(f),request(f,kind),200).path("data");
    }
    JsonNode latest(Flow f) throws Exception {return json(f.session,HttpMethod.GET,verificationPath(f)+"/latest",null,200).path("data");}
    long rows(Flow f){return history.findAll().stream().filter(row->row.getBusinessRegistrationInfoId().equals(f.info)).count();}
    static String status(String code){return "{\"status_code\":\"OK\",\"request_cnt\":1,\"match_cnt\":1,\"data\":[{\"b_no\":\""+NUMBER+"\",\"b_stt_cd\":\""+code+"\"}]}";}
    static String validation(boolean match,String code){return "{\"status_code\":\"OK\",\"request_cnt\":1,\"valid_cnt\":"+(match?1:0)+",\"data\":[{\"b_no\":\""+NUMBER+"\",\"valid\":\""+(match?"01":"02")+"\""+(match?",\"status\":{\"b_no\":\""+NUMBER+"\",\"b_stt_cd\":\""+code+"\"}":"")+"}]}";}

    Flow uncertainCompanyFlow(String kind) throws Exception {
        String companyLine=switch(kind) {
            case "MISSING" -> "";
            case "CONFLICT" -> "상호: Candidate Alpha\n법인명: Candidate Beta";
            default -> "상호:\nSynthetic Owner";
        };
        String synthetic=TEXT.replace("상호: Synthetic Company",companyLine);
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return synthetic;
        }).when(vision).requestOcr(anyString(),any());
        return unconfirmedFlow();
    }

    JsonNode extraction(Flow f) throws Exception {
        return json(f.session,HttpMethod.GET,"/expert/me/business-registration-info/"+f.info+"/ocr-extraction",null,200).path("data");
    }

    @ParameterizedTest @ValueSource(strings={"FALLBACK","MISSING","CONFLICT"})
    void uncertainCompanyCanBeExplicitlyConfirmedAndOnlyUserInputReachesNts(String kind) throws Exception {
        var f=uncertainCompanyFlow(kind);var before=review(f);
        var extracted=extraction(f);var company=extracted.at("/extracted/company_name");
        assertThat(extracted.path("ocr_status").asText()).isEqualTo("SUCCEEDED");
        assertThat(extracted.path("file_id").asLong()).isEqualTo(f.file);
        assertThat(extracted.path("ocr_revision").asLong()).isEqualTo(f.ocr);
        assertThat(company.path("status").asText()).isEqualTo(kind.equals("MISSING")?"MISSING":"NEEDS_CONFIRMATION");
        assertThat(company.path("value").isNull()).isTrue();
        assertThat(company.path("candidates").size()).isEqualTo(kind.equals("MISSING")?0:kind.equals("CONFLICT")?2:1);
        if(kind.equals("FALLBACK")) {
            var candidate=company.path("candidates").get(0);
            assertThat(candidate.path("reason").asText()).isEqualTo("UNVERIFIED_NEXT_LINE_ASSOCIATION");
            assertThat(candidate.path("valid").asBoolean()).isTrue();
            assertThat(candidate.path("evidence").asText().contains("\n")).isTrue();
            assertThat(candidate.path("raw").asText().isBlank()).isFalse();
        }
        assertThat(extracted.at("/user_input/company_name").asText().equals("Initial Company")).isTrue();
        for(String operation:List.of("STATUS","VALIDATE"))
            json(f.session,HttpMethod.POST,verificationPath(f),request(f,operation),409);
        verifyNoInteractions(transport);assertThat(rows(f)).isZero();
        long previousInput=f.input;
        String userCompany="User Confirmed Synthetic Company";
        confirm(f,userCompany);
        assertThat(f.input).isGreaterThan(previousInput);
        var saved=infos.findById(f.info).orElseThrow();
        assertThat(saved.getCompanyName().equals(userCompany)).isTrue();
        assertThat(saved.isConfirmedFor(f.file,f.ocr)).isTrue();
        assertThat(saved.getConfirmedInputRevision()).isEqualTo(f.input);
        var calls=new AtomicInteger();
        doAnswer(call->{
            calls.incrementAndGet();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var body=mapper.readTree((String)call.getArgument(1));
            if(((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")) {
                assertThat(body.at("/businesses/0/b_nm").asText().equals(userCompany)).isTrue();
                assertThat(body.at("/businesses/0/p_nm").asText().equals("Synthetic Owner")).isTrue();
                assertThat(body.at("/businesses/0/b_no").asText().equals(NUMBER)).isTrue();
                assertThat(body.at("/businesses/0/start_dt").asText()).isEqualTo("20240229");
                return new NtsHttpTransport.Response(200,validation(true,"01"));
            }
            assertThat(body.path("b_no").size()).isEqualTo(1);
            assertThat(body.path("b_no").get(0).asText().equals(NUMBER)).isTrue();
            assertThat(body.has("businesses")).isFalse();
            return new NtsHttpTransport.Response(200,status("01"));
        }).when(transport).post(any(),anyString(),any());
        var statusResult=verifyFlow(f,"STATUS");
        assertThat(statusResult.path("authenticity").asText()).isEqualTo("NOT_REQUESTED");
        var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("execution_status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(result.path("business_state").asText()).isEqualTo("ACTIVE");
        assertThat(result.path("validity").asText()).isEqualTo("CURRENT");
        assertThat(result.path("input_revision").asLong()).isEqualTo(f.input);
        assertThat(result.path("ocr_revision").asLong()).isEqualTo(f.ocr);
        assertThat(latest(f).path("execution_id").asText().equals(result.path("execution_id").asText())).isTrue();
        var row=history.findFirstByBusinessRegistrationInfoIdOrderByIdDesc(f.info).orElseThrow();
        assertThat(row.getInputRevision()).isEqualTo(f.input);assertThat(row.getOcrRevision()).isEqualTo(f.ocr);
        assertThat(row.getFileId()).isEqualTo(f.file);assertThat(row.getCompletedAt()).isNotNull();
        assertThat(row.getFileSha256()).hasSize(64);assertThat(rows(f)).isEqualTo(2);assertThat(calls.get()).isEqualTo(2);
        var afterExtraction=extraction(f);
        assertThat(afterExtraction.at("/extracted/company_name/value").isNull()).isTrue();
        assertThat(afterExtraction.at("/user_input/company_name").asText().equals(userCompany)).isTrue();
        assertThat(review(f)).isEqualTo(before);
    }

    @ParameterizedTest(name="invalid confirmation case {index}")
    @ValueSource(strings={"", "123", "()", "Invalid\nName"})
    void invalidManualCompanyCannotConfirmOrReachNts(String invalid) throws Exception {
        var f=uncertainCompanyFlow("FALLBACK");var before=review(f);long input=f.input;
        var body=new HashMap<>(business(f.file,invalid));body.put("ocr_revision",f.ocr);
        json(f.session,HttpMethod.PUT,"/expert/me/business-registration-info/"+f.info+"/confirmation",body,400);
        var saved=infos.findById(f.info).orElseThrow();
        assertThat(saved.getInputRevision()).isEqualTo(input);assertThat(saved.getConfirmedInputRevision()).isNull();
        assertThat(saved.getCompanyName().equals("Initial Company")).isTrue();
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        verifyNoInteractions(transport);assertThat(rows(f)).isZero();assertThat(review(f)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"OCR","FILE"})
    void uncertainCompanyConfirmationRejectsOutdatedSource(String changed) throws Exception {
        var f=uncertainCompanyFlow("FALLBACK");
        var old=new HashMap<>(business(f.file,"User Confirmed Synthetic Company"));old.put("ocr_revision",f.ocr);
        if(changed.equals("OCR"))ocr(f);else old.put("file_id",f.file+1000000);
        json(f.session,HttpMethod.PUT,"/expert/me/business-registration-info/"+f.info+"/confirmation",old,409);
        assertThat(infos.findById(f.info).orElseThrow().getConfirmedInputRevision()).isNull();
        verifyNoInteractions(transport);assertThat(rows(f)).isZero();
    }

    @Test void editedManualCompanyInvalidatesConfirmationAndOldMatchButPreservesHistory() throws Exception {
        var f=uncertainCompanyFlow("FALLBACK");confirm(f,"First Confirmed Synthetic Company");
        var matched=verifyFlow(f,"VALIDATE");long oldInput=f.input;var oldRequest=request(f,"VALIDATE");
        f.input=json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,
                business(f.file,"Edited Synthetic Company"),200).at("/data/input_revision").asLong();
        assertThat(f.input).isGreaterThan(oldInput);
        assertThat(infos.findById(f.info).orElseThrow().getConfirmedInputRevision()).isNull();
        var stale=latest(f);assertThat(stale.path("validity").asText()).isEqualTo("STALE");
        assertThat(stale.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(stale.path("input_revision").asLong()).isEqualTo(oldInput);
        assertThat(stale.path("execution_id").asText().equals(matched.path("execution_id").asText())).isTrue();
        clearInvocations(transport);
        json(f.session,HttpMethod.POST,verificationPath(f),oldRequest,409);
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        verifyNoInteractions(transport);assertThat(rows(f)).isEqualTo(1);
        confirm(f,"Edited Synthetic Company");
        assertThat(verifyFlow(f,"VALIDATE").path("validity").asText()).isEqualTo("CURRENT");
        assertThat(rows(f)).isEqualTo(2);
    }

    @Test void syntacticallyValidWrongCompanyIsUserInputNotAnOcrOrNtsMatch() throws Exception {
        var f=uncertainCompanyFlow("FALLBACK");confirm(f,"Other User Entered Synthetic Company");
        doAnswer(call->new NtsHttpTransport.Response(200,
                ((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")?validation(false,"01"):status("01")))
                .when(transport).post(any(),anyString(),any());
        var before=review(f);var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("authenticity").asText()).isEqualTo("NOT_CONFIRMED");
        assertThat(result.path("validity").asText()).isEqualTo("CURRENT");
        assertThat(review(f)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"PENDING","APPROVED","REJECTED"})
    void manuallyConfirmedFallbackAndNtsFailurePreserveFileAndApprovedProfile(String fileState) throws Exception {
        var f=uncertainCompanyFlow("FALLBACK");seedReview(f,fileState,"UNCHANGED");var before=review(f);
        confirm(f,"User Confirmed Synthetic Company");
        doThrow(new HttpTimeoutException("Synthetic timeout")).when(transport).post(any(),anyString(),any());
        var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("execution_status").asText()).isEqualTo("FAILED");
        assertThat(result.path("failure").asText()).isEqualTo("TIMEOUT");
        assertThat(result.path("authenticity").asText()).isEqualTo("UNAVAILABLE");
        assertThat(latest(f).path("validity").asText()).isEqualTo("CURRENT");
        assertThat(rows(f)).isEqualTo(1);assertThat(review(f)).isEqualTo(before);
    }

    JsonNode fileExtraction(Flow f) throws Exception {
        return json(f.session,HttpMethod.GET,"/files/"+f.file+"/ocr-extraction",null,200).path("data");
    }
    boolean confirmationCurrent(Flow f) throws Exception {
        return extraction(f).at("/user_input/confirmation_current").asBoolean();
    }

    @ParameterizedTest @ValueSource(strings={"INPUT","OCR","FILE"})
    void ocrFirstRegistrationConfirmationAndReconfirmationUseLatestSnapshot(String change) throws Exception {
        doReturn(TEXT.replace("상호: Synthetic Company","상호:\nSynthetic Owner"))
                .when(vision).requestOcr(anyString(),any());
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);ocr(f);
        long infoCount=infos.count(),historyCount=history.count(),fileCount=files.count();var reviewBefore=review(f);
        var extracted=fileExtraction(f);
        assertThat(extracted.path("business_registration_info_id").isNull()).isTrue();
        assertThat(extracted.path("user_input").isNull()).isTrue();
        assertThat(extracted.path("file_id").asLong()).isEqualTo(f.file);
        assertThat(extracted.path("ocr_revision").asLong()).isEqualTo(f.ocr);
        assertThat(extracted.at("/extracted/business_number/status").asText()).isEqualTo("CANDIDATE");
        assertThat(extracted.at("/extracted/representative_name/status").asText()).isEqualTo("CANDIDATE");
        assertThat(extracted.at("/extracted/opening_date/status").asText()).isEqualTo("CANDIDATE");
        assertThat(extracted.at("/extracted/company_name/status").asText()).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(extracted.at("/extracted/company_name/value").isNull()).isTrue();
        assertThat(extracted.at("/extracted/company_name/candidates/0/reason").asText()).isEqualTo("UNVERIFIED_NEXT_LINE_ASSOCIATION");
        assertThat(extracted.has("text")).isFalse();
        fileExtraction(f); // Repeated GET must not create any domain rows or rewrite review state.
        assertThat(infos.count()).isEqualTo(infoCount);assertThat(history.count()).isEqualTo(historyCount);
        assertThat(files.count()).isEqualTo(fileCount);assertThat(review(f)).isEqualTo(reviewBefore);
        var created=json(f.session,HttpMethod.POST,"/expert/me/business-registration-info",
                business(f.file,"Manually Entered Synthetic Company"),200).path("data");
        f.info=created.path("id").asLong();f.input=created.path("input_revision").asLong();
        assertThat(created.path("confirmation_current").asBoolean()).isFalse();
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        verifyNoInteractions(transport);
        confirm(f,"Manually Entered Synthetic Company");
        assertThat(confirmationCurrent(f)).isTrue();
        assertThat(extraction(f).at("/user_input/ocr_revision").asLong()).isEqualTo(f.ocr);
        var first=verifyFlow(f,"VALIDATE");var oldConfirmation=new HashMap<>(business(f.file,"Manually Entered Synthetic Company"));
        oldConfirmation.put("ocr_revision",f.ocr);
        if(change.equals("INPUT")) {
            f.input=json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,
                    business(f.file,"Updated Synthetic Company"),200).at("/data/input_revision").asLong();
        } else {
            if(change.equals("FILE"))f.file=upload(f.session,pdf(1),200);
            ocr(f);
            if(change.equals("FILE"))f.input=json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,
                    business(f.file,"Updated Synthetic Company"),200).at("/data/input_revision").asLong();
            json(f.session,HttpMethod.PUT,"/expert/me/business-registration-info/"+f.info+"/confirmation",oldConfirmation,409);
        }
        assertThat(confirmationCurrent(f)).isFalse();
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        confirm(f,"Reconfirmed Synthetic Company");assertThat(confirmationCurrent(f)).isTrue();
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var body=mapper.readTree((String)call.getArgument(1));
            assertThat(body.at("/businesses/0/b_nm").asText().equals("Reconfirmed Synthetic Company")).isTrue();
            return new NtsHttpTransport.Response(200,validation(true,"01"));
        }).when(transport).post(any(),anyString(),any());
        var second=verifyFlow(f,"VALIDATE");
        assertThat(second.path("validity").asText()).isEqualTo("CURRENT");
        assertThat(second.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(second.path("input_revision").asLong()).isEqualTo(f.input);
        assertThat(rows(f)).isEqualTo(2);
        assertThat(history.findAll().stream().anyMatch(row->row.getBusinessRegistrationInfoId().equals(f.info)
                &&row.getExecutionId().equals(first.path("execution_id").asText()))).isTrue();
        // The file-only API never guesses a linked business-info record, even after registration.
        assertThat(fileExtraction(f).path("user_input").isNull()).isTrue();
        assertThat(files.findById(f.file).orElseThrow().getVerificationStatus().name()).isEqualTo("PENDING");
    }

    @Test void fileExtractionRejectsForeignMissingWrongPurposeAndAnonymousAccess() throws Exception {
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);
        json(signup(),HttpMethod.GET,"/files/"+f.file+"/ocr-extraction",null,403);
        json(f.session,HttpMethod.GET,"/files/9223372036854775807/ocr-extraction",null,404);
        long certificate=upload(f.session,pdf(1),200,"CERTIFICATE");
        json(f.session,HttpMethod.GET,"/files/"+certificate+"/ocr-extraction",null,400);
        json(null,HttpMethod.GET,"/files/"+f.file+"/ocr-extraction",null,401);
        verifyNoInteractions(transport);
    }

    @ParameterizedTest @ValueSource(strings={"NOT_RUN","RUNNING","FAILED","EMPTY"})
    void fileExtractionReportsNonSuccessWithoutReusingOldOcr(String state) throws Exception {
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);long count=infos.count();
        if(!state.equals("NOT_RUN")) {
            ocr(f);
            if(state.equals("RUNNING"))new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx->files.findById(f.file).orElseThrow().startOcr(UUID.randomUUID().toString(),java.time.LocalDateTime.now()));
            else {
                if(state.equals("EMPTY"))doReturn(" ").when(vision).requestOcr(anyString(),any());
                else doThrow(new CustomException(ErrorCode.OCR_FAILED)).when(vision).requestOcr(anyString(),any());
                json(f.session,HttpMethod.POST,"/files/"+f.file+"/ocr",null,state.equals("EMPTY")?422:502);
            }
        }
        var data=fileExtraction(f);
        assertThat(data.path("ocr_status").asText()).isEqualTo(state);
        assertThat(data.path("extraction_status").asText()).isEqualTo("OCR_NOT_SUCCEEDED");
        for(String field:List.of("business_number","representative_name","company_name","opening_date")) {
            assertThat(data.at("/extracted/"+field+"/status").asText()).isEqualTo("MISSING");
            assertThat(data.at("/extracted/"+field+"/candidates").size()).isZero();
        }
        assertThat(infos.count()).isEqualTo(count);verifyNoInteractions(transport);
    }

    @Test void nonExpertCannotUseFileExtraction() throws Exception {
        var owner=new Flow();owner.session=signup();owner.file=upload(owner.session,pdf(1),200);
        String email=UUID.randomUUID()+"@example.test",password=UUID.randomUUID()+"!a9";
        json(null,HttpMethod.POST,"/auth/signup",Map.of("userType","CLIENT","name","Synthetic Client",
                "email",email,"phone","01000000000","password",password,"businessName","Synthetic Client Company"),201);
        String token=json(null,HttpMethod.POST,"/auth/login",Map.of("email",email,"password",password),200)
                .at("/data/accessToken").asText();
        if(token.isBlank())throw new AssertionError("Missing synthetic client token");
        json(new Session(0,token),HttpMethod.GET,"/files/"+owner.file+"/ocr-extraction",null,403);
        verifyNoInteractions(transport);
    }

    @ParameterizedTest @ValueSource(strings={"RUNNING","FAILED","EMPTY"})
    void previouslyConfirmedInputIsNotCurrentDuringNonSuccessfulOcr(String state) throws Exception {
        var f=flow();assertThat(confirmationCurrent(f)).isTrue();
        seedReview(f,"PENDING",state);
        var data=extraction(f);
        assertThat(data.path("ocr_status").asText()).isEqualTo(state);
        assertThat(data.at("/user_input/confirmation_current").asBoolean()).isFalse();
        assertThat(data.at("/user_input/ocr_revision").asLong()).isEqualTo(data.path("ocr_revision").asLong());
        clearInvocations(transport);
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        verifyNoInteractions(transport);
    }

    @Test void confirmationIsNotNtsMatchOrAdministratorApprovalAndFailsWhenOcrFails() throws Exception {
        var f=flow();var before=review(f);assertThat(confirmationCurrent(f)).isTrue();
        doAnswer(call->new NtsHttpTransport.Response(200,
                ((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")?validation(false,"01"):status("01")))
                .when(transport).post(any(),anyString(),any());
        assertThat(verifyFlow(f,"VALIDATE").path("authenticity").asText()).isEqualTo("NOT_CONFIRMED");
        assertThat(confirmationCurrent(f)).isTrue();assertThat(review(f)).isEqualTo(before);
        doThrow(new CustomException(ErrorCode.OCR_FAILED)).when(vision).requestOcr(anyString(),any());
        json(f.session,HttpMethod.POST,"/files/"+f.file+"/ocr",null,502);
        assertThat(confirmationCurrent(f)).isFalse();
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void extractionDoesNotLogRawOcrTokenOrCredential(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String canary="SYNTHETIC_PRIVATE_OCR_CANARY_52";
        doReturn(TEXT+"\n"+canary).when(vision).requestOcr(anyString(),any());
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);ocr(f);fileExtraction(f);
        assertThat(output.getAll().contains(canary)).isFalse();
        assertThat(output.getAll().contains(f.session.token())).isFalse();
        assertThat(output.getAll().contains("SYNTHETIC_E2E_NOT_A_REAL_KEY")).isFalse();
        verifyNoInteractions(transport);
    }

    @Test void identicalLatestConfirmationKeepsExistingCurrentHistoryWithoutAnotherNtsCall() throws Exception {
        var f=flow();var first=verifyFlow(f,"VALIDATE");long input=f.input,ocr=f.ocr;
        clearInvocations(transport);
        confirm(f,"Synthetic Company");
        assertThat(f.input).isEqualTo(input);assertThat(f.ocr).isEqualTo(ocr);
        assertThat(confirmationCurrent(f)).isTrue();
        var current=latest(f);
        assertThat(current.path("execution_id").asText()).isEqualTo(first.path("execution_id").asText());
        assertThat(current.path("validity").asText()).isEqualTo("CURRENT");
        assertThat(rows(f)).isEqualTo(1);verifyNoInteractions(transport);
    }

    @ParameterizedTest @ValueSource(strings={"INPUT","OCR"})
    void reconfirmationCannotReviveOldHistoryEvenWhenUserValuesReturnToOriginal(String change) throws Exception {
        var f=flow();var first=verifyFlow(f,"VALIDATE");long oldInput=f.input,oldOcr=f.ocr;
        clearInvocations(transport);
        if(change.equals("INPUT")) {
            f.input=json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,
                    business(f.file,"Changed Synthetic Company"),200).at("/data/input_revision").asLong();
        } else ocr(f); // Mock Vision returns identical text; revision must still change.
        assertThat(confirmationCurrent(f)).isFalse();
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
        confirm(f,"Synthetic Company");
        assertThat(confirmationCurrent(f)).isTrue();
        if(change.equals("INPUT"))assertThat(f.input).isGreaterThan(oldInput);
        else {assertThat(f.input).isEqualTo(oldInput);assertThat(f.ocr).isGreaterThan(oldOcr);}
        var stale=latest(f);
        assertThat(stale.path("execution_id").asText()).isEqualTo(first.path("execution_id").asText());
        assertThat(stale.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(stale.path("validity").asText()).isEqualTo("STALE");
        assertThat(stale.path("input_revision").asLong()).isEqualTo(oldInput);
        assertThat(stale.path("ocr_revision").asLong()).isEqualTo(oldOcr);
        assertThat(rows(f)).isEqualTo(1);verifyNoInteractions(transport);
    }

    @Test void fullHttpPdfOcrExtractionConfirmationStatusValidateAndHistory() throws Exception {
        var f=unconfirmedFlow();
        var extracted=json(f.session,HttpMethod.GET,"/expert/me/business-registration-info/"+f.info+"/ocr-extraction",null,200).path("data");
        assertThat(extracted.at("/extracted/business_number/value").asText()).isEqualTo(NUMBER);
        assertThat(extracted.at("/extracted/representative_name/value").asText()).isEqualTo("Synthetic Owner");
        assertThat(extracted.at("/extracted/company_name/value").asText()).isEqualTo("Synthetic Company");
        assertThat(extracted.at("/extracted/opening_date/value").asText()).isEqualTo("2024-02-29");
        assertThat(extracted.path("reference_only").asBoolean()).isTrue();
        assertThat(extracted.path("page_information_available").asBoolean()).isFalse();
        assertThat(extracted.at("/user_input/company_name").asText()).isEqualTo("Initial Company");
        confirm(f,"Corrected Synthetic Company");
        var confirmed=infos.findById(f.info).orElseThrow();
        assertThat(confirmed.getCompanyName()).isEqualTo("Corrected Synthetic Company");
        assertThat(confirmed.getConfirmedInputRevision()).isEqualTo(f.input);
        assertThat(confirmed.getConfirmedOcrRevision()).isEqualTo(f.ocr);
        assertThat(confirmed.getOpeningDate()).isEqualTo(java.time.LocalDate.of(2024,2,29));
        var file=files.findById(f.file).orElseThrow();
        assertThat(file.getOcrRawText()).isEqualTo(TEXT);
        assertThat(new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                "SELECT p.user_id FROM files f JOIN expert_profiles p ON p.id=f.expert_profile_id WHERE f.id=?",Long.class,f.file)).isEqualTo(f.session.userId());
        String oldApproval=file.getVerificationStatus().name();
        String oldProfile=profiles.findByUserId(f.session.userId()).orElseThrow().getVerificationStatus().name();
        assertThat(file.getFileType().name()).isEqualTo("BUSINESS_REGISTRATION");
        assertThat(infos.findById(f.info).orElseThrow().getConfirmedFileId()).isEqualTo(f.file);
        var status=verifyFlow(f,"STATUS");
        assertThat(status.path("authenticity").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(status.path("business_state").asText()).isEqualTo("ACTIVE");
        var matched=verifyFlow(f,"VALIDATE");
        assertThat(matched.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(matched.path("validity").asText()).isEqualTo("CURRENT");
        assertThat(latest(f).path("execution_id").asText()).isEqualTo(matched.path("execution_id").asText());
        assertThat(rows(f)).isEqualTo(2);assertThat(providerCalls.get()).isEqualTo(2);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(file.getFilePath()))));
        assertThat(matched.path("file_sha256").asText()).isEqualTo(digest);
        var download=http.exchange(base()+"/files/"+f.file+"/download",HttpMethod.GET,new HttpEntity<>(headers(f.session)),byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(download.getBody()))).isEqualTo(digest);
        assertThat(files.findById(f.file).orElseThrow().getVerificationStatus().name()).isEqualTo(oldApproval);
        assertThat(profiles.findByUserId(f.session.userId()).orElseThrow().getVerificationStatus().name()).isEqualTo(oldProfile);
        flyway.validate();assertThat(flyway.info().applied()).hasSize(16);
    }

    @ParameterizedTest @ValueSource(strings={"01","02","03"})
    void stateAndMatchAreSeparateThroughRealHttp(String code) throws Exception {
        var f=flow();
        doAnswer(call->{providerCalls.incrementAndGet();
            return new NtsHttpTransport.Response(200,((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")?validation(true,code):status(code));}).when(transport).post(any(),anyString(),any());
        String expected=Map.of("01","ACTIVE","02","SUSPENDED","03","CLOSED").get(code);
        assertThat(verifyFlow(f,"STATUS").path("business_state").asText()).isEqualTo(expected);
        var result=verifyFlow(f,"VALIDATE");assertThat(result.path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(result.path("business_state").asText()).isEqualTo(expected);assertThat(rows(f)).isEqualTo(2);
    }

    @Test void mismatchIsNotHttpFailureAndUsesOneStatusFallback() throws Exception {
        var f=flow();
        doAnswer(call->{providerCalls.incrementAndGet();
            return new NtsHttpTransport.Response(200,((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")?validation(false,"01"):status("01"));}).when(transport).post(any(),anyString(),any());
        var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("execution_status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.path("authenticity").asText()).isEqualTo("NOT_CONFIRMED");
        assertThat(result.path("status_lookup_performed").asBoolean()).isTrue();
        assertThat(providerCalls.get()).isEqualTo(2);assertThat(rows(f)).isEqualTo(1);
    }

    @Test void unregisteredOrDeletedIsDistinctFromProviderOutage() throws Exception {
        var f=flow();
        doAnswer(call->{
            providerCalls.incrementAndGet();
            return new NtsHttpTransport.Response(200,((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")
                    ?validation(false,"01")
                    :"{\"status_code\":\"OK\",\"request_cnt\":1,\"match_cnt\":0,\"data\":[{\"b_no\":\""+NUMBER+"\",\"b_stt_cd\":\"\",\"tax_type\":\"국세청에 등록되지 않은 사업자등록번호입니다\"}]}");
        }).when(transport).post(any(),anyString(),any());
        var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("authenticity").asText()).isEqualTo("NOT_CONFIRMED");
        assertThat(result.path("business_state").asText()).isEqualTo("NOT_FOUND_OR_DELETED");
        assertThat(result.path("failure").asText()).isEqualTo("NONE");assertThat(rows(f)).isEqualTo(1);
    }

    @Test void successfulOcrStillRequiresExplicitUserConfirmation() throws Exception {
        var f=unconfirmedFlow();
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"STATUS"),409);
        assertThat(infos.findById(f.info).orElseThrow().getConfirmedInputRevision()).isNull();
        assertThat(providerCalls.get()).isZero();assertThat(rows(f)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"TIMEOUT","NETWORK","HTTP500","HTTP403","INVALID_JSON","WRONG_NUMBER","BUSINESS_ERROR"})
    void providerFailureIsPersistedAndNotMistakenForMatch(String type) throws Exception {
        var f=flow();
        doAnswer(call->{
            if(type.equals("TIMEOUT"))throw new HttpTimeoutException("synthetic timeout");
            if(type.equals("NETWORK"))throw new IOException("synthetic network failure");
            if(type.equals("HTTP500"))return new NtsHttpTransport.Response(500,"{}");
            if(type.equals("HTTP403"))return new NtsHttpTransport.Response(403,"{}");
            if(type.equals("INVALID_JSON"))return new NtsHttpTransport.Response(200,"not-json");
            if(type.equals("WRONG_NUMBER"))return new NtsHttpTransport.Response(200,validation(true,"01").replace(NUMBER,"9876543215"));
            return new NtsHttpTransport.Response(200,"{\"status_code\":\"ERROR\"}");
        }).when(transport).post(any(),anyString(),any());
        var result=verifyFlow(f,"VALIDATE");
        assertThat(result.path("execution_status").asText()).isEqualTo("FAILED");
        assertThat(result.path("authenticity").asText()).isEqualTo("UNAVAILABLE");
        String expected=Map.of("TIMEOUT","TIMEOUT","NETWORK","NETWORK","HTTP500","PROVIDER_ERROR","HTTP403","AUTHENTICATION",
                "INVALID_JSON","INVALID_RESPONSE","WRONG_NUMBER","INVALID_RESPONSE","BUSINESS_ERROR","PROVIDER_ERROR").get(type);
        assertThat(result.path("failure").asText()).isEqualTo(expected);
        assertThat(history.findFirstByBusinessRegistrationInfoIdOrderByIdDesc(f.info).orElseThrow().getFailure().name()).isEqualTo(expected);
        assertThat(latest(f).path("failure").asText()).isEqualTo(expected);assertThat(rows(f)).isEqualTo(1);
    }

    @Test void staleInputAndOcrRequestsAreRejectedAndOldHistoryRemains() throws Exception {
        var f=flow();verifyFlow(f,"VALIDATE");
        var old=request(f,"VALIDATE");confirm(f,"Corrected Synthetic Company");
        json(f.session,HttpMethod.POST,verificationPath(f),old,409);
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
        verifyFlow(f,"VALIDATE");assertThat(rows(f)).isEqualTo(2);
        var beforeRerun=request(f,"VALIDATE");long oldOcr=f.ocr;ocr(f);
        json(f.session,HttpMethod.POST,verificationPath(f),beforeRerun,409);
        var oldConfirmation=new HashMap<>(business(f.file,"Synthetic Company"));oldConfirmation.put("ocr_revision",oldOcr);
        json(f.session,HttpMethod.PUT,"/expert/me/business-registration-info/"+f.info+"/confirmation",oldConfirmation,409);
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"VALIDATE"),409);
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
        assertThat(rows(f)).isEqualTo(2);confirm(f,"Synthetic Company");
        assertThat(verifyFlow(f,"VALIDATE").path("validity").asText()).isEqualTo("CURRENT");assertThat(rows(f)).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings={"INPUT","OCR","FILE"})
    void concurrentHttpRequestsDoNotMixRevisionsOrOverwriteLatest(String change) throws Exception {
        var f=flow();var old=request(f,"VALIDATE");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if(count.incrementAndGet()==1){entered.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Controlled request timed out");}
            return new NtsHttpTransport.Response(200,validation(true,"01"));
        }).when(transport).post(any(),anyString(),any());
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->json(f.session,HttpMethod.POST,verificationPath(f),old,200).path("data"));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(latest(f).path("execution_status").asText()).isEqualTo("RUNNING");
            json(f.session,HttpMethod.POST,verificationPath(f),old,409);
            if(change.equals("OCR"))ocr(f);
            if(change.equals("FILE")) {
                f.file=upload(f.session,pdf(1),200);ocr(f);
                json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,business(f.file,"Synthetic Company"),200);
            }
            confirm(f,"Changed Synthetic Company");
            var newer=verifyFlow(f,"VALIDATE");release.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS).path("validity").asText()).isEqualTo("STALE");
            assertThat(latest(f).path("execution_id").asText()).isEqualTo(newer.path("execution_id").asText());
            assertThat(latest(f).path("validity").asText()).isEqualTo("CURRENT");assertThat(rows(f)).isEqualTo(2);
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void ownershipAndUnsafePdfAreRejectedBeforeProviderCalls() throws Exception {
        var f=flow();var other=signup();
        json(other,HttpMethod.GET,"/files/"+f.file+"/ocr",null,403);
        json(other,HttpMethod.POST,verificationPath(f),request(f,"STATUS"),403);
        json(other,HttpMethod.GET,verificationPath(f)+"/latest",null,403);
        upload(other,pdf(51),400);
        assertThat(providerCalls.get()).isZero();assertThat(rows(f)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"EMPTY","FAILED","TIMEOUT"})
    void ocrFailureHasNonSuccessHttpAndBlocksVerification(String type) throws Exception {
        var f=flow();
        if(type.equals("EMPTY"))doReturn("   ").when(vision).requestOcr(anyString(),any());
        else doThrow(new CustomException(type.equals("TIMEOUT")?ErrorCode.OCR_TIMEOUT:ErrorCode.OCR_FAILED)).when(vision).requestOcr(anyString(),any());
        json(f.session,HttpMethod.POST,"/files/"+f.file+"/ocr",null,type.equals("EMPTY")?422:type.equals("TIMEOUT")?504:502);
        var state=json(f.session,HttpMethod.GET,"/files/"+f.file+"/ocr",null,200).path("data");
        assertThat(state.path("status").asText()).isEqualTo(type.equals("EMPTY")?"EMPTY":"FAILED");
        assertThat(state.path("text").isNull()).isTrue();
        f.ocr=state.path("revision").asLong();
        json(f.session,HttpMethod.POST,verificationPath(f),request(f,"STATUS"),409);
        assertThat(rows(f)).isZero();assertThat(providerCalls.get()).isZero();
    }

    record ReviewSnapshot(String fileStatus,String reason,String profileStatus,Boolean verified,
                          java.time.LocalDateTime verifiedAt,java.time.LocalDateTime reviewedAt) {}
    ReviewSnapshot review(Flow f) {
        var file=files.findById(f.file).orElseThrow();
        var profile=profiles.findByUserId(f.session.userId()).orElseThrow();
        return new ReviewSnapshot(file.getVerificationStatus().name(),file.getRejectReason(),
                profile.getVerificationStatus().name(),profile.getIsVerified(),profile.getVerifiedAt(),profile.getReviewedAt());
    }
    void seedReview(Flow f,String fileState,String ocrState) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            var file=files.findById(f.file).orElseThrow();
            if(fileState.equals("APPROVED"))file.approveVerification();
            if(fileState.equals("REJECTED"))file.rejectVerification("Synthetic existing review reason");
            // Same entity method as administrator approval, isolated precondition only; no admin API changes.
            profiles.findByUserId(f.session.userId()).orElseThrow().updateVerificationStatus(
                    com.capdi.backend.domain.expert.entity.VerificationStatusEnum.APPROVED,null);
            if(!ocrState.equals("UNCHANGED")&&!ocrState.equals("NOT_RUN")) {
                file.startOcr(UUID.randomUUID().toString(),java.time.LocalDateTime.now());
                if(!ocrState.equals("RUNNING"))file.finishOcr(
                        ocrState.equals("SUCCEEDED_MATCH")?TEXT:ocrState.equals("SUCCEEDED_MISMATCH")?"Unrelated synthetic text":null,
                        com.capdi.backend.domain.expert.entity.OcrStatusEnum.valueOf(ocrState.startsWith("SUCCEEDED")?"SUCCEEDED":ocrState),
                        ocrState.equals("FAILED")?"OCR_FAILED":ocrState.equals("EMPTY")?"OCR_EMPTY":null);
            }
        });
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> reviewAndOcrCases() {
        return Arrays.stream(new String[]{"PENDING","APPROVED","REJECTED"}).flatMap(state->
                Arrays.stream(new String[]{"NOT_RUN","RUNNING","FAILED","EMPTY","SUCCEEDED_MATCH","SUCCEEDED_MISMATCH"})
                        .map(ocr->org.junit.jupiter.params.provider.Arguments.of(state,ocr)));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("reviewAndOcrCases")
    void businessPostPatchPreserveEveryReviewAcrossOcrStates(String fileState,String ocrState) throws Exception {
        var f=new Flow();f.session=signup();f.file=upload(f.session,pdf(1),200);
        seedReview(f,fileState,ocrState);
        var before=review(f);var originalOcr=files.findById(f.file).orElseThrow();
        var body=new HashMap<>(business(f.file,"  Synthetic Company  "));
        body.remove("opening_date");body.put("business_number","123 - 45 - 67891");body.put("representative_name","  Synthetic Owner  ");
        var created=json(f.session,HttpMethod.POST,"/expert/me/business-registration-info",body,200).path("data");
        f.info=created.path("id").asLong();
        assertThat(created.path("business_number").asText()).isEqualTo(NUMBER);
        assertThat(created.path("representative_name").asText()).isEqualTo("Synthetic Owner");
        assertThat(created.path("company_name").asText()).isEqualTo("Synthetic Company");
        assertThat(infos.findById(f.info).orElseThrow().getOpeningDate()).isNull();
        assertThat(review(f)).isEqualTo(before);
        var changed=json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,
                business(f.file,"  Corrected Synthetic Company  "),200).path("data");
        assertThat(changed.path("company_name").asText()).isEqualTo("Corrected Synthetic Company");
        assertThat(changed.path("opening_date").asText()).isEqualTo("2024-02-29");
        var omitted=new HashMap<>(business(f.file,"Corrected Synthetic Company"));omitted.remove("opening_date");
        json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,omitted,200);
        assertThat(infos.findById(f.info).orElseThrow().getOpeningDate()).isEqualTo(java.time.LocalDate.of(2024,2,29));
        assertThat(infos.findById(f.info).orElseThrow().getConfirmedInputRevision()).isNull();
        assertThat(review(f)).isEqualTo(before);
        var afterOcr=files.findById(f.file).orElseThrow();
        assertThat(afterOcr.getOcrStatus()).isEqualTo(originalOcr.getOcrStatus());
        assertThat(afterOcr.getOcrRevision()).isEqualTo(originalOcr.getOcrRevision());
        assertThat(afterOcr.getOcrRawText()).isEqualTo(originalOcr.getOcrRawText());
        assertThat(afterOcr.getOcrRunToken()).isEqualTo(originalOcr.getOcrRunToken());
        assertThat(providerCalls.get()).isZero();assertThat(rows(f)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"business_number=ABC","business_number=---","business_number=1234567890",
            "business_number=12345678912","representative_name=()","representative_name=   ","company_name=()","company_name=123"})
    void invalidPostPatchRollbackInputWithoutChangingReviews(String invalid) throws Exception {
        var f=flow();seedReview(f,"APPROVED","UNCHANGED");
        var before=review(f);var original=infos.findById(f.info).orElseThrow();
        long count=infos.count();
        var parts=invalid.split("=",2);var body=new HashMap<>(business(f.file,"Synthetic Company"));body.put(parts[0],parts[1]);
        json(f.session,HttpMethod.POST,"/expert/me/business-registration-info",body,400);
        json(f.session,HttpMethod.PATCH,"/expert/me/business-registration-info/"+f.info,body,400);
        assertThat(infos.count()).isEqualTo(count);
        var after=infos.findById(f.info).orElseThrow();
        assertThat(after.getBusinessNumber()).isEqualTo(original.getBusinessNumber());
        assertThat(after.getRepresentativeName()).isEqualTo(original.getRepresentativeName());
        assertThat(after.getCompanyName()).isEqualTo(original.getCompanyName());
        assertThat(after.getOpeningDate()).isEqualTo(original.getOpeningDate());
        assertThat(after.getInputRevision()).isEqualTo(original.getInputRevision());
        assertThat(after.getConfirmedInputRevision()).isEqualTo(original.getConfirmedInputRevision());
        assertThat(review(f)).isEqualTo(before);assertThat(providerCalls.get()).isZero();assertThat(rows(f)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"PENDING","APPROVED","REJECTED"})
    void ntsResultsAndStalenessNeverRewriteExistingReviewOrApprovedProfile(String fileState) throws Exception {
        var f=flow();seedReview(f,fileState,"UNCHANGED");var before=review(f);
        assertThat(verifyFlow(f,"VALIDATE").path("authenticity").asText()).isEqualTo("MATCH");
        assertThat(review(f)).isEqualTo(before);
        doAnswer(call->new NtsHttpTransport.Response(200,
                ((java.net.URI)call.getArgument(0)).getPath().endsWith("/validate")?validation(false,"01"):status("01")))
                .when(transport).post(any(),anyString(),any());
        assertThat(verifyFlow(f,"VALIDATE").path("authenticity").asText()).isEqualTo("NOT_CONFIRMED");
        assertThat(review(f)).isEqualTo(before);
        confirm(f,"Changed Synthetic Company");
        assertThat(latest(f).path("validity").asText()).isEqualTo("STALE");
        assertThat(review(f)).isEqualTo(before);
        doThrow(new HttpTimeoutException("Synthetic timeout")).when(transport).post(any(),anyString(),any());
        assertThat(verifyFlow(f,"VALIDATE").path("failure").asText()).isEqualTo("TIMEOUT");
        assertThat(review(f)).isEqualTo(before);assertThat(rows(f)).isEqualTo(3);
    }
}
