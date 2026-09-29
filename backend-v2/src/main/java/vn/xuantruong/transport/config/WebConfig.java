package vn.xuantruong.transport.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.xuantruong.transport.security.ApiAuthInterceptor;
import vn.xuantruong.transport.security.RateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(ApiAuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String projectRoot = Path.of("..").toAbsolutePath().normalize().toUri().toString();
        String adminRoot = Path.of("..", "backend", "admin").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/", "/*.html").addResourceLocations(projectRoot);
        registry.addResourceHandler("/css/**")
                .addResourceLocations(Path.of("..", "css").toAbsolutePath().normalize().toUri().toString());
        registry.addResourceHandler("/js/**")
                .addResourceLocations(Path.of("..", "js").toAbsolutePath().normalize().toUri().toString());
        registry.addResourceHandler("/images/**")
                .addResourceLocations(Path.of("..", "images").toAbsolutePath().normalize().toUri().toString());
        registry.addResourceHandler("/admin/**").addResourceLocations(adminRoot);
    }
}
