package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="internal_tax_items") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InternalTaxItem extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="document_id",nullable=false,updatable=false) private Long documentId;
 @Column(name="line_number",nullable=false,updatable=false) private int lineNumber;
 @Column(name="item_code",nullable=false,length=50,updatable=false) private String itemCode;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal supply;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal vat;
 public static InternalTaxItem line(Long document,int number,String code,BigDecimal supply,BigDecimal vat){
  var i=new InternalTaxItem();i.documentId=document;i.lineNumber=number;i.itemCode=code;i.supply=supply;i.vat=vat;return i;
 }
}
