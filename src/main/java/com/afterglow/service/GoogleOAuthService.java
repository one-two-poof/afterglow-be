package com.afterglow.service;

import com.afterglow.domain.User;
import com.afterglow.repository.UserRepository;
import com.afterglow.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

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
     * Spring Security OAuth2 로그인 성공 후 호출.
     * Google에서 받은 사용자 정보로 User를 upsert하고 JWT를 발급한다.
     */
    @Transactional
    public String loginOrRegister(OAuth2User oAuth2User) {
        String googleSub = oAuth2User.getAttribute("sub");
        String email     = oAuth2User.getAttribute("email");
        String name      = oAuth2User.getAttribute("name");
        String picture   = oAuth2User.getAttribute("picture");

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
