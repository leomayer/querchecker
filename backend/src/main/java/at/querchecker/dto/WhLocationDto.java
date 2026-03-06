package at.querchecker.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhLocationDto {
    private Integer areaId;
    private String name;
    private int level;
    @ArraySchema(schema = @Schema(implementation = WhLocationDto.class))
    private List<WhLocationDto> children;
}
