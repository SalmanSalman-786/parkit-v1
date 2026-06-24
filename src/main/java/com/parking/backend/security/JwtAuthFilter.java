package com.parking.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 🔐 Check if token exists and starts with Bearer
        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7);

            try {
                String userId = jwtUtil.extractUsername(token);
                request.setAttribute("userId", userId);
                String role = jwtUtil.extractRole(token);

                // 🔥 Only set authentication if not already set
                if (SecurityContextHolder.getContext().getAuthentication() == null) {

                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }

            } catch (ExpiredJwtException e) {

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                response.getWriter().write("Token expired");

                return;

            } catch (JwtException e) {

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                response.getWriter().write("Invalid token");

                return;

            } catch (Exception e) {

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                response.getWriter().write("Authentication failed");

                return;
            }
        }

        // ✅ Continue filter chain
        filterChain.doFilter(request, response);
    }
}