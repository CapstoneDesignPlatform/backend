-- Canonical tax workflow links. Existing rows remain untouched and nullable links
-- allow historical records to remain read-only compatibility data.
ALTER TABLE financial_tax_invoices
  ADD COLUMN internal_document_id BIGINT NULL,
  ADD CONSTRAINT uk_fin_tax_invoice_internal_document UNIQUE (internal_document_id),
  ADD CONSTRAINT fk_fin_tax_invoice_internal_document
    FOREIGN KEY (internal_document_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT;

ALTER TABLE internal_tax_documents
  ADD COLUMN financial_invoice_id BIGINT NULL,
  ADD CONSTRAINT uk_internal_tax_document_financial_invoice UNIQUE (financial_invoice_id),
  ADD CONSTRAINT fk_internal_tax_document_financial_invoice
    FOREIGN KEY (financial_invoice_id) REFERENCES financial_tax_invoices(id) ON DELETE RESTRICT;

ALTER TABLE internal_tax_executions
  ADD COLUMN financial_operation_id BIGINT NULL,
  ADD CONSTRAINT uk_internal_tax_execution_financial_operation UNIQUE (financial_operation_id),
  ADD CONSTRAINT fk_internal_tax_execution_financial_operation
    FOREIGN KEY (financial_operation_id) REFERENCES financial_operations(id) ON DELETE RESTRICT;
