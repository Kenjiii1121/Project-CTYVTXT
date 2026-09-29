package vn.xuantruong.transport.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class DataSourceConfig {

    @Bean
    DataSource dataSource(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        String springUrl = environment.getProperty("spring.datasource.url");
        HikariConfig config = new HikariConfig();
        config.setPoolName("transport-v2-pool");
        config.setConnectionTimeout(10_000);
        config.setMaximumPoolSize(8);

        if (Boolean.parseBoolean(environment.getProperty("APP_FORCE_H2", "false"))) {
            configureH2(config);
        } else if (StringUtils.hasText(databaseUrl)) {
            JdbcSettings settings = parseDatabaseUrl(databaseUrl, environment.getProperty("DB_SSL", "true"));
            config.setJdbcUrl(settings.url());
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        } else if (StringUtils.hasText(springUrl)) {
            config.setJdbcUrl(springUrl);
            config.setUsername(environment.getProperty("spring.datasource.username", ""));
            config.setPassword(environment.getProperty("spring.datasource.password", ""));
        } else if (Boolean.parseBoolean(environment.getProperty("APP_ALLOW_H2", "false"))) {
            configureH2(config);
        } else {
            throw new IllegalStateException("Thieu DATABASE_URL. Dat APP_ALLOW_H2=true neu chi muon chay demo local.");
        }
        return new HikariDataSource(config);
    }

    private static void configureH2(HikariConfig config) {
        config.setJdbcUrl("jdbc:h2:mem:transport;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
    }

    static JdbcSettings parseDatabaseUrl(String value, String dbSsl) {
        if (value.startsWith("jdbc:")) {
            return new JdbcSettings(value, "", "");
        }
        int schemeSeparator = value.indexOf("://");
        String scheme = schemeSeparator > 0 ? value.substring(0, schemeSeparator) : "";
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL phai la PostgreSQL URL");
        }

        String remainder = value.substring(schemeSeparator + 3);
        int pathStart = remainder.indexOf('/');
        int queryStart = remainder.indexOf('?');
        int authorityEnd = pathStart >= 0 ? pathStart : remainder.length();
        if (queryStart >= 0 && queryStart < authorityEnd) authorityEnd = queryStart;

        String authority = remainder.substring(0, authorityEnd);
        int credentialsEnd = authority.lastIndexOf('@');
        if (credentialsEnd < 0) throw new IllegalArgumentException("DATABASE_URL thieu thong tin dang nhap");

        String[] credentials = authority.substring(0, credentialsEnd).split(":", 2);
        String username = credentials.length > 0 ? decode(credentials[0]) : "";
        String password = credentials.length > 1 ? decode(credentials[1]) : "";

        String hostAndPort = authority.substring(credentialsEnd + 1);
        int port = 5432;
        String host = hostAndPort;
        int portSeparator = hostAndPort.lastIndexOf(':');
        if (portSeparator > 0 && hostAndPort.substring(portSeparator + 1).matches("\\d+")) {
            host = hostAndPort.substring(0, portSeparator);
            port = Integer.parseInt(hostAndPort.substring(portSeparator + 1));
        }
        if (!StringUtils.hasText(host)) throw new IllegalArgumentException("DATABASE_URL thieu hostname");

        String path = pathStart >= 0
                ? remainder.substring(pathStart, queryStart >= 0 ? queryStart : remainder.length())
                : "/postgres";
        String query = queryStart >= 0 ? remainder.substring(queryStart + 1) : null;
        if (!StringUtils.hasText(query) && !"false".equalsIgnoreCase(dbSsl)) {
            query = "sslmode=require";
        } else if (!StringUtils.hasText(query)) {
            query = "sslmode=disable";
        }
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?" + query;
        return new JdbcSettings(jdbcUrl, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    record JdbcSettings(String url, String username, String password) {}
}
