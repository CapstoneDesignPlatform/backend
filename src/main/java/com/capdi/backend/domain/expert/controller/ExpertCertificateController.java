package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.service.ExpertCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExpertCertificateController {

    private final ExpertCertificateService expertCertificateService;
}
