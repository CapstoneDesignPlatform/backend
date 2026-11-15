CREATE TABLE internal_tax_documents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, scope_key VARCHAR(64) NOT NULL UNIQUE,
 request_hash VARCHAR(64) NOT NULL, original_document_id BIGINT NULL,
 supplier_snapshot_id BIGINT NOT NULL, recipient_snapshot_id BIGINT NOT NULL, kind VARCHAR(30) NOT NULL,
 taxation VARCHAR(20) NOT NULL, modification VARCHAR(50) NULL, write_date DATE NOT NULL,
 supply DECIMAL(15,2) NOT NULL, vat DECIMAL(15,2) NOT NULL, total DECIMAL(15,2) NOT NULL,
 state VARCHAR(20) NOT NULL, reviewer_id BIGINT NULL, reason_code VARCHAR(100) NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 FOREIGN KEY(original_document_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT,
 FOREIGN KEY(supplier_snapshot_id) REFERENCES billing_party_snapshots(id) ON DELETE RESTRICT,
 FOREIGN KEY(recipient_snapshot_id) REFERENCES billing_party_snapshots(id) ON DELETE RESTRICT,
 FOREIGN KEY(reviewer_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(supply+vat=total AND total<>0 AND supply=FLOOR(supply) AND vat=FLOOR(vat)),
 CHECK(supplier_snapshot_id<>recipient_snapshot_id), CHECK(state IN ('POLICY_HOLD','APPROVED','REJECTED'))
);
CREATE TABLE internal_tax_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, line_number INT NOT NULL,
 item_code VARCHAR(50) NOT NULL, supply DECIMAL(15,2) NOT NULL, vat DECIMAL(15,2) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(document_id,line_number), FOREIGN KEY(document_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT,
 CHECK(line_number>0 AND supply=FLOOR(supply) AND vat=FLOOR(vat))
);
