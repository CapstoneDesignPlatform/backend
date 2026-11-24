package com.capdi.backend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaxDocumentCanonicalLinkTest {
    @Test
    void documentAndInvoiceLinksAreWriteOnce() {
        var document = InternalTaxDocument.draft(10L, "a".repeat(64), "b".repeat(64), null, 1L, 2L,
                PaymentTaxInvoiceDraft.Kind.PLATFORM_TO_EXPERT, FinancialTaxInvoice.Taxation.TAXABLE, null,
                LocalDate.parse("2026-01-01"), new BigDecimal("90"), new BigDecimal("10"), new BigDecimal("100"));
        document.linkFinancialInvoice(20L);
        assertThatThrownBy(() -> document.linkFinancialInvoice(21L)).isInstanceOf(IllegalStateException.class);

        var invoice = FinancialTaxInvoice.create(30L, 10L, 1L, 2L, null, null, FinancialTaxInvoice.Taxation.TAXABLE,
                LocalDate.parse("2026-01-01"), new BigDecimal("90"), new BigDecimal("10"), "c".repeat(64));
        invoice.linkInternalDocument(40L);
        assertThatThrownBy(() -> invoice.linkInternalDocument(41L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvedDocumentCannotBeReviewedAgain() {
        var document = InternalTaxDocument.draft(10L, "a".repeat(64), "b".repeat(64), null, 1L, 2L,
                PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT, FinancialTaxInvoice.Taxation.TAXABLE, null,
                LocalDate.parse("2026-01-01"), new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("100"));
        document.review(5L, true, "REVIEWED", true);
        assertThat(document.getState()).isEqualTo(InternalTaxDocument.State.APPROVED);
        assertThatThrownBy(() -> document.review(6L, false, "REVIEWED_AGAIN", true)).isInstanceOf(IllegalStateException.class);
    }
}
