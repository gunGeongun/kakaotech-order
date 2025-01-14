package gift.controller.OAuth;

import gift.dto.OAuth.AuthTokenResponse;
import gift.service.OAuth.KakaoAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/kakao")
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;

    @Autowired
    public KakaoAuthController(KakaoAuthService kakaoAuthService) {
        this.kakaoAuthService = kakaoAuthService;
    }

    @GetMapping("/login")
    public void getAuthCode(HttpServletResponse response) throws IOException {
        String url = kakaoAuthService.createCodeUrl();
        response.sendRedirect(url);
    }

    @GetMapping("/auth")
    public ResponseEntity<AuthTokenResponse> getAccessToken(@RequestParam String code) {
        AuthTokenResponse accessTokenResponse = kakaoAuthService.getAccessToken(code);
        return ResponseEntity.ok(accessTokenResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestParam String accessToken) {
        String token = kakaoAuthService.register(accessToken);
        return ResponseEntity.ok(Map.of("access_token", token));
    }
}
