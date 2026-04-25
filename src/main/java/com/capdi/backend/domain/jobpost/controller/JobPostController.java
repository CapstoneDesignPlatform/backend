package com.capdi.backend.domain.jobpost.controller;

import com.capdi.backend.domain.jobpost.service.JobPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-posts")
public class JobPostController {

    private final JobPostService jobPostService;
}
