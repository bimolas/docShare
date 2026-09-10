package com.subject77.docshare.modules.folder.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMoveRequestDto {
    @NotEmpty(message = "IDs list cannot be empty")
    private List<Long> ids;
    private Long targetFolderId;
}
