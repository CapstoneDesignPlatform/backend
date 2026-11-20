package com.capdi.backend.domain.bid.service;

import com.capdi.backend.domain.bid.dto.*;
import com.capdi.backend.domain.bid.repository.BidSelectionHistoryRepository;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation=Isolation.READ_COMMITTED)
public class SelectionCancellationService {
    private final PaymentSourceRepository sources;
    private final BidSelectionHistoryRepository history;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final EntityManager em;

    public SelectionCancelResponse cancel(Long userId, String code, Long bidId, SelectionCancelRequest request) {
        if(userId==null)throw new CustomException(ErrorCode.UNAUTHORIZED);
        // Serialize against order creation/preparation/completion before locking the announcement.
        String role=sources.userType(userId,true).orElseThrow(()->new CustomException(ErrorCode.UNAUTHORIZED));
        if(!"CLIENT".equals(role))throw new CustomException(ErrorCode.FORBIDDEN);
        return cancelLocked(userId,code,bidId,request,false);
    }

    public SelectionCancelResponse cancelGuest(String code, Long bidId, SelectionCancelRequest request) {
        return cancelLocked(null,code,bidId,request,true);
    }

    private SelectionCancelResponse cancelLocked(Long actor, String code, Long bidId,
                                                 SelectionCancelRequest request, boolean guest) {
        if(request==null || request.getSelectionRound()==null || request.getSelectionRound()<1
                || request.getReason()==null || request.getReason().isBlank() || request.getReason().length()>500)
            throw new CustomException(ErrorCode.INVALID_INPUT);
        var announcement=sources.lockAnnouncement(code)
                .orElseThrow(()->new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        if(guest ? !history.isGuest(code) : !actor.equals(announcement.ownerId()))
            throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        long round=request.getSelectionRound();
        if(announcement.selectionRound()!=round)throw new CustomException(ErrorCode.SELECTION_CHANGED);
        var bids=history.lockBids(code);
        var target=bids.stream().filter(b->b.id().equals(bidId)).findFirst()
                .orElseThrow(()->new CustomException(ErrorCode.BID_NOT_FOUND));
        var selection=history.lockSelection(announcement.id(),round)
                .orElseThrow(()->new CustomException(ErrorCode.SELECTION_CHANGED));
        if(!selection.bidId().equals(bidId))throw new CustomException(ErrorCode.SELECTION_CHANGED);
        // A retry of this exact cancelled round cannot restore/decrement anything a second time.
        if(selection.cancelledAt()!=null)return response(code,bidId,round);
        if(!"CLOSED".equals(announcement.status()) || !"STEP_3_EXPERT_SELECTION".equals(announcement.progressStep()))
            throw new CustomException(ErrorCode.SELECTION_NOT_CANCELABLE);
        if(!"SELECTED".equals(target.status()) || bids.stream().filter(b->"SELECTED".equals(b.status())).count()!=1)
            throw new CustomException(ErrorCode.SELECTION_CHANGED);

        var orders=payments.findByAnnouncementAndRoundForUpdate(announcement.id(),round);
        for(Payment order:orders) {
            em.refresh(order,LockModeType.PESSIMISTIC_WRITE);
            if(!order.getBidId().equals(bidId) || order.getStatus()!=PaymentStatus.CREATED
                    || order.getActiveAttemptId()!=null || attempts.existsByPaymentId(order.getId()))
                throw new CustomException(ErrorCode.SELECTION_PAYMENT_STARTED);
        }
        orders.forEach(Payment::cancelUnattemptedOrder);
        if(history.restoreSelectedBid(bidId)!=1)throw new CustomException(ErrorCode.SELECTION_CHANGED);
        history.restoreRejectedBids(code,round);
        if(history.decrementSelectedCount(selection.expertProfileId())!=1
                || history.recordCancellation(selection.id(),actor,request.getReason().trim())!=1)
            throw new IllegalStateException("Selection cancellation history update failed");
        payments.flush();
        return response(code,bidId,round);
    }

    private SelectionCancelResponse response(String code, Long bidId, long round) {
        return new SelectionCancelResponse(code,bidId,round,"PENDING",3);
    }
}
