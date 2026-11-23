DELETE t1 FROM refresh_tokens t1
  JOIN refresh_tokens t2 ON t1.user_id = t2.user_id AND t1.id < t2.id;
ALTER TABLE refresh_tokens DROP INDEX uk_refresh_tokens_token;
UPDATE refresh_tokens SET token = SHA2(token, 256);
ALTER TABLE refresh_tokens CHANGE COLUMN token token_hash VARCHAR(64) NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash);
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id);
