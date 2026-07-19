package com.parking.backend.security;

import com.parking.backend.service.RateLimitService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Value("${rate.limit.user.login}")
    private long userLoginLimit;

    @Value("${rate.limit.guard.login}")
    private long guardLoginLimit;

    @Value("${rate.limit.admin.login}")
    private long adminLoginLimit;

    @Value("${rate.limit.refresh}")
    private long refreshLimit;

    @Value("${rate.limit.payment}")
    private long paymentLimit;

    @Value("${rate.limit.admin}")
    private long adminLimit;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        String ip = getClientIp(request);

        long capacity = 0;
        Duration duration = Duration.ofMinutes(1);
        String bucketKey = null;

        if (path.startsWith("/api/auth/login")) {

            capacity = userLoginLimit;
            bucketKey = ip + ":LOGIN";

        } else if (path.startsWith("/api/auth/admin/login")) {

            capacity = adminLoginLimit;
            bucketKey = ip + ":ADMIN_LOGIN";

        } else if (path.startsWith("/api/auth/guard/login")) {

            capacity = guardLoginLimit;
            bucketKey = ip + ":GUARD_LOGIN";

        } else if (path.startsWith("/api/auth/refresh")) {

            capacity = refreshLimit;
            bucketKey = ip + ":REFRESH";

        } else if (path.startsWith("/api/payment")) {

            capacity = paymentLimit;
            bucketKey = ip + ":PAYMENT";

        } else if (path.startsWith("/api/admin")) {

            capacity = adminLimit;
            bucketKey = ip + ":ADMIN";

        }

        if (bucketKey != null) {

            Bucket bucket = rateLimitService.resolveBucket(
                    bucketKey,
                    capacity,
                    duration);

            boolean allowed = bucket.tryConsume(1);

            if (!allowed) {

                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                response.getWriter().write("""
                        {
                          "success": false,
                          "error": "RATE_LIMIT_EXCEEDED",
                          "message": "Too many requests. Please try again later."
                        }
                        """);

                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}