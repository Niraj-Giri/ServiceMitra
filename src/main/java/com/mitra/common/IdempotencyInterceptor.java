package com.mitra.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey != null) {
            log.info("Parsed Idempotency-Key header: {}", idempotencyKey);
            // Placeholder: Check against processed_idempotency_keys table and return early if duplicate
        }
        return true;
    }
}
