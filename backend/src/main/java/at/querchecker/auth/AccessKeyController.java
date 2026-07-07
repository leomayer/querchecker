package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccessKeyController {

    private final AccessKeyService service;

    public record GenerateKeyRequest(Role role, int quotaLimit) {}

    public record UpdateKeyRequest(Role role, Integer quotaLimit) {}

    @PostMapping("/generate-key")
    @PreAuthorize("hasRole('SUPERUSER')")
    public AccessKeyCreatedDto generateKey(@RequestBody GenerateKeyRequest request) {
        return service.generateKey(request.role(), request.quotaLimit());
    }

    @GetMapping("/keys")
    @PreAuthorize("hasRole('SUPERUSER')")
    public List<AccessKeyOverviewDto> listKeys() {
        return service.listKeys();
    }

    @PatchMapping("/keys/{id}")
    @PreAuthorize("hasRole('SUPERUSER')")
    public AccessKeyOverviewDto updateKey(@PathVariable Long id, @RequestBody UpdateKeyRequest request) {
        return service.updateKey(id, request.role(), request.quotaLimit());
    }

    @PostMapping("/keys/{id}/revoke")
    @PreAuthorize("hasRole('SUPERUSER')")
    public AccessKeyOverviewDto revoke(@PathVariable Long id) {
        return service.revoke(id);
    }

    @PostMapping("/keys/{id}/unrevoke")
    @PreAuthorize("hasRole('SUPERUSER')")
    public AccessKeyOverviewDto unrevoke(@PathVariable Long id) {
        return service.unrevoke(id);
    }
}
