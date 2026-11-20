package com.capdi.backend.domain.bid.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BidSelectionHistoryRepository {
    private final JdbcTemplate jdbc;

    public Optional<String> lockUserType(Long userId) {
        return jdbc.query("SELECT user_type FROM users WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),userId)
                .stream().findFirst();
    }

    public void recordSelection(Long announcementId, Long bidId, long round, Long expertProfileId,
                                Long selectedBy, BigDecimal amount) {
        jdbc.update("INSERT INTO bid_selection_history " +
                        "(announcement_id,bid_id,selection_round,expert_profile_id,final_amount,selected_by,selected_at) " +
                        "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
                announcementId,bidId,round,expertProfileId,amount,selectedBy);
    }

    public Optional<Selection> lockSelection(Long announcementId, long round) {
        return jdbc.query("SELECT id,bid_id,expert_profile_id,cancelled_at FROM bid_selection_history " +
                        "WHERE announcement_id=? AND selection_round=? FOR UPDATE",
                (rs,n)->new Selection(rs.getLong("id"),rs.getLong("bid_id"),rs.getLong("expert_profile_id"),
                        rs.getObject("cancelled_at",LocalDateTime.class)),announcementId,round).stream().findFirst();
    }

    public List<BidState> lockBids(String code) {
        return jdbc.query("SELECT id,status FROM bids WHERE announcement_code=? ORDER BY id FOR UPDATE",
                (rs,n)->new BidState(rs.getLong("id"),rs.getString("status")),code);
    }

    public boolean isGuest(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT a.user_id IS NULL AND c.user_id IS NULL " +
                "FROM announcement a JOIN client_info c ON c.id=a.client_info_id WHERE a.announcement_code=?",Boolean.class,code));
    }

    public int restoreSelectedBid(Long bidId) {
        return jdbc.update("UPDATE bids SET status='PENDING',final_amount=NULL,rejected_selection_round=NULL," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='SELECTED'",bidId);
    }

    public void restoreRejectedBids(String code, long round) {
        jdbc.update("UPDATE bids SET status='PENDING',rejected_selection_round=NULL,updated_at=CURRENT_TIMESTAMP(6) " +
                "WHERE announcement_code=? AND status='REJECTED' AND rejected_selection_round=?",code,round);
    }

    public int decrementSelectedCount(Long expertProfileId) {
        return jdbc.update("UPDATE expert_profiles SET selected_count=GREATEST(selected_count-1,0)," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",expertProfileId);
    }

    public int recordCancellation(Long id, Long actor, String reason) {
        return jdbc.update("UPDATE bid_selection_history SET cancelled_by=?,cancel_reason=?,cancelled_at=CURRENT_TIMESTAMP(6) " +
                "WHERE id=? AND cancelled_at IS NULL",actor,reason,id);
    }

    public record Selection(Long id, Long bidId, Long expertProfileId, LocalDateTime cancelledAt) { }
    public record BidState(Long id, String status) { }
}
