package com.society.complaint.config;

import com.society.complaint.entity.User;
import com.society.complaint.enums.Role;
import com.society.complaint.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin.email:admin@society.com}")
    private String defaultAdminEmail;

    @Value("${app.default-admin.password:Admin@123}")
    private String defaultAdminPassword;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public void run(String... args) {
        checkMailConfig();
        seedDefaultAdmin();
    }

    private void checkMailConfig() {
        log.info("=================================================================");
        if (!mailEnabled) {
            log.info(" MAIL IS DISABLED (app.mail.enabled=false)");
            log.info(" Emails will be logged to the console instead of sent.");
        } else if (mailUsername.isBlank() || mailUsername.equals("your-email@gmail.com")
                || mailPassword.isBlank() || mailPassword.equals("YOUR_16_CHAR_APP_PASSWORD")) {
            log.warn(" MAIL IS ENABLED BUT NOT CONFIGURED!");
            log.warn(" spring.mail.username / spring.mail.password in application.properties");
            log.warn(" still contain placeholder values. Every email send WILL FAIL.");
            log.warn(" Either set real Gmail credentials + an App Password, or set");
            log.warn(" app.mail.enabled=false to log emails to console instead.");
        } else {
            log.info(" Mail is configured. Sending as: {}", mailUsername);
            log.info(" If emails still don't arrive, check spam folder and verify");
            log.info(" the App Password (not your normal Gmail password) is correct.");
        }
        log.info("=================================================================");
    }

    private void seedDefaultAdmin() {
        boolean adminExists = !userRepository.findByRole(Role.ADMIN).isEmpty();

        if (adminExists) {
            log.info("Admin account already exists - skipping default admin seeding.");
            return;
        }

        User admin = User.builder()
                .fullName("Society Administrator")
                .email(defaultAdminEmail.toLowerCase())
                .password(passwordEncoder.encode(defaultAdminPassword))
                .phoneNumber("9999999999")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        userRepository.save(admin);

        log.info("=================================================================");
        log.info(" Default ADMIN account created");
        log.info(" Email:    {}", defaultAdminEmail);
        log.info(" Password: {}", defaultAdminPassword);
        log.info(" Please log in and create additional admins from the Admin panel,");
        log.info(" or change this password, before using this in any real deployment.");
        log.info("=================================================================");
    }
}