package com.capdi.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import com.capdi.backend.global.entity.BaseTimeEntity;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_social_provider_social_id",
                        columnNames = {"social_provider", "social_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserTypeEnum userType;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "password", length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20)
    private SocialProviderEnum socialProvider;

    @Column(name = "social_id", length = 100)
    private String socialId;

    public void updateBasicInfo(String name, String phone) {
        if (name != null) {
            this.name = name;
        }

        if (phone != null) {
            this.phone = phone;
        }
    }
}
