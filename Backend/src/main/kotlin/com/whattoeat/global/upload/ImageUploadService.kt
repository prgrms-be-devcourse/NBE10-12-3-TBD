package com.whattoeat.global.upload

import com.whattoeat.global.exception.InvalidImageFormatException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class ImageUploadService {

    @Value("\${app.upload.path:uploads}")
    private lateinit var uploadPath: String

    @Value("\${app.upload.url-prefix:/uploads/}")
    private lateinit var urlPrefix: String

    @Throws(IOException::class)
    fun upload(file: MultipartFile?): String {
        if (file == null || file.isEmpty) {
            throw InvalidImageFormatException("업로드할 파일이 없습니다.")
        }

        val original = StringUtils.cleanPath(file.originalFilename ?: "")
        val ext = getExtension(original).lowercase()
        if (ext !in ALLOWED_EXTENSIONS) {
            throw InvalidImageFormatException("지원하지 않는 이미지 형식입니다: $ext")
        }

        val dir = Paths.get(uploadPath).toAbsolutePath().normalize()
        Files.createDirectories(dir)

        val stored = "${UUID.randomUUID()}.$ext"
        file.transferTo(dir.resolve(stored))

        val prefix = if (urlPrefix.endsWith("/")) urlPrefix else "$urlPrefix/"
        return prefix + stored
    }

    private fun getExtension(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot == -1) "" else filename.substring(dot + 1)
    }

    companion object {
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }
}
