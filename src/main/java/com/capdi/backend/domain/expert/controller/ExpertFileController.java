package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.service.ExpertFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExpertFileController {

    private final ExpertFileService expertFileService;
}
