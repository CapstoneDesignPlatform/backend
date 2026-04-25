package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.service.NotificationSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationSubscriptionController {

    private final NotificationSubscriptionService notificationSubscriptionService;
}
