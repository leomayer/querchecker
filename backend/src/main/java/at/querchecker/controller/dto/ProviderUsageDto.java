package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProviderUsageDto {
    private long calls;
    private double avgDurationMs;
}
