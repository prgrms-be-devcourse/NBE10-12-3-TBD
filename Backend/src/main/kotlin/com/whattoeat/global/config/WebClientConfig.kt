package com.whattoeat.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun webClient(): WebClient =
        WebClient.builder()
            .baseUrl("https://kapi.kakao.com")
            .build()
}
