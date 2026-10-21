package com.ceramicerp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Which websites (the React frontend) may call this API from a browser. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
