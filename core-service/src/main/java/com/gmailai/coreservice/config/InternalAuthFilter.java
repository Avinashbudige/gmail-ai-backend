package com.gmailai.coreservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * InternalAuthFilter — enforces the service-to-service trust boundary.
 *
 * The core-service must NEVER be called directly by clients. All legitimate
 * traffic flows through the API gateway, which injects the X-Internal-Secret
 * header on every proxied request.
 *
 * Exempt paths:
 *   /api/webhook/** — called by Google Pub/Sub, not the gateway.
 *   /health          — liveness/readiness probe.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${INTERNAL_SERVICE_SECRET:}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Exempt Google Pub/Sub webhook and health probe
        if (path.startsWith("/api/webhook") || path.startsWith("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // If no secret is configured (dev/mock mode), allow through with a warning
        if (internalSecret == null || internalSecret.isBlank()) {
            logger.warn("[Security] INTERNAL_SERVICE_SECRET is not set — skipping service auth check (dev mode only!)");
            filterChain.doFilter(request, response);
            return;
        }

        String providedSecret = request.getHeader("X-Internal-Secret");
        if (!internalSecret.equals(providedSecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden: missing or invalid service secret\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
