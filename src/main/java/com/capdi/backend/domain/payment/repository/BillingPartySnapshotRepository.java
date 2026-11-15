package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.BillingPartySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BillingPartySnapshotRepository extends JpaRepository<BillingPartySnapshot,Long> {}
