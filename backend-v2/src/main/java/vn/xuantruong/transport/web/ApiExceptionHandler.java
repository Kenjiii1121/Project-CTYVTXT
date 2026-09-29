package vn.xuantruong.transport.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> handleApi(ApiException error) {
        return ResponseEntity.status(error.status()).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> handleUnexpected(Exception error) {
        log.error("Unhandled API error", error);
        return ResponseEntity.internalServerError().body(Map.of("error", "Loi server"));
    }
}
