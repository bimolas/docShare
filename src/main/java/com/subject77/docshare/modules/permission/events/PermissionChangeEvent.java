package com.subject77.docshare.modules.permission.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionChangeEvent implements Serializable {

    private Long permissionId;
    private String recordHash;
    private String eventType; // GRANT, REVOKE, UPDATE
    private Instant timestamp;
}
