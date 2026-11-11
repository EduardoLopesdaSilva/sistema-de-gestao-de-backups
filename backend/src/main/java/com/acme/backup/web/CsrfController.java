package com.acme.backup.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public CsrfView token(CsrfToken csrfToken) {
        return new CsrfView(csrfToken.getHeaderName(), csrfToken.getToken());
    }
    public record CsrfView(String headerName, String token) {}
}
