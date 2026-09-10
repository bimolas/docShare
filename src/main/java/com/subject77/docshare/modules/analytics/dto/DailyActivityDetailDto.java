package com.subject77.docshare.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivityDetailDto {
    private String date;
    private long totalActions;
    private List<ActivityItemDto> activities;
}
