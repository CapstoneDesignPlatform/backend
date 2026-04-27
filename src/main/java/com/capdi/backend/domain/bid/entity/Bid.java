package com.capdi.backend.domain.bid.entity;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Bid extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bids.announcement_code -> announcement.announcement_code
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_code", referencedColumnName = "announcement_code", nullable = false)
    private Announcement announcement;

    // bids.expert_user_id -> users.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_user_id", nullable = false)
    private User expertUser;

    @Column(name = "bid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal bidAmount;

    @Column(name = "bid_start_date", nullable = false)
    private LocalDate bidStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BidStatusEnum status;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
