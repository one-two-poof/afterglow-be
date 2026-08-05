package com.afterglow.web;

import com.afterglow.config.JwtProperties;
import com.afterglow.domain.User;
import com.afterglow.repository.UserRepository;
import com.afterglow.web.dto.AuthResponse;
import com.afterglow.web.dto.UserResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    public AuthController(UserRepository userRepository, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
    }

    /** 구글 로그인 진입점. Spring Security의 OAuth2 인증 시작 경로로 리다이렉트한다. */
    @GetMapping("/login/google")
    public ResponseEntity<Void> loginGoogle() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/google"))
                .build();
    }

    /** JWT가 있는 사용자의 내 정보 조회 */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal String userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return userRepository.findById(Long.parseLong(userId))
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }
}
