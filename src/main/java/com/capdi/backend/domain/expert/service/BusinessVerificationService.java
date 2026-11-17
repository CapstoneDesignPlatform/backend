package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.NtsBusinessClient;
import com.capdi.backend.domain.expert.dto.*;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.capdi.backend.domain.expert.entity.BusinessVerification.Kind;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class BusinessVerificationService {
    private final BusinessVerificationPersistenceService persistence;
    private final VerificationFileHasher hasher;
    private final NtsBusinessClient client;

    public BusinessVerificationResponse execute(Long user, Long id, BusinessVerificationRequest request) {
        var observed = persistence.prepare(user, id, request);
        var attempt = persistence.start(user, id, request, observed, hasher.hash(observed.path()));
        NtsBusinessResult result;
        boolean lookup = attempt.kind() == Kind.STATUS;
        try {
            result = lookup ? client.lookupStatus(observed.number())
                    : client.validate(observed.number(), observed.opening(), observed.representative(), observed.company());
            if (!lookup && result.authenticity() == Authenticity.NOT_CONFIRMED && result.businessState() == BusinessState.UNKNOWN
                    && result.failure() == Failure.NONE) {
                var status = client.lookupStatus(observed.number());
                result = new NtsBusinessResult(result.authenticity(), status.businessState(), status.failure());
                lookup = true;
            }
        } catch (RuntimeException ignored) {
            result = NtsBusinessResult.unavailable(Failure.INTERNAL_ERROR);
        }
        try {
            return persistence.complete(user, id, attempt, hasher.hash(observed.path()), result, lookup);
        } catch (RuntimeException ignored) {
            try { persistence.storageFailed(user, id, attempt); } catch (RuntimeException recoveryFailed) {
                // The persisted RUNNING lease can be reconciled by a subsequent POST or GET.
            }
            throw new CustomException(ErrorCode.NTS_RESULT_STORAGE_FAILED);
        }
    }

    public BusinessVerificationResponse latest(Long user, Long id) {
        var observed = persistence.observe(user, id);
        return persistence.latest(user, id, observed, hasher.hash(observed.path()));
    }
}
