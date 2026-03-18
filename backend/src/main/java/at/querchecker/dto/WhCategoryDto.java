package at.querchecker.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhCategoryDto {
    private Integer whId;
    private String name;
    private int level;
    @ArraySchema(schema = @Schema(ref = "#/components/schemas/WhCategoryDto"))
    private List<WhCategoryDto> children;
}
