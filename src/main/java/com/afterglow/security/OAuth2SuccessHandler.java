package com.afterglow.security;

import com.afterglow.config.JwtProperties;
import com.afterglow.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleOAuthService googleOAuthService;
    private final JwtProperties jwtProperties;

    public OAuth2SuccessHandler(GoogleOAuthService googleOAuthService, JwtProperties jwtProperties) {
        this.googleOAuthService = googleOAuthService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String jwt = googleOAuthService.loginOrRegister(oAuth2User);

        // 프론트엔드로 JWT를 쿼리 파라미터에 담아 리다이렉트
        // 운영환경에서는 프론트 도메인으로 변경하세요
        String redirectUrl = UriComponentsBuilder
                .fromUriString("http://localhost:3000/oauth/callback")
                .queryParam("token", jwt)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
