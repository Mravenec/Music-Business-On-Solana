package com.eh8s.eh8s.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite UI (ports 5173 and 4173) to call this API from the browser.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /**
   * Registers CORS mappings for local Vite origins.
   *
   * @param registry Spring CORS registry
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4173",
            "http://127.0.0.1:4173")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type");
  }
}
