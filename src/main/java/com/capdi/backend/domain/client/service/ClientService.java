package com.capdi.backend.domain.client.service;

import com.capdi.backend.domain.client.dto.ClientInfoRequest;
import com.capdi.backend.domain.client.dto.ClientInfoResponse;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    @Transactional
    public ClientInfoResponse saveGuestClientInfo(ClientInfoRequest request) {
        ClientInfo clientInfo = ClientInfo.builder()
                .user(null)
                .representativeName(request.getRepresentativeName())
                .contact(request.getContact())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .businessNumber(request.getBusinessNumber())
                .address(request.getAddress())
                .website(request.getWebsite())
                .description(request.getDescription())
                .build();

        return ClientInfoResponse.from(clientRepository.save(clientInfo));
    }

    @Transactional
    public ClientInfoResponse saveClientInfo(Long userId, ClientInfoRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (clientRepository.existsByUser_Id(userId)) {
            throw new CustomException(ErrorCode.CLIENT_INFO_ALREADY_EXISTS);
        }

        ClientInfo clientInfo = ClientInfo.builder()
                .user(user)
                .representativeName(request.getRepresentativeName())
                .contact(request.getContact())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .businessNumber(request.getBusinessNumber())
                .address(request.getAddress())
                .website(request.getWebsite())
                .description(request.getDescription())
                .build();

        return ClientInfoResponse.from(clientRepository.save(clientInfo));
    }

    @Transactional
    public ClientInfoResponse updateClientInfo(Long userId, ClientInfoRequest request) {
        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        clientInfo.update(
                request.getCompanyName(),
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getContact(),
                request.getEmail(),
                request.getAddress(),
                request.getWebsite(),
                request.getDescription()
        );

        return ClientInfoResponse.from(clientInfo);
    }

    public ClientInfoResponse getClientInfo(Long userId) {
        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        return ClientInfoResponse.from(clientInfo);
    }
}
