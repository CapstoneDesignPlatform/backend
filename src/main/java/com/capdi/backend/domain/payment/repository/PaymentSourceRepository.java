package com.capdi.backend.domain.payment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Scalar locking reads bypass OSIV's managed entity cache. No source domain is modified. */
@Repository
@RequiredArgsConstructor
public class PaymentSourceRepository {
    private final JdbcTemplate jdbc;

    public Optional<String> userType(Long id, boolean lock) {
        return jdbc.query("SELECT user_type FROM users WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> rs.getString(1), id).stream().findFirst();
    }

    public Optional<AnnouncementSnapshot> lockAnnouncement(String code) {
        return jdbc.query("SELECT id, user_id, status, progress_step FROM announcement " +
                        "WHERE announcement_code = ? FOR UPDATE",
                (rs, row) -> new AnnouncementSnapshot(rs.getLong("id"), rs.getObject("user_id", Long.class),
                        rs.getString("status"), rs.getString("progress_step")), code).stream().findFirst();
    }

    public Optional<BidSnapshot> lockBid(Long id, String code) {
        return jdbc.query("SELECT id, expert_user_id, status, final_amount FROM bids " +
                        "WHERE id = ? AND announcement_code = ? FOR UPDATE",
                (rs, row) -> new BidSnapshot(rs.getLong("id"), rs.getLong("expert_user_id"),
                        rs.getString("status"), rs.getBigDecimal("final_amount")), id, code).stream().findFirst();
    }

    public List<Long> lockSelectedBidIds(String code) {
        return jdbc.query("SELECT id FROM bids WHERE announcement_code = ? AND status = 'SELECTED' FOR UPDATE",
                (rs, row) -> rs.getLong(1), code);
    }

    public Optional<Long> expertProfileId(Long expertUserId) {
        return jdbc.query("SELECT id FROM expert_profiles WHERE user_id = ?",
                (rs, row) -> rs.getLong(1), expertUserId).stream().findFirst();
    }

    public record AnnouncementSnapshot(Long id, Long ownerId, String status, String progressStep) { }
    public record BidSnapshot(Long id, Long expertUserId, String status, BigDecimal finalAmount) { }
}
