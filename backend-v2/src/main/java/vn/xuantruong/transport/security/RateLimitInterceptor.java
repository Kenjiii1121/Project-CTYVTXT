package vn.xuantruong.transport.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public RateLimitInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        Limit limit = switch (path) {
            case "/api/login" -> new Limit(10, 15 * 60_000L, "Thu qua nhieu lan. Vui long doi 15 phut roi thu lai.");
            case "/api/bookings" -> new Limit(8, 60 * 60_000L, "Ban da gui qua nhieu yeu cau. Vui long thu lai sau hoac goi hotline.");
            default -> null;
        };
        if (limit == null) return true;

        long now = clock.millis();
        if (windows.size() > 1_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= 60 * 60_000L);
        }
        String key = path + ":" + clientIp(request);
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= limit.windowMs) return new Window(now, 1);
            return new Window(current.startedAt, current.count + 1);
        });
        if (window.count <= limit.maxRequests) return true;

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", limit.message));
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private record Limit(int maxRequests, long windowMs, String message) {}
    private record Window(long startedAt, int count) {}
}
