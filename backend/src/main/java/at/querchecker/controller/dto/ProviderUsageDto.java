package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProviderUsageDto {
    private long calls;
    private long callsToday;
    private long tokensIn;
    private long tokensOut;
    private long quotaUsage;
    private long quotaLimit;
}
