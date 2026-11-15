package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="expert_seller_drafts",uniqueConstraints=@UniqueConstraint(name="uk_seller_draft_expert",columnNames="expert_profile_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ExpertSellerDraft extends BaseTimeEntity {
    public enum State { CONTRACT_HOLD }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="expert_profile_id",nullable=false,updatable=false) private Long expertProfileId;
    @Column(name="business_info_id",nullable=false) private Long businessInfoId;
    @Column(name="input_revision",nullable=false) private long inputRevision;
    @Column(name="file_id",nullable=false) private Long fileId;
    @Column(name="ocr_revision",nullable=false) private long ocrRevision;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Version @Column(nullable=false) private long version;
    public static ExpertSellerDraft create(Long profile,Long info,long input,Long file,long ocr) {
        var d=new ExpertSellerDraft();d.expertProfileId=profile;d.replace(info,input,file,ocr);return d;
    }
    public void replace(Long info,long input,Long file,long ocr) {
        if(info==null || file==null || input<0 || ocr<1)throw new IllegalArgumentException("Invalid seller snapshot");
        businessInfoId=info;inputRevision=input;fileId=file;ocrRevision=ocr;state=State.CONTRACT_HOLD;
    }
}
