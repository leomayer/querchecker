package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhLocationDto {
    private Integer areaId;
    private String name;
    private int level;
    private List<WhLocationDto> children;
}
