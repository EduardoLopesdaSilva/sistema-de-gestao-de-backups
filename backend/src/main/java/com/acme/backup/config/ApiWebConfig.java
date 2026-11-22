package com.acme.backup.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

@Configuration
public class ApiWebConfig implements WebMvcConfigurer {
    private final String frontendOrigin;
    private final String emailAccessToken;
    private final String emailEncryptionKey;
    private static final Pattern RETIRED_ENDPOINTS = Pattern.compile(
        "^/api/(?:users(?:/.*)?|volumes/[^/]+/(?:uploads|folders|items(?:/.*)?))/?$");

    public ApiWebConfig(@Value("${app.cors-origin}") String frontendOrigin,
            @Value("${app.email-access.access-token:}") String emailAccessToken,
            @Value("${app.email-access.encryption-key:}") String emailEncryptionKey) {
        this.frontendOrigin = frontendOrigin;
        this.emailAccessToken = emailAccessToken;
        this.emailEncryptionKey = emailEncryptionKey;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(frontendOrigin)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Requested-With", "X-Email-Access-Token")
            .allowCredentials(false);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RetiredEndpointInterceptor()).addPathPatterns("/api/**");
        registry.addInterceptor(new EmailAccessInterceptor(emailAccessToken, emailEncryptionKey))
                .addPathPatterns("/api/email-access", "/api/email-access/**");
    }

    private static final class RetiredEndpointInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            if (RETIRED_ENDPOINTS.matcher(request.getRequestURI()).matches()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return false;
            }
            return true;
        }
    }

    private static final class EmailAccessInterceptor implements HandlerInterceptor {
        private final String configuredToken;
        private final String configuredEncryptionKey;
        private EmailAccessInterceptor(String configuredToken, String configuredEncryptionKey) {
            this.configuredToken = configuredToken;
            this.configuredEncryptionKey = configuredEncryptionKey;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            if (configuredToken == null || configuredToken.length() < 32 || !validEncryptionKey(configuredEncryptionKey)) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Configure EMAIL_ACCESS_TOKEN e EMAIL_ACCESS_ENCRYPTION_KEY para habilitar este módulo.");
                return false;
            }
            String supplied = request.getHeader("X-Email-Access-Token");
            boolean matches = supplied != null && MessageDigest.isEqual(
                    configuredToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de acesso inválida.");
                return false;
            }
            return true;
        }

        private static boolean validEncryptionKey(String encoded) {
            try { return Base64.getDecoder().decode(encoded).length == 32; }
            catch (IllegalArgumentException | NullPointerException ex) { return false; }
        }
    }
}
