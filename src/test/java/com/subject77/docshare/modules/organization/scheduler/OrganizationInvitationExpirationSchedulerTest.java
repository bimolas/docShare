package com.subject77.docshare.modules.organization.scheduler;

import com.subject77.docshare.modules.organization.services.OrganizationInvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationInvitationExpirationSchedulerTest {

    @Mock
    private OrganizationInvitationService invitationService;

    @InjectMocks
    private OrganizationInvitationExpirationScheduler scheduler;

    @Test
    @DisplayName("Should execute scheduled task and process expired invitations when count > 0")
    void expireElapsedOrganizationInvitations_Success() {
        when(invitationService.processExpiredInvitations()).thenReturn(5);

        scheduler.expireElapsedOrganizationInvitations();

        verify(invitationService, times(1)).processExpiredInvitations();
    }

    @Test
    @DisplayName("Should execute scheduled task normally when count == 0")
    void expireElapsedOrganizationInvitations_ZeroExpired() {
        when(invitationService.processExpiredInvitations()).thenReturn(0);

        scheduler.expireElapsedOrganizationInvitations();

        verify(invitationService, times(1)).processExpiredInvitations();
    }

    @Test
    @DisplayName("Should catch and handle exceptions gracefully without propagating")
    void expireElapsedOrganizationInvitations_ExceptionHandledGracefully() {
        when(invitationService.processExpiredInvitations())
                .thenThrow(new RuntimeException("Database connectivity failure during expiration check"));

        // Must not throw
        scheduler.expireElapsedOrganizationInvitations();

        verify(invitationService, times(1)).processExpiredInvitations();
    }
}
