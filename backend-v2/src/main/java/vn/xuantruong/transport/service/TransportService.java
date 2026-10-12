package vn.xuantruong.transport.service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.xuantruong.transport.web.ApiException;

@Service
public class TransportService {
    private static final Set<String> BOOKING_STATUSES = Set.of("moi", "da_bao_gia", "dang_chay", "hoan_thanh", "huy");
    private static final Set<String> VEHICLE_STATUSES = Set.of("san_sang", "dang_chay", "bao_duong");
    private static final Set<String> TRIP_STATUSES = Set.of("cho_chay", "dang_chay", "hoan_thanh", "huy");
    private static final Set<String> ACTIVE_TRIP_STATUSES = Set.of("cho_chay", "dang_chay");
    private static final Set<String> ASSIGNABLE_BOOKING_STATUSES = Set.of("moi", "da_bao_gia");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-\\s().]{8,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final PasswordService passwords;

    public TransportService(JdbcTemplate jdbc, PasswordService passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public Map<String, Object> findAdminForLogin(String username) {
        return first("SELECT * FROM admins WHERE username = ?", username == null ? "" : username);
    }

    public Map<String, Object> findAdmin(long id) {
        return first("SELECT id, username, ho_ten, role FROM admins WHERE id = ?", id);
    }

    public boolean isOwner(long id) {
        Map<String, Object> admin = findAdmin(id);
        return admin != null && "owner".equals(admin.get("role"));
    }

    public void changePassword(long id, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) throw ApiException.badRequest("Mat khau moi phai tu 8 ky tu");
        Map<String, Object> admin = first("SELECT password_hash, salt FROM admins WHERE id = ?", id);
        if (admin == null || !passwords.verify(value(oldPassword), value(admin.get("salt")), value(admin.get("password_hash")))) {
            throw ApiException.badRequest("Mat khau cu khong dung");
        }
        String salt = passwords.newSalt();
        jdbc.update("UPDATE admins SET password_hash = ?, salt = ? WHERE id = ?", passwords.hash(newPassword, salt), salt, id);
    }

    @Transactional
    public long createBooking(Map<String, Object> booking) {
        requireText(booking, "ho_ten", "dien_thoai", "diem_lay_hang", "diem_giao_hang");
        String name = cleanText(booking.get("ho_ten"));
        String phone = cleanText(booking.get("dien_thoai"));
        String email = cleanText(booking.get("email"));
        Double weight = decimal(booking.get("trong_luong"));
        if (!PHONE_PATTERN.matcher(phone).matches()) throw ApiException.badRequest("So dien thoai khong hop le");
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw ApiException.badRequest("Email khong hop le");
        }
        if (weight != null && weight <= 0) throw ApiException.badRequest("Trong luong phai lon hon 0");
        requireMaxLength(name, 200, "Ho ten");
        requireMaxLength(email, 320, "Email");
        requireMaxLength(cleanText(booking.get("diem_lay_hang")), 500, "Diem lay hang");
        requireMaxLength(cleanText(booking.get("diem_giao_hang")), 500, "Diem giao hang");

        Map<String, Object> customer = first("SELECT id FROM customers WHERE dien_thoai = ?", phone);
        long customerId = customer == null
                ? insertAndReturnId("INSERT INTO customers (ten, dien_thoai, email) VALUES (?, ?, ?)",
                name, phone, nullable(email))
                : number(customer.get("id"));
        return insertAndReturnId("""
                INSERT INTO bookings (customer_id, ho_ten, dien_thoai, email, diem_lay_hang, diem_giao_hang, loai_hang, trong_luong, ghi_chu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, customerId, name, phone, nullable(email),
                cleanText(booking.get("diem_lay_hang")), cleanText(booking.get("diem_giao_hang")),
                nullable(booking.get("loai_hang")), weight, nullable(booking.get("ghi_chu")));
    }

    public List<Map<String, Object>> bookings() {
        return jdbc.queryForList("SELECT * FROM bookings ORDER BY id DESC");
    }

    public void updateBooking(long id, Map<String, Object> changes) {
        if (changes.containsKey("trang_thai")) {
            String status = value(changes.get("trang_thai"));
            if (!BOOKING_STATUSES.contains(status)) throw ApiException.badRequest("Trang thai khong hop le");
            jdbc.update("UPDATE bookings SET trang_thai = ? WHERE id = ?", status, id);
        }
        if (changes.containsKey("gia_tri")) {
            jdbc.update("UPDATE bookings SET gia_tri = ? WHERE id = ?", decimal(changes.get("gia_tri")), id);
        }
    }

    public List<Map<String, Object>> customers() {
        return jdbc.queryForList("""
                SELECT c.*, COUNT(b.id) AS so_don, MAX(b.created_at) AS don_gan_nhat
                FROM customers c LEFT JOIN bookings b ON b.customer_id = c.id
                GROUP BY c.id ORDER BY don_gan_nhat DESC NULLS LAST
                """);
    }

    public List<Map<String, Object>> customerBookings(long customerId) {
        return jdbc.queryForList("SELECT * FROM bookings WHERE customer_id = ? ORDER BY id DESC", customerId);
    }

    public void updateCustomer(long id, Map<String, Object> changes) {
        updateIfText("customers", "ten", id, changes.get("ten"));
        updateIfText("customers", "email", id, changes.get("email"));
        updateIfText("customers", "ghi_chu", id, changes.get("ghi_chu"));
    }

    public List<Map<String, Object>> vehicles() {
        return jdbc.queryForList("SELECT * FROM vehicles ORDER BY id");
    }

    public long createVehicle(Map<String, Object> vehicle) {
        requireText(vehicle, "ten", "bien_so");
        try {
            return insertAndReturnId("INSERT INTO vehicles (ten, bien_so, loai, tai_trong) VALUES (?, ?, ?, ?)",
                    value(vehicle.get("ten")), value(vehicle.get("bien_so")), nullable(vehicle.get("loai")), nullable(vehicle.get("tai_trong")));
        } catch (DuplicateKeyException error) {
            throw ApiException.badRequest("Bien so da ton tai");
        }
    }

    @Transactional
    public void updateVehicle(long id, Map<String, Object> changes) {
        if (changes.containsKey("trang_thai") && StringUtils.hasText(value(changes.get("trang_thai")))
                && !VEHICLE_STATUSES.contains(value(changes.get("trang_thai")))) {
            throw ApiException.badRequest("Trang thai xe khong hop le");
        }
        if ("san_sang".equals(value(changes.get("trang_thai")))
                && count("SELECT COUNT(*) FROM trips WHERE vehicle_id = ? AND trang_thai IN ('cho_chay', 'dang_chay')", id) > 0) {
            throw ApiException.badRequest("Xe van con chuyen dang hoat dong");
        }
        for (String field : List.of("ten", "bien_so", "loai", "tai_trong", "trang_thai")) {
            updateIfText("vehicles", field, id, changes.get(field));
        }
    }

    @Transactional
    public void deleteVehicle(long id) {
        Map<String, Object> vehicle = first("SELECT trang_thai FROM vehicles WHERE id = ? FOR UPDATE", id);
        if (vehicle == null) throw ApiException.notFound("Khong tim thay xe");
        if (!"san_sang".equals(vehicle.get("trang_thai"))
                || count("SELECT COUNT(*) FROM trips WHERE vehicle_id = ? AND trang_thai IN ('cho_chay', 'dang_chay')", id) > 0) {
            throw ApiException.badRequest("Khong the xoa xe dang co chuyen hoat dong");
        }
        jdbc.update("DELETE FROM vehicles WHERE id = ?", id);
    }

    public List<Map<String, Object>> trips() {
        return jdbc.queryForList("""
                SELECT t.*, v.ten AS ten_xe, v.bien_so, b.ho_ten AS khach, b.diem_lay_hang, b.diem_giao_hang
                FROM trips t LEFT JOIN vehicles v ON v.id = t.vehicle_id
                LEFT JOIN bookings b ON b.id = t.booking_id ORDER BY t.id DESC
                """);
    }

    @Transactional
    public long createTrip(Map<String, Object> trip) {
        Long vehicleId = longOrNull(trip.get("vehicle_id"));
        if (vehicleId == null) throw ApiException.badRequest("Can chon xe");
        Map<String, Object> vehicle = first("SELECT id, trang_thai FROM vehicles WHERE id = ? FOR UPDATE", vehicleId);
        if (vehicle == null) throw ApiException.notFound("Khong tim thay xe");
        if (!"san_sang".equals(vehicle.get("trang_thai"))) {
            throw ApiException.badRequest("Xe dang ban, vui long chon xe khac");
        }

        Long bookingId = longOrNull(trip.get("booking_id"));
        if (bookingId != null) {
            Map<String, Object> booking = first("SELECT id, trang_thai FROM bookings WHERE id = ? FOR UPDATE", bookingId);
            if (booking == null) throw ApiException.notFound("Khong tim thay don dat xe");
            if (!ASSIGNABLE_BOOKING_STATUSES.contains(value(booking.get("trang_thai")))) {
                throw ApiException.badRequest("Don dat xe da duoc xu ly hoac dang chay");
            }
        }

        long id = insertAndReturnId("INSERT INTO trips (booking_id, vehicle_id, tai_xe, ngay_khoi_hanh, ghi_chu) VALUES (?, ?, ?, ?, ?)",
                bookingId, vehicleId, nullable(trip.get("tai_xe")), nullable(trip.get("ngay_khoi_hanh")), nullable(trip.get("ghi_chu")));
        jdbc.update("UPDATE vehicles SET trang_thai = 'dang_chay' WHERE id = ?", vehicleId);
        if (bookingId != null) jdbc.update("UPDATE bookings SET trang_thai = 'dang_chay' WHERE id = ?", bookingId);
        return id;
    }

    @Transactional
    public void updateTrip(long id, String status) {
        if (!TRIP_STATUSES.contains(status)) throw ApiException.badRequest("Trang thai khong hop le");
        Map<String, Object> trip = first("SELECT * FROM trips WHERE id = ? FOR UPDATE", id);
        if (trip == null) throw ApiException.notFound("Khong tim thay chuyen");
        String currentStatus = value(trip.get("trang_thai"));
        if (status.equals(currentStatus)) return;

        Long vehicleId = longOrNull(trip.get("vehicle_id"));
        if (ACTIVE_TRIP_STATUSES.contains(status) && !ACTIVE_TRIP_STATUSES.contains(currentStatus)) {
            if (vehicleId == null) throw ApiException.badRequest("Chuyen khong con xe de mo lai");
            Map<String, Object> vehicle = first("SELECT trang_thai FROM vehicles WHERE id = ? FOR UPDATE", vehicleId);
            if (vehicle == null) throw ApiException.notFound("Khong tim thay xe");
            if (!"san_sang".equals(vehicle.get("trang_thai"))) {
                throw ApiException.badRequest("Xe dang ban, khong the mo lai chuyen");
            }
            jdbc.update("UPDATE vehicles SET trang_thai = 'dang_chay' WHERE id = ?", vehicleId);
        }

        jdbc.update("UPDATE trips SET trang_thai = ? WHERE id = ?", status, id);
        if (("hoan_thanh".equals(status) || "huy".equals(status)) && vehicleId != null) {
            long activeTrips = count("SELECT COUNT(*) FROM trips WHERE vehicle_id = ? AND id != ? AND trang_thai IN ('cho_chay', 'dang_chay')",
                    vehicleId, id);
            if (activeTrips == 0) jdbc.update("UPDATE vehicles SET trang_thai = 'san_sang' WHERE id = ?", vehicleId);
            if (trip.get("booking_id") != null && "hoan_thanh".equals(status)) {
                jdbc.update("UPDATE bookings SET trang_thai = 'hoan_thanh' WHERE id = ?", trip.get("booking_id"));
            }
        }
    }

    public boolean isDatabaseHealthy() {
        Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
        return value != null && value == 1;
    }

    public Map<String, Object> stats() {
        ZonedDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("don_moi", count("SELECT COUNT(*) FROM bookings WHERE trang_thai = 'moi'"));
        result.put("don_dang_chay", count("SELECT COUNT(*) FROM bookings WHERE trang_thai = 'dang_chay'"));
        result.put("xe_san_sang", count("SELECT COUNT(*) FROM vehicles WHERE trang_thai = 'san_sang'"));
        result.put("xe_dang_chay", count("SELECT COUNT(*) FROM vehicles WHERE trang_thai = 'dang_chay'"));
        result.put("tong_khach", count("SELECT COUNT(*) FROM customers"));
        result.put("don_thang_nay", count("SELECT COUNT(*) FROM bookings WHERE created_at >= ?", Timestamp.from(monthStart.toInstant())));
        Double revenue = jdbc.queryForObject("SELECT COALESCE(SUM(gia_tri), 0) FROM bookings WHERE trang_thai != 'huy' AND created_at >= ?",
                Double.class, Timestamp.from(monthStart.toInstant()));
        result.put("doanh_thu_thang_nay", revenue == null ? 0D : revenue);
        return result;
    }

    public List<Map<String, Object>> revenue() {
        YearMonth firstMonth = YearMonth.now().minusMonths(5);
        Instant since = firstMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT created_at, gia_tri, trang_thai FROM bookings WHERE created_at >= ? ORDER BY created_at",
                Timestamp.from(since));
        Map<YearMonth, double[]> grouped = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) grouped.put(firstMonth.plusMonths(i), new double[]{0, 0});
        for (Map<String, Object> row : rows) {
            Instant created = toInstant(row.get("created_at"));
            YearMonth month = YearMonth.from(created.atZone(ZoneId.systemDefault()));
            double[] values = grouped.get(month);
            if (values == null) continue;
            values[0]++;
            if (!"huy".equals(row.get("trang_thai")) && row.get("gia_tri") instanceof Number amount) values[1] += amount.doubleValue();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((month, values) -> result.add(Map.of("thang", month.toString(), "so_don", (long) values[0], "doanh_thu", values[1])));
        return result;
    }

    public List<Map<String, Object>> admins() {
        return jdbc.queryForList("SELECT id, username, ho_ten, role, created_at FROM admins ORDER BY id");
    }

    public long createAdmin(Map<String, Object> admin) {
        String username = value(admin.get("username"));
        String password = value(admin.get("password"));
        if (!StringUtils.hasText(username) || password.length() < 8) throw ApiException.badRequest("Can ten dang nhap va mat khau tu 8 ky tu");
        String salt = passwords.newSalt();
        String role = "owner".equals(admin.get("role")) ? "owner" : "staff";
        try {
            return insertAndReturnId("INSERT INTO admins (username, password_hash, salt, ho_ten, role) VALUES (?, ?, ?, ?, ?)",
                    username, passwords.hash(password, salt), salt,
                    StringUtils.hasText(value(admin.get("ho_ten"))) ? value(admin.get("ho_ten")) : username, role);
        } catch (DuplicateKeyException error) {
            throw ApiException.badRequest("Ten dang nhap da ton tai");
        }
    }

    public void deleteAdmin(long actingId, long targetId) {
        if (actingId == targetId) throw ApiException.badRequest("Khong the xoa chinh minh");
        Map<String, Object> target = first("SELECT role FROM admins WHERE id = ?", targetId);
        if (target != null && "owner".equals(target.get("role")) && count("SELECT COUNT(*) FROM admins WHERE role = 'owner' AND id != ?", targetId) == 0) {
            throw ApiException.badRequest("Phai con it nhat 1 chu tai khoan");
        }
        jdbc.update("DELETE FROM admins WHERE id = ?", targetId);
    }

    private void updateIfText(String table, String field, long id, Object candidate) {
        String text = value(candidate);
        if (StringUtils.hasText(text)) jdbc.update("UPDATE " + table + " SET " + field + " = ? WHERE id = ?", text, id);
    }

    private void requireText(Map<String, Object> body, String... fields) {
        for (String field : fields) if (!StringUtils.hasText(value(body.get(field)))) throw ApiException.badRequest("Thieu thong tin bat buoc");
    }

    private void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) throw ApiException.badRequest(fieldName + " qua dai");
    }

    private long insertAndReturnId(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a generated id");
        return key.longValue();
    }

    private Map<String, Object> first(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long longOrNull(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException error) { throw ApiException.badRequest("ID khong hop le"); }
    }

    private Double decimal(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException error) { throw ApiException.badRequest("Gia tri so khong hop le"); }
    }

    private Object nullable(Object value) {
        String text = cleanText(value);
        return text.isBlank() ? null : text;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String cleanText(Object value) {
        return value(value).trim();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.time.LocalDateTime dateTime) return dateTime.atZone(ZoneId.systemDefault()).toInstant();
        throw new IllegalStateException("Unsupported timestamp value: " + value);
    }
}
