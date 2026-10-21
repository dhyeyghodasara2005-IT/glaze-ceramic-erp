package com.ceramicerp.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.bootstrap.*} - the first admin account, created only when there are no users. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        String adminUsername,
        String adminPassword,
        String adminFullName
) {
}
