\# 진단매치 BackEnd 기능 개선



\## 작업 브랜치

\- feature/jang-guest-client



\## 작업 목적

회원가입 없이 휴대폰 번호 확인만으로 기업진단을 의뢰할 수 있도록

비회원 의뢰인 정보 저장 API와 관련 DB 구조를 개선하였습니다.



\## 주요 변경 내용



\### 1. 비회원 의뢰 API 추가

\- 비회원 전용 요청 DTO `GuestClientInfoRequest` 추가

\- 비회원용 의뢰인 정보 저장 엔드포인트 및 서비스 로직 보완

\- 기존 회원용 의뢰 로직은 최대한 유지



\### 2. 비회원 입력값 선택 처리

비회원 의뢰에서는 필수 정보와 선택 정보를 구분하여 처리할 수 있도록 개선했습니다.



주요 내용:

\- 담당자명(managerName) 저장 지원

\- 연락처(contact) 저장

\- 대표자명 선택 입력 허용

\- 이메일 선택 입력 허용

\- 선택 입력값이 없어도 비회원 의뢰가 저장될 수 있도록 처리



\### 3. Client 관련 수정 파일

\- `ClientController.java`

\- `ClientService.java`

\- `ClientInfo.java`

\- `GuestClientInfoRequest.java`



\### 4. DB Migration 추가

\- `V12\_\_make\_guest\_client\_fields\_optional.sql`



변경 내용:

\- `representative\_name` NULL 허용

\- `email` NULL 허용

\- `manager\_name` 컬럼 추가



\## 로컬 개발 환경

\- Spring Boot

\- Java 21

\- Backend Local Port: 8081

\- MySQL Local Port: 3307



\## 빌드 확인



```bash

./gradlew compileJava

```



\- `BUILD SUCCESSFUL` 확인

