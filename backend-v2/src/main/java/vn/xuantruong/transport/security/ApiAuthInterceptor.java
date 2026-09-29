package vn.xuantruong.transport.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.xuantruong.transport.service.TransportService;

@Component
public class ApiAuthInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper;
    private final TransportService service;

    public ApiAuthInterceptor(ObjectMapper objectMapper, TransportService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("OPTIONS".equals(method)
                || "/api/login".equals(path)
                || "/api/me".equals(path)
                || ("POST".equals(method) && "/api/bookings".equals(path))) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (!(adminId instanceof Number)) {
            writeError(response, 401, "Chua dang nhap");
            return false;
        }
        if (path.startsWith("/api/admins") && !service.isOwner(((Number) adminId).longValue())) {
            writeError(response, 403, "Chi chu tai khoan moi co quyen nay");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
