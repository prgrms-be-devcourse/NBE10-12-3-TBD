package com.whattoeat.global.upload

import com.whattoeat.global.exception.InvalidImageFormatException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ImageUploadServiceTest {

    @Test
    fun `이미지 저장 전에 실패하면 임시 파일을 남기지 않는다`(@TempDir uploadDir: Path) {
        val service = ImageUploadService()
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString())
        ReflectionTestUtils.setField(service, "urlPrefix", "/uploads/")

        assertThatThrownBy {
            service.upload(MockMultipartFile("image", "broken.jpg", "image/jpeg", "not-an-image".toByteArray()))
        }.isInstanceOf(InvalidImageFormatException::class.java)

        val entries = Files.list(uploadDir)
        val entryCount = try {
            entries.count()
        } finally {
            entries.close()
        }
        assertThat(entryCount).isZero()
    }

    @Test
    fun `일반 업로드는 이미지 비율을 변경하지 않는다`(@TempDir uploadDir: Path) {
        val source = BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB)
        val bytes = uploadDir.resolve("source.jpg").toFile().also {
            ImageIO.write(source, "jpg", it)
        }.readBytes()

        val service = ImageUploadService()
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString())
        ReflectionTestUtils.setField(service, "urlPrefix", "/uploads/")

        val url = service.upload(MockMultipartFile("image", "source.jpg", "image/jpeg", bytes))
        val stored = uploadDir.resolve(url.substringAfterLast('/')).toFile()
        val uploaded = ImageIO.read(stored)

        assertThat(uploaded.width).isEqualTo(4000)
        assertThat(uploaded.height).isEqualTo(3000)
    }

    @Test
    fun `큰 이미지는 비율을 유지해 최대 크기로 축소한다`(@TempDir uploadDir: Path) {
        val source = BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        graphics.color = Color.ORANGE
        graphics.fillRect(0, 0, source.width, source.height)
        graphics.dispose()
        val bytes = uploadDir.resolve("source.jpg").toFile().also {
            ImageIO.write(source, "jpg", it)
        }.readBytes()

        val service = ImageUploadService()
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString())
        ReflectionTestUtils.setField(service, "urlPrefix", "/uploads/")

        val url = service.uploadProfileImage(MockMultipartFile("image", "source.jpg", "image/jpeg", bytes))
        val stored = uploadDir.resolve(url.substringAfterLast('/')).toFile()
        val resized = ImageIO.read(stored)

        assertThat(resized.width).isEqualTo(1024)
        assertThat(resized.height).isEqualTo(1024)
    }
}
