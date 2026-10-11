package com.bjarne.datingrecommendationsuserservice.moderation;

import com.bjarne.datingrecommendationsuserservice.entity.Role;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Makes the accounts listed in app.security.admin-emails administrators on startup (the account must exist).
 * Further admins are appointed by an admin via PUT /api/admin/users/{referenceId}/role.
 */
@Component
@Order(50)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final List<String> adminEmails;

    public AdminBootstrap(UserRepository userRepository,
                          @Value("${app.security.admin-emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.adminEmails = adminEmails;
    }

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        for (String email : adminEmails) {
            if (email.isBlank()) {
                continue;
            }
            userRepository.findByEmailIgnoreCase(email.trim()).ifPresentOrElse(user -> {
                if (user.getRole() != Role.ADMIN) {
                    user.setRole(Role.ADMIN);
                    userRepository.save(user);
                    LOGGER.info("Made {} an administrator (app.security.admin-emails)", user.getReferenceId());
                }
            }, () -> LOGGER.warn("Admin account {} from app.security.admin-emails does not exist yet", email.trim()));
        }
    }
}
