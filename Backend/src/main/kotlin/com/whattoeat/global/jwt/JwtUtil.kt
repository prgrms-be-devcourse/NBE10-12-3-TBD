package com.whattoeat.global.jwt

import com.whattoeat.domain.user.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil {

    private lateinit var key: SecretKey

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    @Value("\${jwt.access-expiration}")
    private var accessExpiration: Long = 0

    @Value("\${jwt.refresh-expiration}")
    private var refreshExpiration: Long = 0

    @PostConstruct
    fun init() {
        val keyBytes = Decoders.BASE64.decode(secret)
        key = Keys.hmacShaKeyFor(keyBytes)
    }

    fun generateAccessToken(user: User): String =
        Jwts.builder()
            .subject(user.id.toString())
            .claim("loginId", user.loginId)
            .claim("role", user.role.name)
            .claim("tokenType", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessExpiration))
            .signWith(key)
            .compact()

    fun generateRefreshToken(user: User): String =
        Jwts.builder()
            .subject(user.id.toString())
            .claim("tokenType", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(key)
            .compact()

    fun parseToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    fun getUserId(token: String): Long = parseToken(token).subject.toLong()

    fun getRole(token: String): String = parseToken(token).get("role", String::class.java)

    fun getRemainingExpiration(token: String): Long =
        parseToken(token).expiration.time - System.currentTimeMillis()

    // userId와 남은 만료 시간을 동시에 써야 하는 호출부(로그아웃 등)가 getUserId/
    // getRemainingExpiration을 따로 부르면 같은 토큰을 두 번 파싱하게 되므로, 한 번만
    // 파싱해서 둘 다 얻을 수 있게 묶어서 제공한다.
    fun getTokenInfo(token: String): TokenInfo {
        val claims = parseToken(token)
        return TokenInfo(claims.subject.toLong(), claims.expiration.time - System.currentTimeMillis())
    }

    data class TokenInfo(val userId: Long, val remainingMillis: Long)
}
