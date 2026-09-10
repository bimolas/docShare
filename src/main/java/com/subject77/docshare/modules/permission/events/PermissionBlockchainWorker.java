package com.subject77.docshare.modules.permission.events;

import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.BlockchainAnchoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionBlockchainWorker {

    private final PermissionRepository permissionRepository;
    private final BlockchainAnchoringService blockchainAnchoringService;

    @Async
    @EventListener
    @Transactional
    public void handlePermissionChangeEvent(PermissionChangeEvent event) {
        log.info("Processing asynchronous permission event for permissionId={}", event.getPermissionId());

        Optional<PermissionEntity> optionalPermission = permissionRepository.findById(event.getPermissionId());
        if (optionalPermission.isEmpty()) {
            log.warn("Permission entity with id={} not found during blockchain worker processing", event.getPermissionId());
            return;
        }

        PermissionEntity permission = optionalPermission.get();
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;
        String txHash = null;
        Exception lastException = null;

        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                txHash = blockchainAnchoringService.anchorHashToBlockchain(permission.getId(), permission.getRecordHash());
                success = true;
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed to anchor PermissionID={} to blockchain: {}",
                        attempt, maxRetries, permission.getId(), e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (success) {
            permission.setBlockchainTxHash(txHash);
            permission.setBlockchainStatus(BlockchainStatus.ANCHORED);
            permissionRepository.save(permission);
            log.info("Successfully updated PermissionID={} with blockchain status ANCHORED. TxHash={}", permission.getId(), txHash);
        } else {
            log.error("Failed to anchor PermissionID={} to blockchain after {} attempts", permission.getId(), maxRetries, lastException);
            permission.setBlockchainStatus(BlockchainStatus.FAILED);
            permissionRepository.save(permission);
        }
    }
}
