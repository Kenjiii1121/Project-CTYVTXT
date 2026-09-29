package vn.xuantruong.transport.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataSourceConfigTest {
    @Test
    void convertsSupabaseStyleUrlToJdbcSettings() {
        DataSourceConfig.JdbcSettings settings = DataSourceConfig.parseDatabaseUrl(
                "postgresql://user.name:p%40ss@pooler.example.com:6543/postgres", "true");

        assertThat(settings.url()).isEqualTo("jdbc:postgresql://pooler.example.com:6543/postgres?sslmode=require");
        assertThat(settings.username()).isEqualTo("user.name");
        assertThat(settings.password()).isEqualTo("p@ss");
    }

    @Test
    void acceptsUnescapedSpecialCharactersInPassword() {
        DataSourceConfig.JdbcSettings settings = DataSourceConfig.parseDatabaseUrl(
                "postgresql://postgres.project:p@ss[word]+value@pooler.example.com:6543/postgres", "true");

        assertThat(settings.url()).isEqualTo(
                "jdbc:postgresql://pooler.example.com:6543/postgres?sslmode=require");
        assertThat(settings.username()).isEqualTo("postgres.project");
        assertThat(settings.password()).isEqualTo("p@ss[word]+value");
    }
}
