package com.ceramicerp.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * On the plant's server, refuses to start with the template's placeholder passwords. Better that it won't start on the
 * day it is installed than that it runs for months with a password anyone can read in the repository.
 */
@Component
@Profile("prod")
public class ProductionChecks {

    static final String PLACEHOLDER = "CHANGE_ME";

    public ProductionChecks(@Value("${spring.datasource.password:}") String databasePassword,
                            @Value("${app.jwt.secret:}") String jwtSecret,
                            @Value("${app.bootstrap.admin-password:}") String adminPassword) {
        check(Map.of("spring.datasource.password", databasePassword,
                "app.jwt.secret", jwtSecret,
                "app.bootstrap.admin-password", adminPassword));
    }

    /** Says only which settings are wrong, never their values. */
    static void check(Map<String, String> settings) {
        List<String> wrong = new ArrayList<>();
        settings.forEach((name, value) -> {
            if (value.isBlank() || PLACEHOLDER.equals(value)) {
                wrong.add(name);
            }
        });
        if (!wrong.isEmpty()) {
            wrong.sort(null);
            throw new IllegalStateException("Set real values in config/application-local.yml before running in production: "
                    + String.join(", ", wrong));
        }
    }
}
