package com.subject77.docshare.modules.organization.scheduler;

import com.subject77.docshare.modules.organization.services.OrganizationInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task component running every 15 minutes to process and transition
 * expired PENDING organization invitations to EXPIRED status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationInvitationExpirationScheduler {

    private final OrganizationInvitationService invitationService;

    /**
     * Executes every 15 minutes to batch-update expired invitations.
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void expireElapsedOrganizationInvitations() {
        log.debug("Starting scheduled check for expired organization invitations...");
        try {
            int count = invitationService.processExpiredInvitations();
            if (count > 0) {
                log.info("Scheduled job expired {} organization invitation(s)", count);
            }
        } catch (Exception e) {
            log.error("Error occurred while processing expired organization invitations: {}", e.getMessage(), e);
        }
    }
}
