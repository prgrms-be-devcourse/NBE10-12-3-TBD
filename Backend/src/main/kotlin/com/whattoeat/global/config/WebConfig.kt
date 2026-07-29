package com.whattoeat.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    @Value("\${app.upload.path:uploads}")
    private lateinit var uploadPath: String

    @Value("\${app.upload.url-prefix}")
    private lateinit var uploadUrlPrefix: String

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        var prefix = uploadUrlPrefix

        if (prefix.startsWith("http://") || prefix.startsWith("https://")) {
            val pathStart = prefix.indexOf('/', prefix.indexOf("://") + 3)
            prefix = if (pathStart == -1) "/" else prefix.substring(pathStart)
        }
        if (!prefix.endsWith("/")) {
            prefix += "/"
        }

        registry.addResourceHandler(prefix + "**").addResourceLocations("file:$uploadPath/")
    }
}
