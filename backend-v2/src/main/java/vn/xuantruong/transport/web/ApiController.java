package vn.xuantruong.transport.web;

import jakarta.servlet.http.HttpSession;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.xuantruong.transport.service.MailService;
import vn.xuantruong.transport.service.PasswordService;
import vn.xuantruong.transport.service.TransportService;

@RestController
public class ApiController {
    private final TransportService service;
    private final PasswordService passwords;
    private final MailService mail;

    public ApiController(TransportService service, PasswordService passwords, MailService mail) {
        this.service = service;
        this.passwords = passwords;
        this.mail = mail;
    }

    @GetMapping("/healthz")
    Map<String, Object> health() {
        return Map.of("ok", true, "version", "2.0.0", "uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
    }

    @PostMapping("/api/login")
    Map<String, Object> login(@RequestBody Map<String, Object> body, HttpSession session) {
        Map<String, Object> admin = service.findAdminForLogin(text(body.get("username")));
        if (admin == null || !passwords.verify(text(body.get("password")), text(admin.get("salt")), text(admin.get("password_hash")))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sai ten dang nhap hoac mat khau");
        }
        session.setAttribute("adminId", ((Number) admin.get("id")).longValue());
        return Map.of("ok", true);
    }

    @PostMapping("/api/logout")
    Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/api/me")
    Map<String, Object> me(HttpSession session) {
        Object id = session.getAttribute("adminId");
        if (!(id instanceof Number)) return Map.of("loggedIn", false);
        Map<String, Object> admin = service.findAdmin(((Number) id).longValue());
        if (admin == null) return Map.of("loggedIn", false);
        Map<String, Object> result = new LinkedHashMap<>(admin);
        result.put("loggedIn", true);
        return result;
    }

    @PostMapping("/api/change-password")
    Map<String, Object> changePassword(@RequestBody Map<String, Object> body, HttpSession session) {
        service.changePassword(adminId(session), text(body.get("oldPassword")), text(body.get("newPassword")));
        return Map.of("ok", true);
    }

    @PostMapping("/api/bookings")
    Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
        long id = service.createBooking(body);
        Thread.startVirtualThread(() -> mail.sendBookingNotification(id, body));
        return Map.of("ok", true, "id", id);
    }

    @GetMapping("/api/bookings")
    List<Map<String, Object>> bookings() { return service.bookings(); }

    @PutMapping("/api/bookings/{id}")
    Map<String, Object> updateBooking(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.updateBooking(id, body);
        return Map.of("ok", true);
    }

    @GetMapping("/api/customers")
    List<Map<String, Object>> customers() { return service.customers(); }

    @GetMapping("/api/customers/{id}/bookings")
    List<Map<String, Object>> customerBookings(@PathVariable long id) { return service.customerBookings(id); }

    @PutMapping("/api/customers/{id}")
    Map<String, Object> updateCustomer(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.updateCustomer(id, body);
        return Map.of("ok", true);
    }

    @GetMapping("/api/vehicles")
    List<Map<String, Object>> vehicles() { return service.vehicles(); }

    @PostMapping("/api/vehicles")
    Map<String, Object> createVehicle(@RequestBody Map<String, Object> body) {
        return Map.of("ok", true, "id", service.createVehicle(body));
    }

    @PutMapping("/api/vehicles/{id}")
    Map<String, Object> updateVehicle(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.updateVehicle(id, body);
        return Map.of("ok", true);
    }

    @DeleteMapping("/api/vehicles/{id}")
    Map<String, Object> deleteVehicle(@PathVariable long id) {
        service.deleteVehicle(id);
        return Map.of("ok", true);
    }

    @GetMapping("/api/trips")
    List<Map<String, Object>> trips() { return service.trips(); }

    @PostMapping("/api/trips")
    Map<String, Object> createTrip(@RequestBody Map<String, Object> body) {
        return Map.of("ok", true, "id", service.createTrip(body));
    }

    @PutMapping("/api/trips/{id}")
    Map<String, Object> updateTrip(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.updateTrip(id, text(body.get("trang_thai")));
        return Map.of("ok", true);
    }

    @GetMapping("/api/stats")
    Map<String, Object> stats() { return service.stats(); }

    @GetMapping("/api/stats/revenue")
    List<Map<String, Object>> revenue() { return service.revenue(); }

    @GetMapping("/api/admins")
    List<Map<String, Object>> admins() { return service.admins(); }

    @PostMapping("/api/admins")
    Map<String, Object> createAdmin(@RequestBody Map<String, Object> body) {
        return Map.of("ok", true, "id", service.createAdmin(body));
    }

    @DeleteMapping("/api/admins/{id}")
    Map<String, Object> deleteAdmin(@PathVariable long id, HttpSession session) {
        service.deleteAdmin(adminId(session), id);
        return Map.of("ok", true);
    }

    private long adminId(HttpSession session) {
        return ((Number) session.getAttribute("adminId")).longValue();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
