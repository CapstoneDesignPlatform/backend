package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.dto.InternalSettlementRequests;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.domain.expert.repository.*;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import java.math.BigDecimal;
/** Internal review never authorizes a provider write or verifies a bank account. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InternalSettlementService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;
 private final SettlementBalanceRepository balances;private final SettlementReviewRepository reviews;
 private final ExpertSettlementProfileRepository profiles;private final BusinessRegistrationInfoRepository business;
 private final InternalPayoutRepository payouts;private final FinancialSnapshotGuard snapshotGuard;
 private final FinancialWorkflowPersistenceService finance;private final EntityManager em;
 public record ProfileView(Long id,long revision,ExpertSettlementProfile.BusinessType business_type,
  Long business_info_id,boolean snapshot_current,boolean bank_verified,boolean kyc_verified){}
 public record ReviewView(Long id,Long payment_id,SettlementReview.State state,BigDecimal amount,
  long balance_version,long profile_revision,long version,boolean snapshot_current,
  String execution_state,boolean payout_executable,String reason_code,Long reviewer_id){}
 public record BalanceView(Long payment_id,BigDecimal gross,BigDecimal refunded,BigDecimal platform_amount,
  BigDecimal expert_amount,BigDecimal reserved,BigDecimal paid,BigDecimal recoverable,BigDecimal estimated_available,
  long version,String calculation_state,boolean payout_executable){}
 @Transactional public ProfileView saveProfile(Long user,InternalSettlementRequests.Profile body){
  role(user,"EXPERT",true);Long expert=expert(user);
  var old=profiles.findFirstByExpertProfileIdOrderByRevisionDesc(expert);
  if(old.isPresent() && !payouts.findByProfileIdAndStateIn(old.get().getId(),List.of(InternalPayout.State.REQUESTED,InternalPayout.State.UNKNOWN)).isEmpty())throw conflict();
  if(old.map(ExpertSettlementProfile::getRevision).orElse(0L)!=body.expectedRevision())throw stale();
  var info=business.findForUpdate(body.businessInfoId()).orElseThrow(this::missing);em.refresh(info);
  if(!info.getExpertProfile().getId().equals(expert))throw forbidden();
  var file=info.getFile();em.refresh(file);
  if(file.getOcrStatus()!=OcrStatusEnum.SUCCEEDED || file.getFileType()!=FileTypeEnum.BUSINESS_REGISTRATION || !info.isConfirmedFor(file.getId(),file.getOcrRevision()))throw stale();
  var saved=profiles.saveAndFlush(ExpertSettlementProfile.create(expert,body.expectedRevision()+1,body.businessType(),body.virtualToken(),
   info.getId(),info.getInputRevision(),file.getOcrRevision(),file.getId()));
  return profileView(saved);
 }
 public List<ProfileView> profileHistory(Long user){role(user,"EXPERT",false);return profiles.findByExpertProfileIdOrderByRevisionDesc(expert(user)).stream().map(this::profileView).toList();}
 @Transactional public ReviewView request(Long admin,Long payment,String rawKey,InternalSettlementRequests.Request body){
  role(admin,"ADMIN",true);var p=lock(payment);finance.settlement(admin,payment);
  var b=balances.findByPaymentId(payment).orElseThrow(this::missing);em.refresh(b);
  var profile=latestLockedProfile(p.getExpertProfileId());
  String key=PaymentProcessingKeys.requestKey(rawKey);
  var existing=reviews.findByPaymentIdAndRequestKey(payment,key);
  if(existing.isPresent()){
   var r=existing.get();if(r.getBalanceVersion()!=body.balanceVersion() || r.getProfileRevision()!=body.profileRevision())throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
   return view(r,b,profile);
  }
  if(b.getVersion()!=body.balanceVersion() || profile.getRevision()!=body.profileRevision() || !currentLocked(profile) || !snapshotGuard.profileApproved(profile,true) || !b.integralAllocation() || b.available().signum()<=0 || b.isHeld())throw stale();
  if(reviews.findByPaymentIdOrderByIdDesc(payment).stream().anyMatch(r->r.getState()==SettlementReview.State.PENDING_APPROVAL && r.current(b.getVersion(),profile.getRevision()) && Objects.equals(r.getProfileReviewId(),snapshotGuard.latestReviewId(profile,true))))throw conflict();
  var created=SettlementReview.request(payment,key,b.getVersion(),profile.getRevision(),b.available(),admin);created.captureProfileReview(snapshotGuard.latestReviewId(profile,true));return view(reviews.saveAndFlush(created),b,profile);
 }
 @Transactional public ReviewView decide(Long admin,Long payment,Long review,InternalSettlementRequests.Decision body){
  role(admin,"ADMIN",true);var p=lock(payment);var b=balances.findByPaymentId(payment).orElseThrow(this::missing);em.refresh(b);
  var profile=latestLockedProfile(p.getExpertProfileId());
  var r=reviews.findById(review).orElseThrow(this::missing);em.refresh(r);
  if(!r.getPaymentId().equals(payment))throw missing();
  if(r.getState()!=SettlementReview.State.PENDING_APPROVAL){
   if(r.getReviewerId().equals(admin) && r.getState()==body.decision() && Objects.equals(r.getReasonCode(),body.reasonCode()) && r.getVersion()==body.expectedVersion()+1)return view(r,b,profile);
   throw conflict();
  }
  if(r.getVersion()!=body.expectedVersion() || !r.current(b.getVersion(),profile.getRevision()) || !currentLocked(profile) || !snapshotGuard.profileApproved(profile,true) || !Objects.equals(r.getProfileReviewId(),snapshotGuard.latestReviewId(profile,true)))throw stale();
  if(body.decision()==SettlementReview.State.PENDING_APPROVAL)throw conflict();
  r.decide(admin,body.decision(),body.reasonCode());reviews.flush();
  return view(r,b,profile);
 }
 public List<ReviewView> history(Long admin,Long payment){
  role(admin,"ADMIN",false);var p=payments.findById(payment).orElseThrow(this::missing);
  var b=balances.findByPaymentId(payment).orElseThrow(this::missing);
  var profile=profiles.findFirstByExpertProfileIdOrderByRevisionDesc(p.getExpertProfileId()).orElse(null);
  return reviews.findByPaymentIdOrderByIdDesc(payment).stream().map(r->view(r,b,profile)).toList();
 }
 public List<BalanceView> list(Long user,boolean admin,int page,int size){
  role(user,admin?"ADMIN":"EXPERT",false);if(page<0 || size<1 || size>100)throw new CustomException(ErrorCode.INVALID_INPUT);
  return (admin?balances.findAll(PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id"))):balances.findForExpert(expert(user),PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id")))).stream().map(this::balanceView).toList();
 }
 public BalanceView read(Long user,Long payment,boolean admin){
  role(user,admin?"ADMIN":"EXPERT",false);var p=payments.findById(payment).orElseThrow(this::missing);
  if(!admin && !p.getExpertProfileId().equals(expert(user)))throw forbidden();
  return balanceView(balances.findByPaymentId(payment).orElseThrow(this::missing));
 }
 private ReviewView view(SettlementReview r,SettlementBalance b,ExpertSettlementProfile profile){
  boolean latest=reviews.findByPaymentIdOrderByIdDesc(r.getPaymentId()).stream().findFirst().map(v->v.getId().equals(r.getId())).orElse(false);
  boolean current=latest && profile!=null && r.current(b.getVersion(),profile.getRevision()) && current(profile) && snapshotGuard.profileApproved(profile,false) && Objects.equals(r.getProfileReviewId(),snapshotGuard.latestReviewId(profile,false));
  return new ReviewView(r.getId(),r.getPaymentId(),r.getState(),r.getAmount(),r.getBalanceVersion(),r.getProfileRevision(),r.getVersion(),current,"POLICY_HOLD",false,r.getReasonCode(),r.getReviewerId());
 }
 private BalanceView balanceView(SettlementBalance b){return new BalanceView(b.getPaymentId(),b.getGross(),b.getRefunded(),b.platform(),b.expert(),b.getReserved(),b.getPaid(),b.getRecoverable(),b.available(),b.getVersion(),b.integralAllocation()?"ESTIMATED":"POLICY_HOLD",false);}
 private ProfileView profileView(ExpertSettlementProfile p){return new ProfileView(p.getId(),p.getRevision(),p.getBusinessType(),p.getBusinessInfoId(),current(p),false,false);}
 private boolean current(ExpertSettlementProfile p){
  var latest=profiles.findFirstByExpertProfileIdOrderByRevisionDesc(p.getExpertProfileId());
  if(latest.isEmpty() || latest.get().getRevision()!=p.getRevision())return false;
  var info=business.findById(p.getBusinessInfoId()).orElse(null);if(info==null)return false;
  var file=info.getFile();
  return info.getExpertProfile().getId().equals(p.getExpertProfileId()) && info.getInputRevision()==p.getInputRevision()
   && file.getId().equals(p.getFileId()) && file.getOcrRevision()==p.getOcrRevision() && file.getOcrStatus()==OcrStatusEnum.SUCCEEDED
   && info.isConfirmedFor(file.getId(),file.getOcrRevision());
 }

 private ExpertSettlementProfile latestLockedProfile(Long expert){
  var owner=em.find(ExpertProfile.class,expert);if(owner==null)throw missing();role(owner.getUser().getId(),"EXPERT",true);
  var result=em.createNativeQuery("SELECT * FROM expert_settlement_profiles WHERE expert_profile_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",ExpertSettlementProfile.class).setParameter(1,expert).getResultList();
  if(result.isEmpty())throw missing();var profile=(ExpertSettlementProfile)result.getFirst();em.refresh(profile);return profile;
 }
 private boolean currentLocked(ExpertSettlementProfile p){
  var info=business.findForUpdate(p.getBusinessInfoId()).orElseThrow(this::missing);em.refresh(info);
  var file=em.find(ExpertFile.class,p.getFileId(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(file==null)return false;em.refresh(file);
  return info.getExpertProfile().getId().equals(p.getExpertProfileId()) && info.getInputRevision()==p.getInputRevision()
   && info.getFile().getId().equals(p.getFileId()) && file.getOcrRevision()==p.getOcrRevision() && file.getOcrStatus()==OcrStatusEnum.SUCCEEDED
   && info.isConfirmedFor(file.getId(),file.getOcrRevision());
 }
 private Payment lock(Long id){var p=payments.lockById(id).orElseThrow(this::missing);em.refresh(p);return p;}
 private Long expert(Long user){return sources.expertProfileId(user).orElseThrow(this::forbidden);}
 private void role(Long user,String role,boolean lock){if(user==null || !role.equals(sources.userType(user,lock).orElse("")))throw forbidden();}
 private CustomException stale(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_STALE);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
 private CustomException missing(){return new CustomException(ErrorCode.PAYMENT_NOT_FOUND);}
 private CustomException forbidden(){return new CustomException(ErrorCode.FORBIDDEN);}
}
