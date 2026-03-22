package at.querchecker.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PreferenceResponse {
    private Long categoryId;
    private String categoryName;
    private List<String> fieldKeys;
}
