package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.service.ExpertProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert-profiles")
public class ExpertProfileController {

    private final ExpertProfileService expertProfileService;
}
