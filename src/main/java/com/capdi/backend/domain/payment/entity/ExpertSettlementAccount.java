package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="expert_settlement_accounts",uniqueConstraints=@UniqueConstraint(name="uk_expert_account_revision",columnNames={"expert_profile_id","account_revision"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ExpertSettlementAccount extends BaseTimeEntity {
    public enum State { UNVERIFIED, VERIFIED, UNKNOWN, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="expert_profile_id",nullable=false,updatable=false) private Long expertProfileId;
    @Column(name="account_revision",nullable=false,updatable=false) private long accountRevision;
    @Column(name="masked_number",nullable=false,length=40,updatable=false) private String maskedNumber;
    @Lob @Column(name="encrypted_details",nullable=false,columnDefinition="LONGTEXT",updatable=false) private String encryptedDetails;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="kyc_verified",nullable=false) private boolean kycVerified;
    @Version @Column(nullable=false) private long version;
    public static ExpertSettlementAccount create(Long profile,long revision,String mask,String ciphertext) {
        var a=new ExpertSettlementAccount();a.expertProfileId=profile;a.accountRevision=revision;a.maskedNumber=mask;
        a.encryptedDetails=ciphertext;a.state=State.UNVERIFIED;return a;
    }
    public void proof(boolean verified,boolean kyc){state=verified?State.VERIFIED:State.UNKNOWN;kycVerified=kyc;}
    public void proof(com.capdi.backend.domain.payment.gateway.FinancialGateway.BankState bankState,boolean kyc){
        state=State.valueOf(bankState.name());kycVerified=kyc;
    }
}
