package com.acme.backup.config;

import com.acme.backup.web.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.util.regex.Pattern;

@Configuration
public class ApiWebConfig implements WebMvcConfigurer {
    private final String frontendOrigin;
    private static final Pattern RETIRED_ENDPOINTS = Pattern.compile(
            "^/api/(?:users(?:/.*)?|volumes/[^/]+/(?:uploads|folders|items(?:/.*)?))/?$");

    public ApiWebConfig(@Value("${app.cors-origin}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Requested-With")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthenticationInterceptor(frontendOrigin)).addPathPatterns("/api/**");
        registry.addInterceptor(new RetiredEndpointInterceptor()).addPathPatterns("/api/**");
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

    private static final class SessionAuthenticationInterceptor implements HandlerInterceptor {
        private final String frontendOrigin;
        private SessionAuthenticationInterceptor(String frontendOrigin) { this.frontendOrigin = frontendOrigin; }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.equals("/api/login") || path.equals("/api/logout")) return checkOrigin(request, response);
            if (!checkOrigin(request, response)) return false;
            var session = request.getSession(false);
            if (session == null || session.getAttribute(AuthController.SESSION_USER) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticação necessária.");
                return false;
            }
            return true;
        }

        private boolean checkOrigin(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) return true;
            String origin = request.getHeader("Origin");
            if (origin == null || !origin.equals(frontendOrigin)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem da requisição inválida.");
                return false;
            }
            return true;
        }
    }
}
