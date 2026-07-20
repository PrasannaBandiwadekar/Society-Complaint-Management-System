package com.society.complaint.api;

import com.society.complaint.config.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT filter — runs once per request, before Spring Security's own filters.
 *
 * Plain English:
 * 1. Checks every incoming request for an "Authorization: Bearer <token>" header
 * 2. If found, extracts the token and validates it using JwtService
 * 3. If valid, loads the user and tells Spring Security "this request is
 *    authenticated as this user" — so @PreAuthorize and hasRole() work normally
 * 4. If no token / invalid token: just continues the filter chain, letting
 *    Spring Security handle it (unauthenticated request → 401 for API routes)
 *
 * Only applies to /api/** routes — Thymeleaf pages still use cookie sessions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip JWT check for non-API requests (Thymeleaf pages use session auth)
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7); // remove "Bearer " prefix
            final String email = jwtService.extractEmail(jwt);

            // Only authenticate if not already authenticated
            if (email != null && SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT authenticated: {}", email);
                }
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            // Don't throw — just continue unauthenticated, Spring Security
            // will return 401 for protected endpoints
        }

        filterChain.doFilter(request, response);
    }
}
