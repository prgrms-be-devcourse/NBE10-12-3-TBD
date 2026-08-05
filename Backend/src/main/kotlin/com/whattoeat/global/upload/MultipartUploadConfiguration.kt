package com.whattoeat.global.upload

import org.springframework.boot.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import jakarta.servlet.MultipartConfigElement

@Configuration
class MultipartUploadConfiguration {

    @Bean
    fun multipartConfigElement(): MultipartConfigElement {
        val factory = MultipartConfigFactory()
        factory.setMaxFileSize(DataSize.ofMegabytes(10))
        factory.setMaxRequestSize(DataSize.ofMegabytes(12))
        return factory.createMultipartConfig()
    }
}
