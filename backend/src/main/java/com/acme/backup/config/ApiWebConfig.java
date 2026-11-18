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
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Requested-With")
            .allowCredentials(false);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
}
