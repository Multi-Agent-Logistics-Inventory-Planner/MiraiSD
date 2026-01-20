package com.mirai.inventoryservice.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String personRole = null;
        String personId = null;
        String personName = null;
        String personEmail = null;

        // Extract JWT token from Authorization header
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                personRole = jwtService.extractRole(token);
                personId = jwtService.extractPersonId(token);
                personName = jwtService.extractName(token);
                personEmail = jwtService.extractEmail(token);
            } catch (Exception e) {
                // Invalid token
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Validate token and set authentication (role can be null for unauthenticated users)
        if (personId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Map<String, String> principal = new java.util.HashMap<>();
            principal.put("personId", personId);
            principal.put("personName", personName != null ? personName : "Unknown");
            principal.put("email", personEmail);
            principal.put("role", personRole);

            if (jwtService.validateToken(token)) {
                // Create authentication object with role (default to USER if no role)
                String role = personRole != null ? personRole.toUpperCase() : "USER";
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}