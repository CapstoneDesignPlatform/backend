-- MySQL DDL은 문장마다 자동 커밋되므로 이 스크립트 전체를 한 트랜잭션으로 묶을 수 없다.
-- 대신 1~3단계에서 실패해도 다시 실행할 수 있게 만들었다. 이 경우 원인을 고친 뒤
-- `flyway repair` 후 다시 migrate 하면 된다(원문 토큰을 두 번 해시하는 일은 없다).
-- 예외: 4단계까지 성공했는데 Flyway 이력 기록만 실패했다면 스키마 변환은 이미 끝난 상태다.
-- 이때는 다시 실행하지 말고(token 컬럼이 없어 3단계에서 실패한다) Flyway 이력만 맞춰야 한다.
-- 배포 중 구버전 인스턴스가 이 테이블에 쓰면 4단계가 실패할 수 있으므로 서버를 내린 상태에서 실행한다.

-- 1) 사용자당 가장 최근 행만 남긴다. (DML, 재실행 안전)
DELETE t1 FROM refresh_tokens t1
  JOIN refresh_tokens t2 ON t1.user_id = t2.user_id AND t1.id < t2.id;

-- 2) 해시 컬럼을 nullable로 추가한다. 이미 있으면 건너뛴다.
SET @has_token_hash := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'refresh_tokens'
     AND COLUMN_NAME = 'token_hash');
SET @ddl := IF(@has_token_hash = 0,
  'ALTER TABLE refresh_tokens ADD COLUMN token_hash VARCHAR(64) NULL AFTER user_id',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 원문 컬럼에서 해시를 채운다. 아직 비어 있는 행만 채우므로 재실행 안전.
UPDATE refresh_tokens SET token_hash = SHA2(token, 256) WHERE token_hash IS NULL;

-- 4) 원문 컬럼 제거와 제약 추가를 ALTER 한 문장으로 처리한다.
--    MySQL 8의 단일 DDL 문은 원자적이라 실패하면 아무것도 바뀌지 않는다.
ALTER TABLE refresh_tokens
  DROP INDEX uk_refresh_tokens_token,
  DROP COLUMN token,
  MODIFY COLUMN token_hash VARCHAR(64) NOT NULL,
  ADD CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
  ADD CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id);
