package at.querchecker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhCategoryDto {
    private Integer whId;
    private String name;
    private int level;
    private List<WhCategoryDto> children;
}
