package vn.xuantruong.transport.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.util.StringUtils;
import vn.xuantruong.transport.security.ApiAuthInterceptor;
import vn.xuantruong.transport.security.RateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final Environment environment;

    public WebConfig(ApiAuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor, Environment environment) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.environment = environment;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path projectPath = Path.of(pathProperty("APP_SITE_ROOT", "..")).toAbsolutePath().normalize();
        Path adminPath = Path.of(pathProperty("APP_ADMIN_ROOT", "../backend/admin")).toAbsolutePath().normalize();
        String projectRoot = location(projectPath);
        String adminRoot = location(adminPath);
        registry.addResourceHandler("/", "/*.html").addResourceLocations(projectRoot);
        registry.addResourceHandler("/css/**")
                .addResourceLocations(location(projectPath.resolve("css")));
        registry.addResourceHandler("/js/**")
                .addResourceLocations(location(projectPath.resolve("js")));
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location(projectPath.resolve("images")));
        registry.addResourceHandler("/admin/**").addResourceLocations(adminRoot);
    }

    private String location(Path path) {
        String uri = path.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private String pathProperty(String name, String fallback) {
        String value = environment.getProperty(name);
        return StringUtils.hasText(value) ? value : fallback;
    }
}
