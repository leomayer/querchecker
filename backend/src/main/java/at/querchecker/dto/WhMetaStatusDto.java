package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WhMetaStatusDto {
    private LocalDateTime categoriesLastFetched;
    private LocalDateTime locationsLastFetched;
    private boolean refreshInProgress;
    private String refreshCron;
}
