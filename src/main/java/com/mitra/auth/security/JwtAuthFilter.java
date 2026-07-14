package com.mitra.auth.security;

import com.mitra.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT Authentication Filter.
 *
 * Extracts the user ID and role from the JWT and sets them as the
 * Spring Security Authentication principal and granted authority.
 *
 * SEC-01 FIX: Previously this filter set zero GrantedAuthorities, which
 * made SecurityConfig RBAC rules completely ineffective. Now the role
 * claim from the JWT is mapped to ROLE_<ROLE> so Spring Security can
 * enforce hasRole('ADMIN'), hasRole('CUSTOMER'), etc.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public JwtAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = authService.extractUserIdFromToken(request);
        // Extract role from JWT claim (e.g. "CUSTOMER", "PROVIDER", "ADMIN")
        String role = authService.extractRoleFromToken(request);

        if (userId != null && role != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Map JWT role to Spring Security GrantedAuthority.
            // ROLE_ prefix is required for hasRole() matchers to work.
            List<SimpleGrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
