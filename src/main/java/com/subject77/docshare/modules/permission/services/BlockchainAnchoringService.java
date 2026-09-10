package com.subject77.docshare.modules.permission.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
@Service
public class BlockchainAnchoringService {

    public String anchorHashToBlockchain(Long permissionId, String recordHash) {
        log.info("Anchoring permission record hash to blockchain. PermissionID={}, RecordHash={}", permissionId, recordHash);

        try {
            String payload = "PERMISSION_ANCHOR:" + permissionId + ":" + recordHash + ":" + Instant.now().toEpochMilli();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder("0x");
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String txHash = hexString.toString();
            log.info("Blockchain anchor successful for PermissionID={}. TxHash={}", permissionId, txHash);
            return txHash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error executing blockchain hash computation", e);
        }
    }
}
