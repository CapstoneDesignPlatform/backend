# Issue #50 전문가 인증 파일 업로드 안정성 개선

## 변경 배경

업로드 파일이 컨테이너 내부에만 저장되어 재생성 시 유실될 수 있고, 파일 저장 후 DB 저장이나 트랜잭션이 실패하면 물리 파일이 남을 수 있었다.

## 구현 내용

- Compose에 uploads_data를 선언하고 /app/uploads/files에 연결하도록 구성했다. 기존 MySQL 볼륨과 Google Vision 키 설정은 유지한다.
- FILE_STORAGE_UPLOAD_DIR로 저장 루트를 주입한다. 미지정 시 기존 user.dir/uploads/files를 사용하며, 기존 DB의 절대경로는 재작성하지 않는다.
- ExpertFileStorage로 요청 전용 파일 생성·스트림 쓰기·정리를 분리했다. 쓰기 IOException은 부분 파일을 정리하고 FILE_STORAGE_FAILED로 처리한다.
- afterCompletion에서 확정 롤백일 때만 새 파일을 삭제한다. COMMITTED는 유지하고 UNKNOWN은 파일을 보존해 DB 대조 대상으로 기록한다.
- 기존 파일 교체의 afterCommit 삭제를 유지한다. 정리 오류는 원래 실패를 덮어쓰지 않도록 기록한다.
- Dockerfile이 실행 boot JAR 하나만 복사하도록 수정하고 .dockerignore allowlist로 소스·키·업로드·plain JAR 등을 빌드 컨텍스트에서 제외한다.
- 기존 contextLoads는 테스트 전용 H2로 분리하고, 실제 MySQL/Flyway/JPA 검증을 mysqlIntegrationTest 작업으로 추가했다.

## 검증 결과

```powershell
.\gradlew.bat build mysqlIntegrationTest --rerun-tasks
```

일반 테스트 23개와 격리 MySQL 통합 테스트 8개, 총 31개가 통과했다. MySQL 통합 테스트는 Testcontainers의 tmpfs DB를 사용하며 개발 DB/기존 볼륨을 사용하지 않는다.

- 정상 commit, 반환 후 rollback, 실제 INSERT 제약 오류, JPA flush 실패 및 기존 자격증 교체 rollback 검증.
- 기존 경로 호환성, API 응답, JWT multipart 업로드 및 정상/OpenAction PDF 회귀 검증.
- 격리 MySQL에서 Flyway V1~V12 적용 및 validate 성공.
- 별도 Docker 이미지 빌드 성공. 앱을 실행하지 않고 단일 app.jar와 호스트 boot JAR의 SHA-256 일치 확인.

일반 build만으로는 Docker 통합 테스트를 실행하지 않는다. Docker가 준비된 환경에서 mysqlIntegrationTest를 명시해야 한다.

## 남은 위험 및 배포 조건

- 실제 backend 재생성 후 파일 유지 검증은 아직 수행하지 않았다. 이 PR은 설정·코드 검증이며 운영 전환 완료를 의미하지 않는다.
- 전환 전 쓰기 요청을 통제하고 원본·백업·준비 볼륨을 최종 동기화해야 한다. 빈 볼륨을 기존 경로에 바로 연결하지 않는다.
- 배포 전 조사에서 기존 파일 메타데이터 일부의 물리 파일 결손과 복원 후보가 확인됐다. 복원/결손 수용은 별도 승인 대상으로 유지한다.
- 기존 운영 DB의 미적용 V12는 이 PR에서 수정·추가한 마이그레이션이 아니다. 새 앱 시작 시 적용될 수 있어 DB 백업·격리 복원 검증과 적용 승인이 필요하다.
- 프로세스 강제 종료, commit 통신 단절, 정리 실패 자동 재시도는 보장 범위 밖이다. UNKNOWN은 자동 삭제하지 않는다.
- 사용 중 Flyway의 MySQL 8.4 호환성 경고는 후속 검토가 필요하다.

기존 API·PDF 검사·OCR·ClamAV·결제·DB 스키마는 변경하지 않았다. 실제 배포와 기존 데이터 복원은 별도 작업으로 진행한다.

## 관련 이슈

Related to #50
