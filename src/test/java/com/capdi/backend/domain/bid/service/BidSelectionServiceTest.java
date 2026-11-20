package com.capdi.backend.domain.bid.service;

import com.capdi.backend.domain.announcement.entity.*;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.bid.dto.ExpertSelectRequest;
import com.capdi.backend.domain.bid.entity.*;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.bid.repository.BidSelectionHistoryRepository;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BidSelectionServiceTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void memberAndGuestSelectionWaitForPaymentAndCannotSelectAgain(boolean guest) throws Exception {
        var bids=mock(BidRepository.class);
        var announcements=mock(AnnouncementRepository.class);
        var profiles=mock(ExpertProfileRepository.class);
        var history=mock(BidSelectionHistoryRepository.class);
        when(history.lockUserType(1L)).thenReturn(Optional.of("CLIENT"));
        var service=new BidService(bids,announcements,profiles,history);
        var announcement=mock(Announcement.class);
        when(announcement.getAnnouncementCode()).thenReturn("SELECTION");
        when(announcement.getStatus()).thenReturn(AnnouncementStatusEnum.CLOSED);
        when(announcement.getProgressStep()).thenReturn(AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION);
        when(announcement.nextSelectionRound()).thenReturn(1L);
        when(announcement.getSelectionRound()).thenReturn(1L);
        when(announcement.isOwnedBy(1L)).thenReturn(true);
        when(announcement.isGuest()).thenReturn(guest);
        when(announcements.findByAnnouncementCodeWithLock("SELECTION")).thenReturn(Optional.of(announcement));
        var expert=mock(User.class);when(expert.getId()).thenReturn(2L);
        var bid=Bid.create(announcement,expert,new BigDecimal("1200"));
        org.springframework.test.util.ReflectionTestUtils.setField(bid,"id",3L);
        when(bids.findAllByAnnouncementWithLock(announcement)).thenReturn(java.util.List.of(bid));
        var profile=mock(ExpertProfile.class);
        when(profile.getId()).thenReturn(4L);
        when(profile.getVerificationStatus()).thenReturn(VerificationStatusEnum.APPROVED);
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(profiles.incrementSelectedCountIfApproved(eq(4L),eq(VerificationStatusEnum.APPROVED),any())).thenReturn(1);
        var request=new ObjectMapper().readValue("{\"final_amount\":1000}",ExpertSelectRequest.class);

        if(guest) service.selectGuestExpert("SELECTION",3L,request);
        else service.selectExpert(1L,"SELECTION",3L,request);

        assertThat(bid.getStatus()).isEqualTo(BidStatusEnum.SELECTED);
        assertThat(bid.getFinalAmount()).isEqualByComparingTo("1000");
        assertThat(bid.getBidAmount()).isEqualByComparingTo("1200");
        verify(announcement,never()).updateProgressStep(any());
        verify(bids).rejectAllPendingExcept(announcement,3L,BidStatusEnum.REJECTED,BidStatusEnum.PENDING,1L);
        assertThatThrownBy(()->{
            if(guest) service.selectGuestExpert("SELECTION",3L,request);
            else service.selectExpert(1L,"SELECTION",3L,request);
        }).isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BID_ALREADY_PROCESSED));
        verify(profiles).incrementSelectedCountIfApproved(eq(4L),eq(VerificationStatusEnum.APPROVED),any());
    }
}
