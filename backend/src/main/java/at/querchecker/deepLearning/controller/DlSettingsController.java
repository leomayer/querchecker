package at.querchecker.deepLearning.controller;

import at.querchecker.dto.DlSettingsDto;
import at.querchecker.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dl/settings")
@RequiredArgsConstructor
public class DlSettingsController {

    private final AppConfigService appConfigService;

    @GetMapping
    public DlSettingsDto getSettings() {
        return DlSettingsDto.builder()
            .contextMaxTokens(appConfigService.getDlContextMaxTokens())
            .build();
    }

    @PutMapping
    public DlSettingsDto updateSettings(@RequestBody DlSettingsDto dto) {
        appConfigService.setDlContextMaxTokens(dto.getContextMaxTokens());
        return dto;
    }
}
