package com.parking.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;

        @Value("${cors.allowed-origins}")
        private String allowedOrigins;

        SecurityConfig(JwtAuthFilter jwtAuthFilter) {
                this.jwtAuthFilter = jwtAuthFilter;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

                http
                                .csrf(csrf -> csrf.disable())

                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                .headers(headers -> headers

                                                // Prevent MIME type sniffing
                                                .contentTypeOptions(contentTypeOptions -> {
                                                })

                                                // Prevent clickjacking
                                                .frameOptions(frameOptions -> frameOptions.deny())

                                                // Control referrer information
                                                .referrerPolicy(referrerPolicy -> referrerPolicy
                                                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))

                                                // Restrict browser features
                                                .permissionsPolicy(permissions -> permissions
                                                                .policy("camera=(), microphone=(), geolocation=()")))

                                .authorizeHttpRequests(auth -> auth

                                                // 🔥 VERY IMPORTANT
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // 🔥 WEBSOCKET FIX
                                                .requestMatchers("/ws/**").permitAll()

                                                .requestMatchers("/uploads/**").permitAll()

                                                // 🔓 AUTH APIs
                                                .requestMatchers("/api/auth/**").permitAll()

                                                // 🚓 GUARD ONLY
                                                .requestMatchers("/api/booking/entry/**",
                                                                "/api/booking/exit/**")
                                                .hasRole("GUARD")

                                                // 🔐 ADMIN ONLY
                                                .requestMatchers("/api/booking/revenue")
                                                .hasRole("ADMIN")

                                                // 👤 USER + GUARD
                                                .requestMatchers("/api/booking/user/**",
                                                                "/api/booking/cancel/**")
                                                .hasAnyRole("USER", "GUARD")

                                                // 🔄 LIVE BOOKINGS
                                                .requestMatchers("/api/booking/active",
                                                                "/api/booking/live")
                                                .hasAnyRole("GUARD", "ADMIN")

                                                // 🚶 WALKIN
                                                .requestMatchers("/api/booking/walkin/**")
                                                .hasRole("GUARD")

                                                // 🚓 GUARD DASHBOARD
                                                .requestMatchers("/api/guard/**")
                                                .hasAnyRole("GUARD", "ADMIN")

                                                // 📊 DETAILS
                                                .requestMatchers("/api/booking/details/**",
                                                                "/api/booking/overtime")
                                                .hasAnyRole("GUARD", "ADMIN")

                                                // 📍 PARKING (public)
                                                .requestMatchers("/api/parking/**").permitAll()

                                                .requestMatchers("/api/payment/webhook")
                                                .permitAll()

                                                .requestMatchers("/api/payment/**")
                                                .hasAnyRole("USER", "GUARD", "ADMIN")

                                                // 👤 USER APIs
                                                .requestMatchers("/api/user/**").authenticated()

                                                // 🔔 NOTIFICATIONS
                                                .requestMatchers("/api/notifications/**")
                                                .authenticated()

                                                .requestMatchers("/api/announcements")
                                                .permitAll()

                                                .requestMatchers("/api/admin/announcements/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers("/api/waitlist/**")
                                                .authenticated()

                                                .requestMatchers("/api/admin/**")
                                                .hasRole("ADMIN")

                                                .anyRequest().authenticated())

                                // 🔐 JWT FILTER
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // 🔐 PASSWORD ENCODER
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        // 🔥 CORRECT CORS CONFIG
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {

                CorsConfiguration config = new CorsConfiguration();

                config.setAllowCredentials(true);

                // ===============================
                // DEVELOPMENT
                // ===============================

                // config.setAllowedOriginPatterns(List.of("*"));
                config.setAllowedOrigins(
                                List.of(allowedOrigins.split(",")));

                // ===============================
                // PRODUCTION
                // Uncomment before deployment
                // ===============================

                // config.setAllowedOrigins(List.of(
                // "https://admin.parkit.in",
                // "https://parkit.in"
                // ));

                config.setAllowedMethods(List.of(
                                "GET", "POST", "PUT", "DELETE", "OPTIONS"));

                // config.setAllowedHeaders(List.of("*"));

                config.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "Origin",
                                "X-Requested-With",
                                "admin-key"));

                config.setExposedHeaders(List.of(
                                "Authorization"));

                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", config);

                return source;
        }

}