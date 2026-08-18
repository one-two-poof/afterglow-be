package com.afterglow.config;

import com.afterglow.security.JwtAuthenticationFilter;
import com.afterglow.security.JwtProvider;
import com.afterglow.security.OAuth2SuccessHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final AppCorsProperties corsProperties;

    public SecurityConfig(
            JwtProvider jwtProvider,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            AppCorsProperties corsProperties) {
        this.jwtProvider = jwtProvider;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 공개 API
                .requestMatchers(
                    "/api/health",
                    "/api/items/**",
                    "/api/medical-tourism/**",
                    "/api/auth/login/google",
                    "/api/admin/login",
                    "/oauth2/**",
                    "/login/**",
                    "/h2-console/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/error",
                    // 그늘길 라우팅 프로토타입 (batch/src/main.py + shaderoute 패키지) — DB 없는 공개 프로토타입
                    "/",
                    "/index.html",
                    "/api/route",
                    // buildings.pmtiles를 정적 리소스로 서빙 (static/data/buildings.pmtiles)
                    "/data/**",
                    // 장소 관리 페이지 (정적 리소스 자체는 공개, 안의 쓰기 API 호출은 JWT로 인증)
                    "/admin/**"
                ).permitAll()
                // 장소 조회는 공개, 생성·수정·삭제·동기화는 관리자(role=ADMIN, /admin/login.html) 전용
                .requestMatchers(HttpMethod.GET, "/api/places", "/api/places/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/places", "/api/places/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/places/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/places/**").hasRole("ADMIN")
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            // oauth2Login 기본 진입점은 미인증 요청을 구글 로그인으로 302 리다이렉트시킴.
            // API(fetch) 클라이언트는 리다이렉트를 못 알아채므로, 인증 안 된 API 호출엔
            // 항상 401 JSON을 내려주도록 오버라이드.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(unauthorizedJsonEntryPoint())
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtProvider),
                UsernamePasswordAuthenticationFilter.class)
            .headers(headers ->
                headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedJsonEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"authentication required\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = corsProperties.allowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            config.setAllowedOrigins(origins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
