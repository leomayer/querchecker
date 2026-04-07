package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ModelUsageDto {
    private String model;
    private long calls;
    private long tokensIn;
    private long tokensOut;
}
