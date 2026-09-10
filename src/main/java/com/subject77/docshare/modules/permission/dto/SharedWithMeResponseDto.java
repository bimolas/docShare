package com.subject77.docshare.modules.permission.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedWithMeResponseDto {
    private List<SharedItemDto> sharedFolders;
    private List<SharedItemDto> sharedDocuments;
}
