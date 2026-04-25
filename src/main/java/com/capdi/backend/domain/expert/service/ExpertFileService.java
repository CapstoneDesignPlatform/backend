package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertFileService {

    private final ExpertFileRepository expertFileRepository;
}
