# CAPDI 프론트엔드 API 명세

> 코드 기준: 현재 Worktree의 Spring Controller/DTO/SecurityConfig 및 결제·금융 서비스. 이 문서는 구현된 경로만 기술한다. 서버 context path는 `/api`이며 예시는 `http://localhost:8081/api`를 기준으로 한다. 모든 JSON 예시 값은 합성 예시다.
>
> `ApiResponse` 기본 형식은 `{ "success": true, "message": "...", "data": ... }`; 오류는 `{ "success": false, "message": "...", "data": null }`이다. JWT는 `Authorization: Bearer <accessToken>` 헤더로 보낸다. 인증 정책은 `SecurityConfig` 및 Controller의 `@PreAuthorize` 기준이다.

## 1. 상태 및 사용 가능 범위

| 영역 | API/내부 상태 | 프론트 사용 가능성 |
|---|---|---|
| 전문가 프로필·인증·서류 | 구현 | JWT EXPERT로 사용. 사업자 진위 확인은 설정된 외부 사업자 조회 권한 및 OCR 상태에 따라 실패/보류 가능 |
| 진단 결제 | PortOne V2 SANDBOX 구현 | CLIENT 결제. 카드 TEST PAID 사례 검증됨. 결제창 결과만으로 성공 처리 금지 |
| 가상계좌 | 내부 상태 처리 구현 | 발급은 `WAITING_DEPOSIT`; provider 조회에서 입금을 확인할 때까지 PAID 아님. 실 PG 검증은 제한적 |
| 정산 계좌/프로필·승인 | 내부 snapshot/검토 구현 | 계좌 검증·KYC 완료를 뜻하지 않음. 실제 지급 API는 연동되지 않음 |
| 지급 | `FinancialOperation(PAYOUT)` canonical 구조 | 외부 지급 provider/계약이 없으면 `PENDING_EXTERNAL_PROVIDER`; 실제 송금 완료로 표시 금지 |
| 세금계산서 | 내부 문서 및 operation 구조 | 실제 법적 발행이 아님. 계약·세무정책·provider 승인 전 발행 버튼 비활성/보류 처리 |
| Webhook | 서버 전용, opt-in | 브라우저에서 호출 금지. 수신 후 inbox 저장, 서명검증, provider reconciliation |
| 구독/CMS | 담당 범위 제외 | 이 명세의 기능 계약으로 노출하지 않음. 월 자동갱신/CMS 구현 미확인 |

## 2. 공통 인증, 오류, idempotency

- 로그인: `POST /auth/login`; JSON `email`, `password`. 성공 `data`에는 `accessToken`, `refreshToken`, `role`, `clientInfoId`가 포함된다. 토큰 원문을 로그/분석 도구에 기록하지 않는다.
- 회원 역할은 `CLIENT`, `EXPERT`, `ADMIN`. API별 권한표를 따른다. 관리자 계정은 공개 signup으로 만들 수 없다.
- 대표 HTTP 오류: 400 입력/형식 오류, 401 토큰 없음·만료, 403 역할/소유권 불일치, 404 대상 없음, 409 상태·revision·idempotency 충돌, 503 provider 또는 정책 미활성.
- 결제 주문, Attempt, 정산 review, payout, 세금문서 등 `Idempotency-Key`를 요구하거나 받는 POST는 같은 논리 요청에서 같은 키를 재사용한다. 동일 키에 다른 본문을 보내면 409일 수 있다. SDK 결제 완료/취소 후 새 주문을 임의 생성하지 않는다.
- 프론트는 응답의 `success`와 HTTP status를 함께 처리한다. 서버의 `message`는 표시 문구이지 상태 식별자로 사용하지 않는다.

## 3. 전문가 프로필 및 인증

### `GET /expert/me/profile` — EXPERT

목적: 본인 프로필. Header: Bearer JWT. 응답 `data.user`와 `data.expert_profile`에 사용자/전문가 프로필이 포함된다.

```json
{"success":true,"data":{"user":{"id":12,"name":"홍길동","email":"expert@example.test","phone":"01012345678"},"expert_profile":{"id":7,"user_id":12,"company_name":"예시기업","is_verified":false,"stats":{"active_bids":0,"selected_count":0,"completed_projects":0,"total_earned":0}}}}
```

### `PATCH /expert/me/profile` — EXPERT

Body DTO `ExpertProfileUpdateRequest`: `name`, `phone`, `company_name` (필수 문자열).

```json
{"name":"홍길동","phone":"01012345678","company_name":"예시기업"}
```

응답은 `GET /expert/me/profile`의 동일 DTO 형식.

### `POST /expert/me/verification-requests` — EXPERT

Body `{ "specialty": "세무·재무 진단" }`. 전문가 인증 심사를 신청한다. 응답 `data: null`.

### `GET /expert/me/verification-status` — EXPERT

응답 `data.verification_request`: `id`, `status`, `verification_status` (`NOT_APPLIED` 또는 검토 상태), `specialty`, `company_name`, `certificates`, `business_registration_info`, `submitted_at`, `reviewed_at`, `rejected_reason`.

## 4. 자격증·사업자 서류·OCR

### `POST /expert/me/certificates` — EXPERT

Body: `certificate_name`, `certificate_number`, `issue_date`, `expiry_date`, `owner_name`, `file_id`.

```json
{"certificate_name":"전문자격","certificate_number":"SYNTHETIC-001","issue_date":"2020-01-01","expiry_date":"2030-01-01","owner_name":"홍길동","file_id":31}
```

### `PATCH /expert/me/certificates/{certificateId}` — EXPERT

Body는 생성과 동일한 `ExpertCertificateUpdateRequest`. 응답 `ExpertCertificateResponse`.

### `POST /files?purpose={purpose}` — EXPERT, multipart

`multipart/form-data`, part `file` 및 query `purpose`를 전송한다. 파일 업로드 응답 `data.file`: `id`, `original_name`, `stored_name`, `mime_type`, `size`, `purpose`, `created_at`. 파일 목적 enum/허용 형식/크기는 서버 설정을 따른다.

### `POST /files/{fileId}/ocr` — EXPERT

본인 파일 OCR 실행 요청. 응답 `data: null`. 실행 상태는 아래 조회 API로 확인한다.

### `GET /files/{fileId}/ocr` — EXPERT

응답: `status`, `revision`, `startedAt`, `finishedAt`, `errorCode`; OCR 성공 시에만 `text`가 내려올 수 있다. 민감한 OCR 텍스트를 브라우저 로그에 남기지 않는다.

### `GET /files/{fileId}/ocr-extraction` 또는 `GET /expert/me/business-registration-info/{businessRegistrationInfoId}/ocr-extraction` — EXPERT

사업자 정보 추출 결과 조회. 상세 응답은 `BusinessRegistrationExtractionResponse` DTO 기준.

### `GET /files/{fileId}/download-url` — EXPERT

응답 `data.file`: `id`, `original_name`, `mime_type`, `size`, `download_url`, `expires_at`. URL은 제한된 다운로드 주소이며 영구 공개 링크가 아니다.

### `GET /files/{fileId}/download` — EXPERT

바이너리 응답(`Content-Disposition: attachment`). 공통 JSON wrapper가 아니다.

### `POST /expert/me/business-registration-info` — EXPERT

Body: `file_id`, `business_number`, `representative_name`, `company_name`; `opening_date`는 선택 `YYYY-MM-DD`.

```json
{"file_id":31,"business_number":"0000000000","representative_name":"홍길동","company_name":"예시기업","opening_date":"2020-01-01"}
```

### `PATCH /expert/me/business-registration-info/{businessRegistrationInfoId}` — EXPERT

동일한 사업자 정보 수정 DTO. Response에는 `id`, `file_id`, 사업자 입력값, `input_revision`, `ocr_revision`, `confirmation_current`가 포함된다.

### `PUT /expert/me/business-registration-info/{businessRegistrationInfoId}/confirmation` — EXPERT

Body는 `BusinessConfirmationRequest` DTO. 현재 OCR/file revision에 귀속된 사용자 확인을 기록한다. 실제 국세청 확인 성공과는 별개다.

### `POST /expert/me/business-registration-info/{id}/verifications` — EXPERT

Body: `kind` (기본 `VALIDATE`), `file_id`, `ocr_revision`, `input_revision`. 외부 사업자 상태조회 및 입력 snapshot 정합성을 수행한다. 응답 `execution_id`, `execution_status`, `kind`, `authenticity`, `business_state`, `failure`, `validity`, revision 및 lookup 수행 여부.

### `GET /expert/me/business-registration-info/{id}/verifications/latest` — EXPERT

해당 사업자 정보의 가장 최근 검증 결과.

## 5. 전문가 정산 프로필 및 계좌

### `POST /expert/me/settlement-profiles` — EXPERT

내부 계좌정보 출처를 사업자정보와 묶는 revision snapshot. `virtual_token`은 현재 `virtual_[A-Za-z0-9_-]{8,80}` 형식의 내부 토큰이며 **실제 은행계좌 확인이 아니다**.

```json
{"business_info_id":44,"business_type":"SOLE_PROPRIETOR","virtual_token":"virtual_SYNTHETIC01","expected_revision":0}
```

### `GET /expert/me/settlement-profiles` — EXPERT

본인 profile revision 이력. 응답에 `snapshot_current`, `bank_verified`, `kyc_verified`가 있으나 이는 코드의 검증 신호이며 은행 지급 계약 완료로 오해하지 않는다.

### `POST /expert/me/settlement-accounts` — EXPERT

새 계좌 revision 생성. Body: `bank`, `number`, `holder`, `contactName`, `contactEmail`, `contactPhone` (현재 record 명명 규칙상 camelCase). 실제 계좌번호는 민감정보로 취급하고 응답/로그에 표시하지 않는다. 응답은 account ID/revision, masked number, `kyc_verified` 등 안전한 projection.

```json
{"bank":"BANK_CODE","number":"1234567890","holder":"예시 명의","contactName":"담당자","contactEmail":"finance@example.test","contactPhone":"01012345678"}
```

### `GET /expert/me/settlement-accounts` 및 `GET /expert/me/settlement-accounts/{accountId}` — EXPERT

계좌 revision의 상태/마스킹 조회. 다른 전문가 계좌 ID 접근은 거부된다.

### `POST /expert/me/partner-enrollments` — EXPERT

검증된 사업자 snapshot 및 현재 account/seller revision에 기반한 PG partner 등록 operation 준비. Header `Idempotency-Key`; body `account_id`, `expected_seller_version`. 현재 provider가 없으면 실행 대기/비활성이다.

```json
{"account_id":3,"expected_seller_version":2}
```

### `GET /expert/me/partner-enrollments/{operationId}` — EXPERT

본인 partner operation 상태. 외부 셀러 등록 성공을 의미하지 않는다.

### `PUT /expert/me/seller-draft` / `GET /expert/me/seller-draft` — EXPERT

구형 호환용 seller draft 저장/조회. PUT Body `{ "business_registration_info_id": 44 }`. 이 draft는 실제 PG seller 등록과 다르며 canonical partner enrollment 별도 상태를 확인한다.

## 6. 의뢰인 주문 및 PortOne Checkout

### `POST /announcements/{announcementCode}/payments` — CLIENT

선택된 입찰의 주문 생성. Header `Idempotency-Key` 권장. 본문 가격을 신뢰하지 않고 서버가 bid의 확정 금액을 snapshot한다. Body `{ "bid_id": 9, "method": "CARD" }`; method는 `CARD`, `TRANSFER`, `VIRTUAL_ACCOUNT`.

```json
{"bid_id":9,"method":"CARD"}
```

응답은 Payment DTO (`id`, `order_id`, `amount`, `currency`, `method`, `status` 등). 주문 조건 불일치/미선정/금액 문제는 409. 주문 상태 CREATED에서 Attempt를 생성한다.

### `GET /payments/{paymentId}` — CLIENT 소유자

Payment 상태와 snapshot 조회. 다른 CLIENT의 payment는 접근 불가.

### `POST /payments/{paymentId}/attempts` — CLIENT 소유자

Header `Idempotency-Key` required by service; body `{ "method": "CARD" }`. 반드시 주문 method와 일치해야 한다. 응답 `attempt_id`, `payment_id`, `attempt_key`, `status`, `payment_status`, `method`, `failure`, `created_at`.

### `GET /payments/{paymentId}/attempts/{attemptId}/checkout` — CLIENT 소유자

프론트 결제창 인자만 반환하고 결제를 성공 처리하지 않는다. DTO 필드: `store_id`, `channel_key`, `payment_id`, `order_name`, `total_amount`, `currency`, `pay_method`, `environment`.

```json
{"success":true,"data":{"store_id":"<TEST_STORE_ID>","channel_key":"<TEST_CHANNEL_KEY>","payment_id":"capdi_<attempt-uuid>","order_name":"CAPDI diagnosis service","total_amount":1000.00,"currency":"KRW","pay_method":"CARD","environment":"SANDBOX"}}
```

이 DTO에는 API Secret이 없다. 현재 Provider가 disabled이면 503이며 checkout을 진행하지 않는다.

### Browser SDK (PortOne V2)

현재 API가 주는 `store_id`, `channel_key`, `payment_id`, `order_name`, `total_amount`, `currency`, `pay_method`를 SDK camelCase 옵션에 그대로 대응한다. SDK 반환의 `code`/`message`는 브라우저 결과일 뿐, PAID 증거가 아니다.

```ts
import * as PortOne from "@portone/browser-sdk/v2";
const checkout = response.data;
const result = await PortOne.requestPayment({
  storeId: checkout.store_id,
  channelKey: checkout.channel_key,
  paymentId: checkout.payment_id,
  orderName: checkout.order_name,
  totalAmount: Number(checkout.total_amount),
  currency: checkout.currency, // KRW
  payMethod: checkout.pay_method, // CARD | TRANSFER | VIRTUAL_ACCOUNT
  redirectUrl: `${location.origin}/payments/return`, // mobile REDIRECTION에 필요
});
```

프론트엔드 저장소의 SDK package version은 이 Worktree에 없어 미확정이다. 실제 채널에서 지원되는 payment methods 및 redirect allowlist는 PortOne/Toss 콘솔에서 확인한다.

### `POST /payments/{paymentId}/attempts/{attemptId}/confirmations` — CLIENT 소유자

Body `{ "confirmation_reference": "<SDK-returned-paymentId>" }`. 서버는 browser reference를 Attempt에 묶인 값과 비교한 뒤 PortOne 조회 응답의 Payment ID, 금액, 통화, Store/Channel, TEST 유형, 결제수단, 상태를 검증한다. 서버 검증 전 UI에 성공 표시하지 않는다.

### `POST /payments/{paymentId}/attempts/{attemptId}/reconciliations` — CLIENT 소유자

같은 Attempt에 대한 안전한 provider 조회. UNKNOWN/대기 상태에서 호출하되 반복 polling을 과도하게 하지 않는다. 이미 확정된 결과는 재사용될 수 있다.

### `GET /payments/{paymentId}/attempts/{attemptId}` — CLIENT 소유자

Attempt/Payment 최종 projection 확인.

대표 상태: Attempt `PREPARED`, `REQUESTED`, `WAITING_DEPOSIT`, `SUCCEEDED`, `FAILED`, `CANCELED`, `UNKNOWN`; Payment `CREATED`, `PROCESSING`, `WAITING_DEPOSIT`, `PAID`, `FAILED`, `CANCELED`, `UNKNOWN`.

## 7. 환불 요청 및 관리자 결정

### CLIENT: `POST /payments/{paymentId}/refund-requests` 또는 호환 `POST /payments/{paymentId}/refunds`

Header `Idempotency-Key` 및 body `{ "amount": 100 }`. 두 경로는 같은 `payment_refund_requests` 데이터에 연결된다. 이미 settlement가 있는 주문의 정책 차이는 경로별 조건을 적용하므로 신규 화면은 `/refunds` 경로를 우선 사용하고 legacy 경로를 섞지 않는다. 요청 생성은 환불 실행이 아니다.

### CLIENT 상태: `GET /payments/{paymentId}/refund-requests/{refundId}` 또는 `GET /payments/{paymentId}/refunds/{refundId}`

소유자 범위 상태조회. 응답에 요청 ID, 상태, 금액 및 external execution flag가 포함될 수 있다.

### ADMIN: `POST /admin/refunds/{refundId}/decisions`

Body `{ "decision":"APPROVED", "expected_refund_version":0, "reason_code":"REVIEWED" }`. 승인/hold/reject를 기록한다.

### ADMIN: `POST /admin/refunds/{refundId}/attempts`

Header `Idempotency-Key`, body `{ "approval_id": 21 }`. canonical REFUND operation을 준비한다. 실제 provider disabled이면 실행 불가/정책 보류다.

## 8. 관리자 정산·지급

### settlement balance

- `POST /admin/payments/{paymentId}/settlements`: PAID 및 STEP_7 완료 검증 후 balance 생성/idempotent 조회.
- `GET /admin/payments/{paymentId}/settlement`: gross/refunded/net/platform/expert/reserved/paid/available/recoverable/held/version projection.
- 배분 정책 `SettlementPolicyV1`: 플랫폼 10%, 전문가 90%. 이 비율은 PG fee/VAT 최종 회계정책을 대체하지 않는다.

### payout approval / operation

- `POST /admin/payments/{paymentId}/payout-approvals`: body `decision`, `expected_balance_version`, `account_id`, `expected_account_revision`, `amount`, `reason_code`. 예: `{"decision":"APPROVED","expected_balance_version":1,"account_id":3,"expected_account_revision":2,"amount":900,"reason_code":"SERVICE_COMPLETED"}`.
- `GET /admin/payments/{paymentId}/payout-approvals`: 승인 이력.
- `POST /admin/payments/{paymentId}/payouts`: `Idempotency-Key`, body `{ "approval_id": 8 }`; canonical PAYOUT operation.
- `GET /admin/financial-operations/{operationId}`: operation 상태.
- `POST /admin/financial-operations/{operationId}/execute`: provider submit을 시도하는 관리자 전용 명령. 현재 외부 payout provider 미연동/정책 미승인 환경에서는 실행 완료로 설명하지 말 것.
- `POST /admin/financial-operations/{operationId}/reconciliations`: provider lookup/reconciliation.
- `GET /admin/financial-operations/{operationId}/audits`, `GET /admin/payments/{paymentId}/ledger`: 감사/원장조회.

### legacy settlement review / payout wrapper

- EXPERT: `POST /expert/me/settlement-profiles` (body `business_info_id`, `business_type`, `virtual_token`, `expected_revision`), `GET /expert/me/settlement-profiles`.
- ADMIN: `POST /admin/settlement-profiles/{id}/reviews` (header idempotency; body `profile_revision`, `expected_review_id`, `decision`, `documents`, `reason_code`); 조회 `GET /admin/settlement-profiles/{id}/reviews`; 전문가 본인 조회 `GET /expert/me/settlement-profiles/{id}/reviews`.
- EXPERT balance list/detail: `GET /expert/me/settlements?page=0&size=20`, `GET /expert/me/settlements/{paymentId}`.
- ADMIN list: `GET /admin/settlements?page=0&size=20`.
- ADMIN settlement compatibility review: `POST/GET /admin/payments/{paymentId}/settlement-reviews`, decision `POST /admin/payments/{paymentId}/settlement-reviews/{reviewId}/decisions`.
- Legacy payout compatibility: reserve `POST /admin/payments/{paymentId}/settlement-reviews/{reviewId}/payout-reservations`; inspect admin `GET /admin/payments/{paymentId}/internal-payouts`; expert `GET /expert/me/settlements/{paymentId}/payouts`; operations `POST /admin/internal-payouts/{id}/executions`, `/reconciliations`, `/cancellations`; events `GET /admin/internal-payouts/{id}/events`.

Legacy paths retain IDs/response shapes for compatibility. They bridge to canonical FinancialApproval/FinancialOperation where linked. Do not issue both old and new payout commands for the same approval.

## 9. 세금 문서·발행 operation

두 종류: `EXPERT_TO_CLIENT`, `PLATFORM_TO_EXPERT`. 내부 문서 생성/검토는 실제 법적 발행과 다르다.

### 내부 문서 호환 API — ADMIN

- `POST /admin/payments/{paymentId}/tax-documents` header `Idempotency-Key`, body `kind`, `supplier_snapshot_id`, `recipient_snapshot_id`, optional `original_document_id` + `modification`, `taxation`, `write_date`, `supply`, `vat`, `total`, `items[{item_code,supply,vat}]`.
- `GET /admin/payments/{paymentId}/tax-documents`; `GET /admin/tax-documents/{id}`.
- `POST /admin/tax-documents/{id}/reviews`: `{ "approve": true, "expected_version": 0, "reason_code":"REVIEWED" }`.
- `POST /admin/tax-documents/{id}/revisions`: header idempotency; body `{ "expected_version": 0, "draft": <same Draft DTO> }`.
- `POST /admin/tax-documents/{id}/snapshot-refreshes`: `{ "expected_version": 1 }`.
- `GET /admin/tax-documents/{id}/events`.

### canonical snapshot / operation APIs — ADMIN

- `POST /admin/billing-party-snapshots`: body `role`, optional `subject_user_id`, `brn`, `name`, `representativeName`, `email` (FinancialWorkflowRequests.Party actual Java property names).
- `POST /admin/tax-invoice-drafts/{draftId}/invoices`: body `supplier_snapshot_id`, `recipient_snapshot_id`, `supply`, `vat`, `taxation`, `write_date`, optional `original_invoice_id`, `modification`. Produces immutable FinancialTaxInvoice snapshot.
- `GET /admin/financial-tax-invoices/{invoiceId}`.
- `POST /admin/financial-tax-invoices/{invoiceId}/attempts` header `Idempotency-Key` prepares canonical TAX_INVOICE operation.
- Compatibility execution: `POST /admin/tax-documents/{id}/execution-requests` body `{ "expected_version": 1 }`, then `GET /admin/tax-document-executions/{id}`, `POST /admin/tax-document-executions/{id}/executions`, `/reconciliations`, `GET /admin/tax-document-executions/{id}/events`.

Response fields vary by DTO: internal document response includes `state`, `supply`, `vat`, `total`, `version`, `legally_issued`, `issuance_executable`, `nts_state`, `snapshot_current`, `superseded`, `financial_operation_id`, `canonical_operation_state`. Financial operation response includes `type`, `state`, `provider_environment`, `provider_id`, `failure_code`, `lookup_count`, `next_lookup_at`. Internal `APPROVED` does not mean legally issued. Only an approved provider confirmation may establish issuance. Taxation/VAT and correction policies require business/tax review; API values must not be fabricated.

## 10. Webhook (서버 전용)

`POST /webhooks/portone`, `Content-Type: application/json`; PortOne가 보내는 raw request body와 `webhook-id`, `webhook-timestamp`, `webhook-signature` headers를 변형 없이 전달한다. 서명검증/Store ID/이벤트 ID 검증 후 durable inbox receipt로 ACK한다. Consumer는 `payment.webhook.consumer.enabled=true`에서만 polling한다. 이벤트 본문만으로 Payment를 PAID 처리하지 않고 provider lookup/reconciliation 후 상태를 갱신한다. FE, 브라우저, 고객사가 이 경로를 직접 호출하지 않는다.

## 11. 명세·환경 주의

- Swagger/OpenAPI: `/api/v3/api-docs`, UI `/api/swagger-ui/index.html` (운영 보안정책에 따라 접근 제한할 것). `SwaggerConfig`는 Bearer JWT scheme을 정의한다. 배포 환경의 실제 스펙을 이 문서와 대조한다.
- SANDBOX 결제 Provider 설정과 webhook receiver/consumer는 각각 opt-in이다. LIVE/PRODUCTION 사용을 이 명세가 허가하지 않는다.
- Checkout `environment=SANDBOX`는 FE 표시용 계약값이다. Secret, API credentials는 결코 FE에 전달하지 않는다.
- 외부 payout/refund/tax adapter와 계약이 없으면 operation은 pending/blocked이며 성공 UI를 노출하면 안 된다.
- API가 없는 작업(실제 PG 셀러 승인 완료, 은행계좌 검증, CMS, 월별 renewal)은 FE가 임의 경로를 만들어 호출하지 않는다.
