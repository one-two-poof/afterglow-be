package com.afterglow.security;

import com.afterglow.config.JwtProperties;
import com.afterglow.config.OAuthProperties;
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
    private final OAuthProperties oAuthProperties;

    public OAuth2SuccessHandler(
            GoogleOAuthService googleOAuthService,
            JwtProperties jwtProperties,
            OAuthProperties oAuthProperties) {
        this.googleOAuthService = googleOAuthService;
        this.jwtProperties = jwtProperties;
        this.oAuthProperties = oAuthProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String jwt = googleOAuthService.loginOrRegister(oAuth2User);

        // 프론트엔드로 JWT를 쿼리 파라미터에 담아 리다이렉트.
        // 대상 주소는 app.oauth.frontend-redirect-url(기본값 localhost:3000, 운영은
        // OAUTH_FRONTEND_REDIRECT_URL 환경변수로 오버라이드)로 프로파일별로 분리되어 있음.
        String redirectUrl = UriComponentsBuilder
                .fromUriString(oAuthProperties.frontendRedirectUrl())
                .queryParam("token", jwt)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
