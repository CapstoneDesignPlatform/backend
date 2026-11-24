# CAPDI 프론트엔드 결제·정산 연동 가이드

## 범위

코드 기준 API는 `/api` context path를 사용한다. 이 Worktree에는 프론트엔드 package.json 및 현재 배포 SDK 버전이 없으므로 `@portone/browser-sdk/v2`의 정확한 설치 버전과 callback route는 프론트 저장소에서 확정해야 한다. 이 가이드는 API DTO와 PortOne/TossPayments 공식 V2 연동 안내를 연결한다. 실결제 호출을 이 문서만으로 승인하지 않는다.

공식 참고: [PortOne TossPayments V2 연동 안내](https://developers.portone.io/opi/ko/integration/pg/v2/tosspayments), [PortOne V2 결제 퀵가이드](https://developers.portone.io/opi/ko/quick-guide/payment). 공식 TossPayments 가이드는 일반 결제의 `CARD`, `TRANSFER`, `VIRTUAL_ACCOUNT`, V2 `requestPayment`, 모바일 redirect 사용 시 `redirectUrl` 조건을 안내한다. PG 콘솔에서 실제 채널/수단을 확인해야 한다.

## 1. 로그인 및 역할

1. `POST /api/auth/login`에 `{email,password}` 전송.
2. `data.accessToken`은 메모리 내 인증 상태로 사용하고 `Authorization: Bearer ...` 설정. 토큰/암호를 콘솔, analytics, URL, persistent log에 기록하지 않는다.
3. CLIENT는 의뢰 결제/환불요청, EXPERT는 프로필·정산정보/정산내역, ADMIN은 승인/운영 화면 API만 호출한다.
4. 401은 재로그인/토큰 갱신 흐름, 403은 role·ownership 오류로 처리한다.

```ts
const res = await fetch(`${API}/auth/login`, {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
});
const body = await res.json();
if (!res.ok || !body.success) throw new Error(body.message);
const accessToken = body.data.accessToken; // secret storage/logging 금지
```

## 2. 주문 → Attempt → Checkout → 결제창 → 서버 확인

### 2.1 주문 생성

선택된 bid 및 서버의 확정 금액을 사용한다. 클라이언트가 금액을 지정하지 않는다.

```http
POST /api/announcements/{announcementCode}/payments
Authorization: Bearer <CLIENT access token>
Idempotency-Key: order-{opaque-client-key}
Content-Type: application/json
```

```json
{"bid_id":9,"method":"CARD"}
```

method는 `CARD`, `TRANSFER`, `VIRTUAL_ACCOUNT`. 응답 `data.id`는 CAPDI 숫자 payment ID. Payment `CREATED`를 확인한다.

### 2.2 Attempt 생성

```http
POST /api/payments/{paymentId}/attempts
Authorization: Bearer <CLIENT access token>
Idempotency-Key: attempt-{opaque-client-key}
Content-Type: application/json
```

```json
{"method":"CARD"}
```

응답 `data.attempt_id`, `data.attempt_key`, 상태를 사용한다. 이미 활성 Attempt가 있으면 다른 key로 다시 만들지 말고 조회한다.

### 2.3 Checkout 조회 및 SDK 호출

```http
GET /api/payments/{paymentId}/attempts/{attemptId}/checkout
Authorization: Bearer <CLIENT access token>
```

서버 응답이 주는 값만 SDK에 매핑한다. SDK용 paymentId는 CAPDI attempt에 묶여 있으므로 FE에서 새로 만들지 않는다. API Secret/Webhook Secret은 프론트에 존재하지 않아야 한다.

```ts
import * as PortOne from "@portone/browser-sdk/v2";
const c = checkoutResponse.data;
const sdkResult = await PortOne.requestPayment({
  storeId: c.store_id,
  channelKey: c.channel_key,
  paymentId: c.payment_id,
  orderName: c.order_name,
  totalAmount: Number(c.total_amount),
  currency: c.currency,       // KRW
  payMethod: c.pay_method,    // CARD | TRANSFER | VIRTUAL_ACCOUNT
  redirectUrl: `${location.origin}/payments/portone-return`,
});
```

모바일 TossPayments 리다이렉트 방식은 redirect URL 등록 및 callback 페이지가 필요하다. callback route에서 query 파라미터를 신뢰해 결제 성공으로 바꾸지 않는다. SDK `code`, `message`, `paymentId`는 다음 서버 요청의 참고값일 뿐이다.

### 2.4 Confirmation

브라우저 SDK가 반환한 payment identifier를 `confirmation_reference`로 전달한다. 모바일 redirect 후에도 서버가 보유한 CAPDI `paymentId`와 `attemptId`를 복구한다.

```http
POST /api/payments/{paymentId}/attempts/{attemptId}/confirmations
Authorization: Bearer <CLIENT access token>
Content-Type: application/json
```

```json
{"confirmation_reference":"<SDK result paymentId>"}
```

서버는 provider API 조회를 수행하고 attempt/payment snapshot의 ID, 금액, KRW, Store, TEST Channel, 결제수단, provider state를 확인한다. `attempt.status=SUCCEEDED`와 `payment_status=PAID`가 응답되고 난 뒤에만 사용자 화면을 완료로 바꾼다. `UNKNOWN`, `RESPONSE_MISMATCH`, `REQUESTED`는 성공이 아니며 새 Attempt/결제창 생성 전에 먼저 조회/복구한다.

### 2.5 가상계좌 및 상태 재조회

가상계좌 발급 응답은 `WAITING_DEPOSIT`이다. 계좌 발급을 입금 완료로 표시하면 안 된다. 이후 서버 webhook 및 provider reconciliation이 PAID를 증명할 때까지 대기한다.

```http
POST /api/payments/{paymentId}/attempts/{attemptId}/reconciliations
Authorization: Bearer <CLIENT access token>
```

과도한 반복 호출은 피하고 `GET /api/payments/{paymentId}/attempts/{attemptId}` 또는 `GET /api/payments/{paymentId}`로 현재 projection을 읽는다. 알 수 없는 provider status는 UNKNOWN으로 취급한다.

## 3. Webhook

PortOne이 서버의 `POST /api/webhooks/portone`을 호출한다. FE에서 호출/프록시하지 않는다. 배포에서 receiver (`payment.portone.webhook-enabled`)와 inbox consumer (`payment.webhook.consumer.enabled`)는 별도 설정이며 기본 비활성 정책을 존중한다.

서버는 raw body 서명과 timestamp, Store 및 이벤트 identity를 검증하고 inbox에 저장한 후 ACK한다. 중복 event는 idempotent 처리된다. Consumer는 provider 조회/reconciliation을 수행하므로 웹훅 본문만으로 PAID가 되지 않는다. FE는 서버 Payment 조회 API의 상태만 반영한다.

## 4. 환불 요청 화면

CLIENT가 `POST /api/payments/{paymentId}/refunds`에 `Idempotency-Key` 및 `{ "amount": 100 }`을 보낸다. 이는 환불 요청 생성이지 PG 취소가 아니다. 화면은 반환된 refund ID/state로 요청 접수, 관리자 검토, operation 진행, 최종 결과를 구분한다.

구형 `/refund-requests` 경로가 남아 있으나 신규 화면은 두 경로를 섞지 않는다. 관리자 결정은 내부 관리자 UI 전용 `POST /api/admin/refunds/{refundId}/decisions`, operation 준비는 `/attempts`; provider execute/lookup는 별도 financial operation route다. 현재 외부 환불은 미연동/비활성 상태이므로 “환불 완료” UI를 만들 수 없다.

부분·전체 환불 총액 초과는 서버에서 차단한다. 정산 후 환불은 balance/ledger adjustment 흐름을 따른다. Frontend에서 금액 분배 또는 Payment 상태를 직접 갱신하지 않는다.

## 5. 전문가 계좌 및 정산정보

1. EXPERT가 사업자 서류를 `/files`, OCR 및 사업자정보를 `/expert/me/business-registration-info`에 등록한다.
2. 사업자 입력 확인 및 진위조회 완료 후 `POST /expert/me/settlement-profiles`로 내부 revision을 제출한다. `virtual_token`은 현재 형식 검증용 내부 값이며 은행 계좌 인증을 뜻하지 않는다.
3. 계좌정보 수집 계약이 준비되었을 때 `/expert/me/settlement-accounts`를 사용한다. 실제 계좌번호/연락처를 브라우저 로그에 출력하지 않는다. 응답 마스킹 번호만 표시한다.
4. 관리자 settlement-profile review가 필요할 수 있다. `bank_verified`/`kyc_verified` false면 UI에 “미확인” 상태를 표시한다.
5. 실제 PG 셀러 등록은 `/expert/me/partner-enrollments` operation으로 관리되지만 provider 미연동 시 성공 표시 금지.
6. 전문가 정산내역은 `GET /expert/me/settlements`, 상세 `GET /expert/me/settlements/{paymentId}`, 지급 projection은 `GET /expert/me/settlements/{paymentId}/payouts`.

## 6. 관리자 정산·지급 화면

1. `POST /api/admin/payments/{paymentId}/settlements`로 PAID 및 서비스 완료 조건을 충족한 balance를 준비하고 `GET .../settlement`로 읽는다.
2. API가 제공하는 gross, refunded, net, platform, expert, reserved, paid, available, recoverable, held, version을 그대로 표기한다. V1 가정 정책 배분은 platform 10%, expert 90%. PG 수수료/세금 정산 확정과 혼동하지 않는다.
3. 관리자 승인 시 `POST /api/admin/payments/{paymentId}/payout-approvals`에 현재 `expected_balance_version`, 계좌 ID/revision, 승인 금액, reason code를 보낸다.
4. `GET .../payout-approvals`에서 승인 이력/`execution_state`를 확인한다.
5. `POST /api/admin/payments/{paymentId}/payouts`로 canonical operation을 준비하고 `GET /api/admin/financial-operations/{operationId}`로 상태를 표시한다.
6. 외부 지급 provider가 준비되지 않으면 `PENDING_EXTERNAL_PROVIDER`; execute를 반복하거나 별도 legacy reserve를 동시에 호출하지 않는다. 실행/조회 command는 관리자 전용이므로 프론트 일반 사용자 화면에 노출하지 않는다.
7. 기존 settlement-review/internal-payout 경로는 compatibility API이며 동일 payout을 이중 예약하지 않는다.

## 7. 세금계산서 관리자 화면

1. 세금 문서 종류 `EXPERT_TO_CLIENT`와 `PLATFORM_TO_EXPERT`를 구분한다.
2. 당사자 snapshot 등록(`/admin/billing-party-snapshots`)에서 사업자번호·상호·대표자·이메일 입력은 최소 권한 화면으로 제한한다.
3. 내부 문서 작성(`/admin/payments/{paymentId}/tax-documents`)은 supplier/recipient snapshot, taxation, write date, supply, VAT, total, item lines를 전달한다. 세액 계산정책이 미확정이면 값 추정 금지.
4. 목록/상세 `/admin/payments/{paymentId}/tax-documents`, `/admin/tax-documents/{id}` 및 이벤트를 조회한다.
5. 검토 `/admin/tax-documents/{id}/reviews`는 `expected_version`으로 optimistic concurrency를 적용한다. revision은 `/revisions`; source 변경 후 `/snapshot-refreshes`.
6. `legally_issued=false`, `issuance_executable=false`, provider `NOT_SENT`/blocked이면 내부 draft로 표시한다. 승인된 draft와 법적 전자발행 완료를 구분한다.
7. 실제 외부 발행이 계약/자격증명/세무 정책으로 준비되지 않았다면 operation 실행 버튼을 비활성화한다. UNKNOWN/PENDING은 재전송 대신 reconciliation 조회 흐름으로 안내한다.

## 8. 운영·환경 분리 및 UX 오류 정책

- TEST Store/Channel로 확인된 SANDBOX만 TEST 환경에서 사용한다. Frontend의 환경 라벨은 backend checkout 응답을 반영한다.
- LIVE key를 프론트 빌드 환경변수에 넣지 않는다. API Secret은 서버 secret file 전용이며 Browser SDK가 받지 않는다.
- 실제 운영 결제 전 별도 승인과 PG 콘솔 TEST/LIVE channel 검증이 선행돼야 한다.
- `409 PAYMENT_EXECUTION_CONFLICT/STALE/IDEMPOTENCY_CONFLICT`: 기존 주문/Attempt 조회 후 복구 안내.
- `503 PAYMENT_GATEWAY_DISABLED` 또는 financial policy error: 결제창/송금/발행 재시도 UI 금지, 운영자에게 설정 차단 상태 전달.
- `UNKNOWN`: 결제 성공/실패로 해석 금지. 일정 간격 이후 reconcile/status refresh.
- 카드 결제 완료 뒤 PAID인 기존 건은 재결제 금지.
- Webhook, DB status, Admin execute endpoint를 클라이언트가 직접 호출하는 구조를 추가하지 않는다.

## 9. 프론트팀에 남은 확인

- 실제 프론트 repository/package lock에서 `@portone/browser-sdk` 설치 버전 및 TypeScript types 확정.
- TossPayments TEST channel의 카드/TRANSFER/가상계좌 지원수단 및 모바일 redirect URL을 PortOne console에서 검증.
- 실제 API base URL, CORS origins, callback route를 배포환경에 맞춰 설정.
- 관리자 개인정보 화면 권한, audit 및 masking 확인.
- 실제 PG refund/payout 및 전자세금계산서 provider 계약 전까지 해당 동작을 활성화/성공 UI로 노출하지 않음.

## 10. 서버 구현상 프론트 미지원 범위

- 실제 PG payout/셀러 승인 및 은행계좌 인증
- 실제 PG refund execution
- 법적 전자세금계산서 외부 제출/국세청 전송 완료
- CMS 계좌 자동출금 및 구독 월 자동갱신
- 프론트 SDK 버전과 앱 deep-link callback 계약

이 기능은 API 경로나 응답을 추측해 프론트에서 구현하지 않는다.
