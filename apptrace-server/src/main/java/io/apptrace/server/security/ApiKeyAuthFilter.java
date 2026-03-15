package io.apptrace.server.security;

import io.apptrace.server.domain.model.ApiKeyEntity;
import io.apptrace.server.exception.InvalidApiKeyException;
import io.apptrace.server.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the Bearer API key on every request.
 *
 * On success: stores the authenticated ApiKeyEntity in ApiKeyContext
 * so controllers can read the tenantId without touching the DB again.
 *
 * On failure: returns 401 immediately, request never reaches the controller.
 */
@Component
@AllArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/api/v1/tenants");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing Authorization header");
            return;
        }

        String rawKey = header.substring(BEARER_PREFIX.length()).strip();

        try {
            ApiKeyEntity apiKey = apiKeyService.authenticate(rawKey);
            // Store in thread-local so controllers can access it
            ApiKeyContext.set(apiKey);
            chain.doFilter(request, response);
        } catch (InvalidApiKeyException e) {
            writeUnauthorized(response, e.getMessage());
        } finally {
            // Always clean up thread-local to prevent memory leaks
            ApiKeyContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"error": "Unauthorized", "message": "%s"}
            """.formatted(message));
    }
}