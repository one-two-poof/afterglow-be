package com.afterglow.web;

import com.afterglow.config.JwtProperties;
import com.afterglow.domain.User;
import com.afterglow.repository.UserRepository;
import com.afterglow.web.dto.AuthResponse;
import com.afterglow.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Google OAuth2 로그인 및 로그인한 사용자 정보 조회")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    public AuthController(UserRepository userRepository, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
    }

    @Operation(
            summary = "구글 로그인 시작",
            description = "Spring Security의 OAuth2 인증 시작 경로(/oauth2/authorization/google)로 302 리다이렉트한다. "
                    + "로그인 성공 시 OAuth2SuccessHandler가 app.oauth.frontend-redirect-url로 JWT와 함께 다시 리다이렉트한다.")
    @GetMapping("/login/google")
    public ResponseEntity<Void> loginGoogle() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/google"))
                .build();
    }

    @Operation(summary = "내 정보 조회", description = "JWT로 인증된 사용자 본인의 정보를 조회한다. JWT 필요.")
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
