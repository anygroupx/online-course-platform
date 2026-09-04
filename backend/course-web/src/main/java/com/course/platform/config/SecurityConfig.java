package com.course.platform.config;

import com.course.platform.security.JwtAuthenticationFilter;
import com.course.platform.security.MustChangePasswordFilter;
import com.course.platform.security.JwtAuthenticationEntryPoint;
import com.course.platform.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security配置类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;
    private final MustChangePasswordFilter mustChangePasswordFilter;

    // Source: Docker部署修复 - 使用@ConfigurationProperties正确读取YAML列表
    private final CorsProperties corsProperties;

    /**
     * 不需要认证的路径
     */
    private static final String[] PERMIT_ALL_PATHS = {
            // 认证相关（包含 /auth/login, /auth/refresh 等）
            "/auth/**",
            // 健康检查端点（Docker容器健康检查）
            "/health",
            "/ping",
            // 注册相关
            "/register",
            "/register/validate-invite-code",
            // 支付回调
            "/payment/notify",
            "/payment/return",
            // API文档
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            // 客户端只读主题配置
            "/theme/variables",
            // 静态资源
            "/favicon.ico",
            "/error"
    };

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（使用JWT不需要）
                .csrf(AbstractHttpConfigurer::disable)

                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 配置异常处理
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )

                // 配置Session管理（无状态）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 第三方API路径 - 优先匹配（注意：context-path=/api，所以这里只需要匹配 /external/**）
                        .requestMatchers("/external/**").permitAll()
                        // 其他白名单路径
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        // 管理端路径必须 ADMIN
                        .requestMatchers("/admin/orders/**", "/admin/aqks/**", "/order-batch/**", "/admin/countdown-config/**", "/admin/docking/**").hasAuthority("order:update")
                        .requestMatchers("/payment/config/**", "/payment-config/**").hasAuthority("payment:config")
                        .requestMatchers("/system/**", "/system-config/**", "/system-variable/**", "/admin/variables/**").hasAuthority("system-config:update")
                        .requestMatchers("/logs/**").hasAuthority("security:event:read")
                        .requestMatchers("/announcement/create").hasAuthority("announcement:create")
                        .requestMatchers("/announcement/update", "/announcement/page").hasAuthority("announcement:update")
                        .requestMatchers("/announcement/*/publish", "/announcement/*/offline").hasAuthority("announcement:publish")
                        .requestMatchers("/customer-service/admin/**").hasAuthority("customer-service:read")
                        .requestMatchers("/customer-service/session/*/assign").hasAuthority("customer-service:assign")
                        .requestMatchers("/admin/api-providers/**").hasAuthority("api-provider:update")
                        .requestMatchers("/admin/platforms/**", "/admin/platform-categories/**").hasAuthority("platform:update")
                        .requestMatchers("/admin/security/**").authenticated()
                        .requestMatchers("/admin/rbac/**").hasAuthority("rbac:manage")
                        .requestMatchers("/admin/**").denyAll()
                        // 所有其他请求需要认证
                        .anyRequest().authenticated()
                )

                // 添加限流与JWT过滤器
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS配置
     * Source: Docker部署修复 - 使用CorsProperties从配置文件读取
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源（从CorsProperties读取）
        // 使用 setAllowedOriginPatterns 代替 setAllowedOrigins 以支持更灵活的匹配
        java.util.List<String> origins = corsProperties.getAllowedOrigins() == null
                ? java.util.List.of()
                : corsProperties.getAllowedOrigins().stream()
                    .filter(o -> o != null && !"*".equals(o.trim()) && !o.contains("*"))
                    .toList();
        configuration.setAllowedOriginPatterns(origins);

        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许携带凭证
        configuration.setAllowCredentials(true);

        // 预检请求的有效期（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
