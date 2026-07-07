package at.querchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration ausgeschlossen: erzeugt sonst immer einen In-Memory-User
// mit generiertem Passwort (Log-Rauschen), obwohl SecurityConfig nie httpBasic()/formLogin()
// nutzt und dieser User daher nie erreichbar ist (siehe docs/auth/berechtigungen-konzept.md).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class QuercheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuercheckerApplication.class, args);
    }
}
