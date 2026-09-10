package com.subject77.docshare.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivitySummaryDto {
    private String date; // YYYY-MM-DD
    private long count;
    private int intensity; // 1 to 4 heatmap level rating
}
