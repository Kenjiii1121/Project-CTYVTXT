package vn.xuantruong.transport.service;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailService {
    private final JavaMailSenderImpl sender;
    private final String from;
    private final String notifyTo;

    public MailService(Environment environment) {
        this.from = environment.getProperty("GMAIL_USER", "");
        String password = environment.getProperty("GMAIL_APP_PASSWORD", "").replaceAll("\\s+", "");
        this.notifyTo = environment.getProperty("NOTIFY_TO", from);
        if (!StringUtils.hasText(from) || !StringUtils.hasText(password)) {
            sender = null;
            return;
        }
        JavaMailSenderImpl configured = new JavaMailSenderImpl();
        configured.setHost("smtp.gmail.com");
        configured.setPort(587);
        configured.setUsername(from);
        configured.setPassword(password);
        Properties properties = configured.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        sender = configured;
    }

    public void sendBookingNotification(long id, Map<String, Object> booking) {
        if (sender == null) return;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(notifyTo);
            helper.setSubject("Don dat xe moi #" + id + " - " + text(booking.get("ho_ten")));
            helper.setText("""
                    <h2>Don dat xe moi #%d</h2>
                    <p><b>Khach hang:</b> %s</p>
                    <p><b>Dien thoai:</b> %s</p>
                    <p><b>Tuyen:</b> %s &rarr; %s</p>
                    <p><b>Loai hang:</b> %s</p>
                    """.formatted(id, html(booking.get("ho_ten")), html(booking.get("dien_thoai")),
                    html(booking.get("diem_lay_hang")), html(booking.get("diem_giao_hang")), html(booking.get("loai_hang"))), true);
            sender.send(message);
        } catch (Exception error) {
            System.err.println("Khong gui duoc email don #" + id + ": " + error.getMessage());
        }
    }

    private String html(Object value) {
        return text(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }
}
