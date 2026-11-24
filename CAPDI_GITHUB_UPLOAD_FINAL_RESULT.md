# CAPDI GitHub 업로드 최종 결과

## 업로드 요약

- 저장소: `https://github.com/CapstoneDesignPlatform/backend.git`
- 브랜치: `feat/portone-v2-test-integration`
- 기능 커밋: `dc80964e131528f8139de75079d806188cb52cd7` (`feat: integrate PortOne financial workflows and frontend docs`)
- 원격 push: 성공. 기존에 없던 feature 브랜치를 생성했으며 force push는 사용하지 않았다.
- 기준 비교: push 직전 원격 `dev`는 기존 HEAD와 동일한 기준 커밋이었다. `dev`/`main`에는 직접 push하지 않았다.
- PR 생성 링크: https://github.com/CapstoneDesignPlatform/backend/pull/new/feat/portone-v2-test-integration

## 변경 내용

- PortOne V2 결제 identity/transport 설정과 읽기 전용 조회 사전 점검 구성
- Webhook inbox retry/scheduler, 정산·승인·지급·세금계산서 canonical workflow 연결
- V28–V32 Flyway migration
- 결제, 금융 workflow, schema/migration, 전문가 파일/OCR 호환 테스트
- 프론트 API 명세 및 연동 가이드

### 기능 커밋 파일 목록

- `CAPDI_FRONTEND_API_SPEC.md`
- `CAPDI_FRONTEND_PAYMENT_INTEGRATION_GUIDE.md`
- `src/main/java/com/capdi/backend/domain/payment/config/FinancialWorkflowConfig.java`
- `src/main/java/com/capdi/backend/domain/payment/config/PaymentProcessingConfig.java`
- `src/main/java/com/capdi/backend/domain/payment/config/PaymentWebhookConsumerConfig.java`
- `src/main/java/com/capdi/backend/domain/payment/config/PortOneGetPaymentsProbeRunner.java`
- `src/main/java/com/capdi/backend/domain/payment/config/PortOneSecretFiles.java`
- `src/main/java/com/capdi/backend/domain/payment/controller/FinancialCompletionController.java`
- `src/main/java/com/capdi/backend/domain/payment/controller/FinancialExecutionController.java`
- `src/main/java/com/capdi/backend/domain/payment/dto/FinancialWorkflowResponse.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/FinancialApproval.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/FinancialOperation.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/FinancialTaxInvoice.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/InternalTaxDocument.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/InternalTaxExecution.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/Payment.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/PaymentAttempt.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/PaymentWebhookEvent.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/PayoutCompatibilityLink.java`
- `src/main/java/com/capdi/backend/domain/payment/entity/SettlementPolicyV1.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/FinancialGateway.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/PaymentGateway.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/ProviderAccountIdentity.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneFinancialGateway.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneFinancialTransport.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneProxyConfiguration.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneProxySelector.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneSettings.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneTransport.java`
- `src/main/java/com/capdi/backend/domain/payment/gateway/portone/PortOneV2Gateway.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/FinancialApprovalRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/FinancialOperationRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/FinancialTaxInvoiceRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/InternalTaxExecutionRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/PaymentWebhookEventRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/repository/PayoutCompatibilityLinkRepository.java`
- `src/main/java/com/capdi/backend/domain/payment/service/FinancialReviewService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/FinancialWorkflowPersistenceService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/FinancialWorkflowService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalPayoutPersistenceService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalPayoutService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalSettlementService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalTaxExecutionPersistenceService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalTaxExecutionService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/InternalTaxService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentExecutionPersistenceService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentGatewaySafety.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentWebhookConsumer.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentWebhookPersistenceService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentWebhookRetryPolicy.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PaymentWebhookService.java`
- `src/main/java/com/capdi/backend/domain/payment/service/PayoutCompatibilityStateMapper.java`
- `src/main/java/com/capdi/backend/domain/payment/service/SettlementCompletionGuard.java`
- `src/main/resources/db/migration/V28__add_webhook_retry_backoff.sql`
- `src/main/resources/db/migration/V29__separate_provider_account_identity.sql`
- `src/main/resources/db/migration/V30__link_settlement_reviews_to_financial_approvals.sql`
- `src/main/resources/db/migration/V31__add_payout_compatibility_links.sql`
- `src/main/resources/db/migration/V32__link_tax_documents_to_financial_operations.sql`
- `src/test/java/com/capdi/backend/domain/expert/service/ExpertBusinessEndToEndMySqlTest.java`
- `src/test/java/com/capdi/backend/domain/expert/service/ExpertFileMySqlIntegrationTest.java`
- `src/test/java/com/capdi/backend/domain/expert/service/FullSchemaValidationMySqlTest.java`
- `src/test/java/com/capdi/backend/domain/payment/config/PortOneConfigurationBindingTest.java`
- `src/test/java/com/capdi/backend/domain/payment/config/PortOneSecretFilesTest.java`
- `src/test/java/com/capdi/backend/domain/payment/entity/TaxDocumentCanonicalLinkTest.java`
- `src/test/java/com/capdi/backend/domain/payment/gateway/portone/PortOneFinancialGatewayTest.java`
- `src/test/java/com/capdi/backend/domain/payment/gateway/portone/PortOnePaymentsListUriTest.java`
- `src/test/java/com/capdi/backend/domain/payment/gateway/portone/PortOneProxySelectorTest.java`
- `src/test/java/com/capdi/backend/domain/payment/gateway/portone/PortOneV2GatewayTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/FinancialWorkflowMySqlIntegrationTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PaymentGatewaySafetyTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PaymentOrderMySqlIntegrationTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PaymentProcessingMySqlIntegrationTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PaymentWebhookRetryPolicyTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PaymentWebhookSchedulerContextTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PayoutCompatibilityStateMapperTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/PortOnePaymentMySqlIntegrationTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/ProviderAccountIdentityInvalidLegacyMySqlTest.java`
- `src/test/java/com/capdi/backend/domain/payment/service/ProviderAccountIdentityMigrationMySqlTest.java`

## 검증 결과

- Java toolchain: Microsoft JDK 21.0.10 (Gradle Java 21 toolchain 사용)
- `.\gradlew.bat test --rerun-tasks --console=plain`: `BUILD SUCCESSFUL`, 425 tests, 0 failures, 0 errors, 0 skipped
- `.\gradlew.bat mysqlIntegrationTest --rerun-tasks --console=plain`: `BUILD SUCCESSFUL`, 329 tests, 0 failures, 0 errors, 0 skipped
- 합계: 754 tests 성공
- 통합 결과 XML 최신 시각: 2026-11-24 21:01:56 (KST). 테스트 출력 XML은 모두 parse 가능.
- Flyway: V1–V32 파일 확인. 전체-schema 및 Testcontainers 통합 테스트 통과.
- `git diff --check`, staged diff check 및 staged credential-pattern 검사 통과.
- PortOne 실제 요청, 결제 재실행, 송금, 환불 또는 세금계산서 발행은 수행하지 않았다.

## 업로드에서 제외한 내용

- `infra/` 및 `.runtime`의 로컬 Docker/배포 환경, Secret/credential 설정, 상태/journal, 테스트 데이터 및 산출물
- `.gradle-step3/`, build 산출물 및 테스트 DB 덤프
- 기존 단계별 작업 로그/보고서 중 API 사용자 문서로 필요하지 않은 문서
- 실제 Secret, JWT, 비밀번호, 계정 식별자 및 개인정보

위 제외 파일은 삭제하거나 되돌리지 않았고 Worktree에 보존했다. 기존 CAPDI, Validation D, Sandbox 자원 및 영구 DB는 변경하지 않았다.

## 기능 상태와 남은 연동

- PortOne TEST 카드 결제는 이전 검증 결과가 있으며 이번 업로드 과정에서 거래를 재실행하지 않았다.
- 전문가 수수료 10:90 배분, 관리자 승인, 내부 지급 operation 및 세금 문서 상태 흐름은 코드/DB에서 관리된다.
- 실제 전문가 송금, 외부 PG 환불, 법적 전자세금계산서 발행은 provider 계약·자격·운영 승인 및 실제 adapter 검증 전까지 비활성/미연동이다.
- 가상계좌 입금은 provider 조회와 webhook reconciliation으로 확인하며, 발급만으로 PAID 처리하지 않는다.
- 전문가 월 이용료 및 CMS 자동출금은 이번 업로드 범위에서 제외했다.

## 프론트 문서

- `CAPDI_FRONTEND_API_SPEC.md`: Controller/DTO/Security 기준 API 및 역할/상태/제한 사항
- `CAPDI_FRONTEND_PAYMENT_INTEGRATION_GUIDE.md`: 로그인, Checkout SDK, Confirmation, 가상계좌, 정산/세금 UI 연동 순서

이 보고서는 기능 커밋 이후 생성되며, 동일 feature 브랜치에 별도 문서 커밋으로 추가된다.
