package com.capdi.backend.domain.bid.controller;

import com.capdi.backend.domain.bid.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
}
