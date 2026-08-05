package com.whattoeat.global.upload

import com.whattoeat.global.exception.InvalidImageFormatException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.min

@Service
class ImageUploadService {

    @Value("\${app.upload.path:uploads}")
    private lateinit var uploadPath: String

    @Value("\${app.upload.url-prefix:/uploads/}")
    private lateinit var urlPrefix: String

    @Throws(IOException::class)
    fun upload(file: MultipartFile?): String {
        return upload(file, squareCrop = false)
    }

    @Throws(IOException::class)
    fun uploadProfileImage(file: MultipartFile?): String {
        return upload(file, squareCrop = true)
    }

    private fun upload(file: MultipartFile?, squareCrop: Boolean): String {
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
        val target = dir.resolve(stored)
        val temporary = Files.createTempFile(dir, ".upload-", ".$ext")

        try {
            val image = file.inputStream.use { ImageIO.read(it) }
                ?: throw InvalidImageFormatException("이미지 파일을 읽을 수 없습니다.")

            val normalized = normalizeImage(image, squareCrop)
            if (normalized === image) {
                file.inputStream.use { input ->
                    Files.newOutputStream(temporary).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!ImageIO.write(normalized, ext, output)) {
                        throw InvalidImageFormatException("지원하지 않는 이미지 형식입니다: $ext")
                    }
                    output.toByteArray()
                }
                Files.write(temporary, bytes)
                normalized.flush()
            }

            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(target) }
            throw e
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }

        val prefix = if (urlPrefix.endsWith("/")) urlPrefix else "$urlPrefix/"
        return prefix + stored
    }

    private fun getExtension(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot == -1) "" else filename.substring(dot + 1)
    }

    private fun normalizeImage(image: BufferedImage, squareCrop: Boolean): BufferedImage {
        if (!squareCrop) {
            return image
        }

        val squareSize = min(image.width, image.height)
        val targetSize = min(MAX_DIMENSION, squareSize)
        if (image.width == image.height && image.width <= MAX_DIMENSION) {
            return image
        }

        val sourceX = (image.width - squareSize) / 2
        val sourceY = (image.height - squareSize) / 2
        val type = if (image.colorModel.hasAlpha()) {
            BufferedImage.TYPE_INT_ARGB
        } else {
            BufferedImage.TYPE_INT_RGB
        }
        val normalized = BufferedImage(targetSize, targetSize, type)
        val graphics = normalized.createGraphics()
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        graphics.drawImage(
            image,
            0,
            0,
            targetSize,
            targetSize,
            sourceX,
            sourceY,
            sourceX + squareSize,
            sourceY + squareSize,
            null,
        )
        graphics.dispose()
        return normalized
    }

    companion object {
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
        private const val MAX_DIMENSION = 1024
    }
}
