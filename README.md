# 🛠️ Capdi Backend

> 기업진단 용역거래 플랫폼 백엔드 서버

<br>

## 📦 패키지 구조

```
com.capdi.backend
├── domain
│   ├── {도메인명}
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
└── global          ← 공통 설정 및 유틸
```

<br>

## 📐 네이밍 컨벤션

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `UserService` |
| 메서드 / 변수 | camelCase | `createUser` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 테이블명 | snake_case 복수형 | `users`, `expert_profiles` |
| 컬럼명 | snake_case | `created_at`, `business_number` |
| URL | kebab-case | `/job-posts`, `/expert-profiles` |
| Enum 값 | UPPER_SNAKE_CASE | `PENDING`, `APPROVED` |

<br>

## 📝 코드 컨벤션

### Entity

- 모든 Entity는 `BaseTimeEntity` 상속 (`createdAt`, `updatedAt` 자동 관리)
- `@Setter` 사용 금지 → 값 변경 시 **도메인 메서드** 추가
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 사용
- `String` 대신 `Enum` 사용 (`@Enumerated(EnumType.STRING)`)
- `@Builder.Default` 로 기본값 지정

```java
// ✅ 올바른 예시
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class User extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
}
```

```java
// ❌ 잘못된 예시
@Entity
@Getter
@Setter                              // 금지
public class User {
    private String status;           // String 대신 Enum 사용
    private LocalDateTime createdAt; // BaseTimeEntity 상속으로 대체
}
```

---

### DTO

- Request DTO → `@Valid` + Bean Validation 어노테이션 사용
- Response DTO → `@Builder` + `from()` 정적 팩토리 메서드 사용
- 클라이언트로부터 받으면 안 되는 값은 Request DTO에서 제외 (예: `status`, `verificationStatus`)
- 비밀번호는 `password` 로 받고, Service에서 암호화 후 저장 (`passwordHash` 직접 수신 금지)

```java
// ✅ Request DTO
@Getter
public class UserCreateRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password; // 평문 수신, Service에서 암호화
}

// ✅ Response DTO
@Getter
@Builder
public class UserResponse {
    public static UserResponse from(User user) { ... }
}
```

---

### Controller

- 모든 API 응답은 `ResponseEntity<ApiResponse<T>>` 형태로 반환
- `@RequestBody` 가 있는 경우 반드시 `@Valid` 함께 사용

```java
// ✅ 올바른 예시
@PostMapping
public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @RequestBody @Valid UserCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok("사용자가 생성되었습니다.", userService.createUser(request)));
}

@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(userService.getUser(id)));
}
```

---

### Service

- 클래스 레벨에 `@Transactional(readOnly = true)` 선언
- 쓰기 작업 메서드에만 `@Transactional` 별도 선언
- `IllegalArgumentException` 대신 `CustomException(ErrorCode.XXX)` 사용

```java
// ✅ 올바른 예시
@Service
@Transactional(readOnly = true)
public class UserService {

    @Transactional
    public UserResponse createUser(UserCreateRequest request) { ... }

    public UserResponse getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
```

---

### 예외 처리

- 모든 비즈니스 예외는 `CustomException` 사용
- 에러 코드는 `ErrorCode` enum에 등록 후 사용
- `GlobalExceptionHandler` 에서 일괄 처리

```java
// ✅ ErrorCode 등록
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

// ✅ 예외 발생
throw new CustomException(ErrorCode.USER_NOT_FOUND);
```

---

### API 응답 형식

```json
// 성공
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": { ... }
}

// 실패
{
  "success": false,
  "message": "존재하지 않는 사용자입니다.",
  "data": null
}
```

<br>

## 🌿 브랜치 전략

```
main     ─── 배포 브랜치
dev      ─── 개발 통합 브랜치
feat/*   ─── 기능 개발     (예: feat/user-auth)
fix/*    ─── 버그 수정     (예: fix/bid-price-validation)
```

<br>

## ✉️ 커밋 메시지

| 타입 | 설명 |
|------|------|
| `feat` | 새로운 기능 개발 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 |
| `chore` | 빌드, 설정, 의존성 등 |
| `docs` | 문서 작성 / 수정 |
| `test` | 테스트 코드 작성 |

```
feat: 사용자 로그인 API 구현
fix: 입찰 금액 유효성 검사 오류 수정
refactor: UserService 트랜잭션 분리
chore: build.gradle 의존성 추가
docs: README 코드 컨벤션 작성
```

<br>

## 🎫 이슈 컨벤션

### 라벨 타입

| 라벨 | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 개발 | 사용자 로그인 API 구현 |
| `fix` | 버그 수정 | 입찰 금액 유효성 검사 오류 |
| `refactor` | 코드 리팩토링 | UserService 트랜잭션 분리 |
| `chore` | 빌드, 설정, 의존성 등 | build.gradle 의존성 추가 |
| `docs` | 문서 작성 / 수정 | README 코드 컨벤션 작성 |
| `test` | 테스트 코드 작성 | UserService 단위 테스트 추가 |

### 이슈 제목
```
[feat] 사용자 로그인 API 구현
[fix] 입찰 금액 유효성 검사 오류 수정
[refactor] UserService 트랜잭션 분리
```

### 이슈 본문 템플릿
```markdown
## 📋 작업 개요

이 이슈에서 무엇을 구현/수정하는지 간략히 설명

## ✅ 작업 내용

* 세부 작업 1
* 세부 작업 2
* 세부 작업 3

## 🔗 관련 이슈

* closes #이슈번호 (있는 경우)

## 📎 참고 사항

API 명세, ERD, 디자인 링크 등 참고할 내용 (없으면 생략)
```

<br>

## 🔀 PR 규칙

- PR 제목은 **이슈 제목과 동일**하게 작성
- PR 본문에 `closes #이슈번호` 명시 → 자동 이슈 닫기 활성화
- PR은 반드시 **`dev` 브랜치**로 머지

### 브랜치 ↔ 이슈 연결

브랜치명에 이슈 번호를 포함해 추적을 용이하게 한다.

```
feat/{이슈번호}-{작업명}
fix/{이슈번호}-{작업명}

예시)
feat/23-user-login
fix/47-bid-price-validation
```
