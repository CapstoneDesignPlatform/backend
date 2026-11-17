package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="financial_tax_invoices")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialTaxInvoice extends BaseTimeEntity {
    @Column(name="execution_owner",length=20) private String executionOwner;
    public void claimExecutionOwner(String owner){if(executionOwner!=null && !executionOwner.equals(owner))throw new IllegalStateException("Invoice already owned");executionOwner=owner;}
    @Column(name="provider_environment",length=30) private String providerEnvironment;
    public void bindEnvironment(String environment){if(providerEnvironment!=null && !providerEnvironment.equals(environment))throw new IllegalStateException("Provider environment changed");providerEnvironment=environment;}
    public enum Taxation { TAXABLE, ZERO_RATED, FREE }
    public enum Modification { CORRECTION_OF_ENTRY_ERRORS, CHANGE_IN_SUPPLY_COST, RETURN, CANCELLATION_OF_CONTRACT, DUPLICATE_ISSUANCE_DUE_TO_ERROR }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="draft_id",nullable=false,unique=true,updatable=false) private Long draftId;
    @Column(name="issuance_scope_key",nullable=false,length=64,unique=true,updatable=false) private String issuanceScopeKey;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="supplier_snapshot_id",nullable=false,updatable=false) private Long supplierSnapshotId;
    @Column(name="recipient_snapshot_id",nullable=false,updatable=false) private Long recipientSnapshotId;
    @Column(name="original_invoice_id",updatable=false) private Long originalInvoiceId;
    @Enumerated(EnumType.STRING) @Column(length=50,updatable=false) private Modification modification;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Taxation taxation;
    @Column(name="write_date",nullable=false,updatable=false) private LocalDate writeDate;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal supply;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal vat;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal total;
    @Column(nullable=false) private boolean issued;
    @Column(name="external_id",length=200,unique=true) private String externalId;
    @Enumerated(EnumType.STRING) @Column(name="nts_state",nullable=false,length=20) private FinancialGateway.NtsState ntsState;
    @Version @Column(nullable=false) private long version;
    public static FinancialTaxInvoice create(Long draft,Long payment,Long supplier,Long recipient,Long original,Modification modification,
            Taxation taxation,LocalDate date,BigDecimal supply,BigDecimal vat,String scopeKey) {
        if(scopeKey==null || !scopeKey.matches("[a-f0-9]{64}") || date==null || taxation==null || supply==null || vat==null || supply.stripTrailingZeros().scale()>0
                || vat.stripTrailingZeros().scale()>0 || supply.add(vat).signum()==0
                || supply.abs().compareTo(new BigDecimal("9999999999999"))>0 || vat.abs().compareTo(new BigDecimal("9999999999999"))>0
                || supply.add(vat).abs().compareTo(new BigDecimal("9999999999999"))>0 || (original==null)!=(modification==null)
                || taxation!=Taxation.TAXABLE && vat.signum()!=0)throw new IllegalArgumentException("Invalid tax snapshot");
        var t=new FinancialTaxInvoice();t.issuanceScopeKey=scopeKey;t.draftId=draft;t.paymentId=payment;t.supplierSnapshotId=supplier;t.recipientSnapshotId=recipient;
        t.originalInvoiceId=original;t.modification=modification;t.taxation=taxation;t.writeDate=date;
        t.supply=supply;t.vat=vat;t.total=supply.add(vat);t.ntsState=FinancialGateway.NtsState.NOT_SENT;return t;
    }
    public void observe(FinancialGateway.Evidence e) {
        if(externalId!=null && e.externalId()!=null && !externalId.equals(e.externalId()))throw new IllegalStateException("Invoice identity mismatch");
        if(e.externalId()!=null)externalId=e.externalId();
        if(e.state()==FinancialGateway.State.SUCCEEDED)issued=true;
        if(ntsState!=FinancialGateway.NtsState.SUCCEEDED)ntsState=e.ntsState();
    }
}
