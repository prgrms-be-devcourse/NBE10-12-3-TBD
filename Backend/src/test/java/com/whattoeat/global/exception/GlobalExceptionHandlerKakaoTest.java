package com.whattoeat.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattoeat.global.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerKakaoTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("KakaoApiException 발생 시 502 상태코드 반환")
    void handleKakaoApiException_returns502() {
        KakaoApiException exception = new KakaoApiException("카카오 API 서버 오류");

        ResponseEntity<ErrorResponse> response = handler.handleKakaoApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("KakaoApiException 발생 시 응답 바디에 메시지 포함")
    void handleKakaoApiException_containsMessage() {
        KakaoApiException exception = new KakaoApiException("카카오 API 요청 오류 :401 UNAUTHORIZED");

        ResponseEntity<ErrorResponse> response = handler.handleKakaoApiException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("카카오 API 요청 오류 :401 UNAUTHORIZED");
        assertThat(response.getBody().getStatus()).isEqualTo(502);
    }
}
