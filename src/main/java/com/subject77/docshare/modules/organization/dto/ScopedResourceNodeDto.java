package com.subject77.docshare.modules.organization.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopedResourceNodeDto {
    private Long id;
    private String name;
    private String type; // "FOLDER" or "DOCUMENT"
    private String relativePath;
    @Builder.Default
    private List<ScopedResourceNodeDto> children = new ArrayList<>();
}
