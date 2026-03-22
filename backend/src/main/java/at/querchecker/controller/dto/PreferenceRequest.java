package at.querchecker.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class PreferenceRequest {
    private List<String> fieldKeys;
}
