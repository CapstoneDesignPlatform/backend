package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.InternalPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InternalPayoutRepository extends JpaRepository<InternalPayout,Long> {
 @org.springframework.data.jpa.repository.Query("select p.id from InternalPayout p where p.dispatched=true and p.state in (com.capdi.backend.domain.payment.entity.InternalPayout.State.REQUESTED,com.capdi.backend.domain.payment.entity.InternalPayout.State.UNKNOWN) and p.lookupCount<8 and (p.leaseUntil is null or p.leaseUntil<=:now) and (p.nextLookupAt is null or p.nextLookupAt<=:now)")
 List<Long> due(@org.springframework.data.repository.query.Param("now") java.time.Instant now,org.springframework.data.domain.Pageable page);
 Optional<InternalPayout> findByReviewId(Long review);
 List<InternalPayout> findByPaymentIdOrderByIdDesc(Long payment);
 List<InternalPayout> findByProfileIdAndStateIn(Long profile,List<InternalPayout.State> states);
}
