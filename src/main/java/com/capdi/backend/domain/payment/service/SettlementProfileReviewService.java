package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.dto.SettlementProfileReviewRequest;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class SettlementProfileReviewService {
 private final PaymentSourceRepository sources;private final ExpertSettlementProfileRepository profiles;private final SettlementProfileReviewRepository reviews;
 private final FinancialAuditEventRepository events;private final FinancialSnapshotGuard guard;private final EntityManager em;
 public record View(Long id,Long profile_id,long profile_revision,SettlementProfileReview.Decision decision,
  SettlementProfileReview.Documents documents,String reason_code,Long admin_id,boolean snapshot_current,boolean bank_verified,boolean kyc_verified){}
 @Transactional public View review(Long admin,Long profile,String rawKey,SettlementProfileReviewRequest request){
  role(admin,"ADMIN",true);var p=profiles.findById(profile).orElseThrow(this::missing);
  var owner=em.find(ExpertProfile.class,p.getExpertProfileId());role(owner.getUser().getId(),"EXPERT",true);em.refresh(p);
  String key=PaymentProcessingKeys.requestKey(rawKey),hash=PaymentProcessingKeys.digest(request.toString());
  var matching=em.createNativeQuery("SELECT * FROM settlement_profile_reviews WHERE profile_id=? AND request_key=? FOR UPDATE",SettlementProfileReview.class).setParameter(1,profile).setParameter(2,key).getResultList();
  java.util.Optional<SettlementProfileReview> old=matching.isEmpty()?java.util.Optional.empty():java.util.Optional.of((SettlementProfileReview)matching.getFirst());if(old.isPresent()){
   if(!old.get().getRequestHash().equals(hash))throw conflict();return view(old.get(),p);
  }
  var previous=guard.latestReviewId(p,true);
  if(!Objects.equals(previous,request.expectedReviewId()))throw conflict();
  if(request.profileRevision()!=p.getRevision() || !guard.profileCurrent(p,true))throw conflict();
  SettlementProfileReview r;
  try{r=SettlementProfileReview.record(profile,admin,p.getRevision(),key,hash,request.decision(),request.documents(),request.reasonCode());}catch(IllegalArgumentException ex){throw new CustomException(ErrorCode.INVALID_INPUT);}
  r=reviews.saveAndFlush(r);
  events.save(FinancialAuditEvent.record(null,null,profile,admin,"profile-review:"+r.getId(),"DOCUMENT_REVIEW",r.getDecision().name()).evidence(request.reasonCode(),hash));
  return view(r,p);
 }
 public List<View> history(Long user,Long profile,boolean admin){
  role(user,admin?"ADMIN":"EXPERT",false);var p=profiles.findById(profile).orElseThrow(this::missing);
  if(!admin && !p.getExpertProfileId().equals(sources.expertProfileId(user).orElse(null)))throw new CustomException(ErrorCode.FORBIDDEN);
  return reviews.findByProfileIdOrderByIdDesc(profile).stream().map(r->view(r,p)).toList();
 }
 private View view(SettlementProfileReview r,ExpertSettlementProfile p){
  boolean latest=reviews.findByProfileIdOrderByIdDesc(p.getId()).stream().findFirst().map(v->v.getId().equals(r.getId())).orElse(false);
  return new View(r.getId(),p.getId(),r.getProfileRevision(),r.getDecision(),r.getDocuments(),r.getReasonCode(),r.getAdminId(),latest && guard.profileCurrent(p,false),false,false);
 }
 private void role(Long user,String role,boolean lock){if(user==null || !role.equals(sources.userType(user,lock).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_STALE);}
}
