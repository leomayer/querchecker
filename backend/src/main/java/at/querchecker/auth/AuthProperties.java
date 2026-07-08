package at.querchecker.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "querchecker.auth")
@Data
public class AuthProperties {

    private int sessionDays = 30;
    private int slidingExtensionHours = 24;
    /** DSGVO-Retention für Key-Nutzungshistorie (access_key_usage), Konzept Kap. 7. */
    private int usageRetentionDays = 90;
}
