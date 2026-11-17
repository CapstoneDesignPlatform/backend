package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinancialSnapshotGuard {
 private final ExpertSettlementProfileRepository profiles;private final BusinessRegistrationInfoRepository business;
 private final ExpertFileRepository files;private final SettlementProfileReviewRepository reviews;private final EntityManager em;
 public boolean profileCurrent(ExpertSettlementProfile p,boolean lock){
  if(lock){var owner=em.find(ExpertProfile.class,p.getExpertProfileId());if(owner==null || em.createNativeQuery("SELECT id FROM users WHERE id=? AND user_type='EXPERT' FOR UPDATE").setParameter(1,owner.getUser().getId()).getResultList().isEmpty())return false;}
  var latest=lock?em.createNativeQuery("SELECT id FROM expert_settlement_profiles WHERE expert_profile_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE").setParameter(1,p.getExpertProfileId()).getResultList():java.util.List.of(profiles.findFirstByExpertProfileIdOrderByRevisionDesc(p.getExpertProfileId()).map(ExpertSettlementProfile::getId).orElse(-1L));
  if(latest.isEmpty() || ((Number)latest.getFirst()).longValue()!=p.getId())return false;
  var info=(lock?business.findForUpdate(p.getBusinessInfoId()):business.findById(p.getBusinessInfoId())).orElse(null);if(info==null)return false;if(lock)em.refresh(info);
  var file=(lock?files.findForOcr(p.getFileId()):files.findById(p.getFileId())).orElse(null);if(file==null)return false;if(lock)em.refresh(file);
  return info.getExpertProfile().getId().equals(p.getExpertProfileId()) && info.getInputRevision()==p.getInputRevision()
   && info.getFile().getId().equals(file.getId()) && file.getOcrRevision()==p.getOcrRevision()
   && file.getFileType()==FileTypeEnum.BUSINESS_REGISTRATION && file.getOcrStatus()==OcrStatusEnum.SUCCEEDED
   && file.getVerificationStatus()!=FileVerificationStatusEnum.REJECTED && info.isConfirmedFor(file.getId(),file.getOcrRevision());
 }
 public Long latestReviewId(ExpertSettlementProfile p,boolean lock){
  if(lock){var rows=em.createNativeQuery("SELECT id FROM settlement_profile_reviews WHERE profile_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE").setParameter(1,p.getId()).getResultList();return rows.isEmpty()?null:((Number)rows.getFirst()).longValue();}
  return reviews.findByProfileIdOrderByIdDesc(p.getId()).stream().findFirst().map(SettlementProfileReview::getId).orElse(null);
 }
 public boolean profileApproved(ExpertSettlementProfile p,boolean lock){
  if(!profileCurrent(p,lock))return false;
  if(lock){
   var rows=em.createNativeQuery("SELECT * FROM settlement_profile_reviews WHERE profile_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE",SettlementProfileReview.class).setParameter(1,p.getId()).getResultList();
   if(rows.isEmpty())return false;var r=(SettlementProfileReview)rows.getFirst();em.refresh(r);
   return r.getProfileRevision()==p.getRevision() && r.getDecision()==SettlementProfileReview.Decision.APPROVED && r.getDocuments()==SettlementProfileReview.Documents.ACCEPTED;
  }
  return reviews.findByProfileIdOrderByIdDesc(p.getId()).stream().findFirst().map(r->r.getProfileRevision()==p.getRevision() && r.getDecision()==SettlementProfileReview.Decision.APPROVED && r.getDocuments()==SettlementProfileReview.Documents.ACCEPTED).orElse(false);
 }
}
