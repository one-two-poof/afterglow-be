package com.afterglow.web;

import com.afterglow.config.AdminProperties;
import com.afterglow.config.JwtProperties;
import com.afterglow.security.JwtProvider;
import com.afterglow.web.dto.AdminLoginRequest;
import com.afterglow.web.dto.AuthResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 전용 로그인(/admin/login.html에서 호출). 구글 OAuth와 완전히 별개 경로이며,
 * 성공 시 role=ADMIN인 JWT를 발급한다. /api/places 쓰기 API는 이 role만 허용한다
 * (SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AdminProperties adminProperties;
    private final JwtProperties jwtProperties;
    private final JwtProvider jwtProvider;

    public AdminAuthController(
            AdminProperties adminProperties, JwtProperties jwtProperties, JwtProvider jwtProvider) {
        this.adminProperties = adminProperties;
        this.jwtProperties = jwtProperties;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AdminLoginRequest request) {
        if (!matches(adminProperties.email(), request.email())
                || !matches(adminProperties.password(), request.password())) {
            log.warn("관리자 로그인 실패: email={}", request.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        String token = jwtProvider.generate(0L, adminProperties.email(), "ADMIN");
        return AuthResponse.of(token, jwtProperties.expirationMs());
    }

    /** 타이밍 공격을 피하려고 길이가 달라도 항상 상수 시간 비교로 처리. */
    private static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
