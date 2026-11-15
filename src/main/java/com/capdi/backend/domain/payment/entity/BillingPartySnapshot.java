package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="billing_party_snapshots")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class BillingPartySnapshot extends BaseTimeEntity {
    public enum Role { PLATFORM, EXPERT, CLIENT }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="created_by",nullable=false,updatable=false) private Long createdBy;
    @Column(name="subject_user_id",updatable=false) private Long subjectUserId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Role role;
    @Lob @Column(name="encrypted_details",nullable=false,columnDefinition="LONGTEXT",updatable=false) private String encryptedDetails;
    public static BillingPartySnapshot create(Long creator,Long subject,Role role,String ciphertext) {
        var p=new BillingPartySnapshot();p.createdBy=creator;p.subjectUserId=subject;p.role=role;p.encryptedDetails=ciphertext;return p;
    }
}
