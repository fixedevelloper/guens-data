package com.guens.fieldcollect.config;

import com.guens.fieldcollect.user.Role;
import com.guens.fieldcollect.user.User;
import com.guens.fieldcollect.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le premier administrateur s'il n'existe pas (mot de passe via variables d'environnement). */
@Component
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.bootstrap-admin.username}") String username,
                          @Value("${app.bootstrap-admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByUsername(username)) return;
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setFullName("Administrateur");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.warn("Administrateur initial '{}' créé : changez son mot de passe.", username);
    }
}
