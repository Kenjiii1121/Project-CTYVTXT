package vn.xuantruong.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "APP_FORCE_H2=true",
                "ADMIN_INITIAL_PASSWORD=integration-password-123",
                "COOKIE_SECURE=false"
        })
class TransportApplicationIntegrationTest {
    private static final Pattern FIRST_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)");

    @LocalServerPort
    private int port;

    @Test
    void validatesBookingsProtectsAdminApisAndPreventsDoubleAssignment() throws Exception {
        HttpClient publicClient = HttpClient.newHttpClient();

        HttpResponse<String> health = get(publicClient, "/healthz");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"database\":\"up\"");

        HttpResponse<String> unauthenticated = get(publicClient, "/api/bookings");
        assertThat(unauthenticated.statusCode()).isEqualTo(401);

        HttpResponse<String> invalidBooking = post(publicClient, "/api/bookings", """
                {"ho_ten":"Invalid","dien_thoai":"abc","diem_lay_hang":"A","diem_giao_hang":"B","trong_luong":-1}
                """);
        assertThat(invalidBooking.statusCode()).isEqualTo(400);

        HttpResponse<String> validBooking = post(publicClient, "/api/bookings", """
                {"ho_ten":"Integration Test","dien_thoai":"0900000000","diem_lay_hang":"A","diem_giao_hang":"B","trong_luong":1.5}
                """);
        assertThat(validBooking.statusCode()).isEqualTo(200);
        long bookingId = firstId(validBooking.body());

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient adminClient = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> login = post(adminClient, "/api/login", """
                {"username":"admin","password":"integration-password-123"}
                """);
        assertThat(login.statusCode()).isEqualTo(200);

        HttpResponse<String> vehicles = get(adminClient, "/api/vehicles");
        assertThat(vehicles.statusCode()).isEqualTo(200);
        long vehicleId = firstId(vehicles.body());

        String trip = """
                {"booking_id":%d,"vehicle_id":%d,"tai_xe":"Driver","ngay_khoi_hanh":"2026-10-13"}
                """.formatted(bookingId, vehicleId);
        HttpResponse<String> firstTrip = post(adminClient, "/api/trips", trip);
        assertThat(firstTrip.statusCode()).isEqualTo(200);
        long firstTripId = firstId(firstTrip.body());

        HttpResponse<String> duplicateTrip = post(adminClient, "/api/trips", trip);
        assertThat(duplicateTrip.statusCode()).isEqualTo(400);
        assertThat(duplicateTrip.body()).contains("Xe dang ban");

        HttpResponse<String> completed = put(adminClient, "/api/trips/" + firstTripId, "{\"trang_thai\":\"hoan_thanh\"}");
        assertThat(completed.statusCode()).isEqualTo(200);

        HttpResponse<String> secondBooking = post(publicClient, "/api/bookings", """
                {"ho_ten":"Concurrent Test","dien_thoai":"0911111111","diem_lay_hang":"C","diem_giao_hang":"D","trong_luong":2}
                """);
        long secondBookingId = firstId(secondBooking.body());
        String concurrentTrip = """
                {"booking_id":%d,"vehicle_id":%d,"tai_xe":"Driver 2","ngay_khoi_hanh":"2026-10-14"}
                """.formatted(secondBookingId, vehicleId);

        HttpRequest requestOne = postRequest("/api/trips", concurrentTrip);
        HttpRequest requestTwo = postRequest("/api/trips", concurrentTrip);
        CompletableFuture<HttpResponse<String>> responseOne = adminClient.sendAsync(requestOne, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> responseTwo = adminClient.sendAsync(requestTwo, HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = List.of(responseOne.join().statusCode(), responseTwo.join().statusCode()).stream().sorted().toList();
        assertThat(statuses).containsExactly(200, 400);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String json) throws Exception {
        return client.send(postRequest(path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(HttpClient client, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest postRequest(String path, String json) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private long firstId(String json) {
        Matcher matcher = FIRST_ID.matcher(json);
        assertThat(matcher.find()).as("response contains an id: %s", json).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
