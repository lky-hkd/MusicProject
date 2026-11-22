package com.music.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.*;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiRateLimitInterceptor aiRateLimitInterceptor;

    @Value("${cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 聊天图片等本地上传文件映射到 /uploads/**
        String absolute = java.nio.file.Paths.get(uploadDir).toAbsolutePath().toString().replace("\\", "/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + (absolute.endsWith("/") ? absolute : absolute + "/"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/loginbaseCode",
                        "/api/user/code",
                        "/api/user/register",
                        "/api/user/resetPassword",
                        "/api/song/**",      // 按需放行公开接口
                        "/api/product/**",
                        "/api/order/alipay/notify",
                        "/api/order/alipay/return"
                );
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns("/api/ai/**")
                .order(1);

    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }
}