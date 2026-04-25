package com.capdi.backend.domain.client.entity;

import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "client_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ClientInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(nullable = false, length = 20)
    private String contact;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "business_number", nullable = false, length = 30)
    private String businessNumber;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 255)
    private String website;

    @Column(precision = 15, scale = 2)
    private BigDecimal capital;

    @Column(name = "founded_date")
    private LocalDate foundedDate;

    @Column(columnDefinition = "TEXT")
    private String description;
}
