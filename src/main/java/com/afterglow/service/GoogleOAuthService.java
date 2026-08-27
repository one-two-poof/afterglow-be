package com.afterglow.service;

import com.afterglow.domain.User;
import com.afterglow.repository.UserRepository;
import com.afterglow.security.JwtProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public GoogleOAuthService(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    /**
     * (웹) Spring Security OAuth2 로그인 성공 후 호출.
     * Google에서 받은 사용자 정보로 User를 upsert하고 JWT를 발급한다.
     */
    @Transactional
    public String loginOrRegister(OAuth2User oAuth2User) {
        return upsertAndIssueJwt(
                oAuth2User.getAttribute("sub"),
                oAuth2User.getAttribute("email"),
                oAuth2User.getAttribute("name"),
                oAuth2User.getAttribute("picture"));
    }

    private String upsertAndIssueJwt(String googleSub, String email, String name, String picture) {
        User user = userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.update(name, picture);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(new User(email, name, picture, googleSub)));

        log.info("Google login: userId={}, email={}", user.getId(), user.getEmail());

        return jwtProvider.generate(user.getId(), user.getEmail(), user.getRole().name());
    }
}
