package io.tradex.auth.infra

import io.tradex.auth.domain.TokenInvalidException
import io.tradex.auth.port.IssuedToken
import io.tradex.auth.port.TokenIssuer
import io.tradex.auth.port.TokenPair
import io.tradex.auth.port.TokenSubject
import io.tradex.auth.port.TokenVerifier
import io.tradex.auth.port.VerifiedToken
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tradex.auth.jwt")
data class JwtProperties(
    val issuer: String = "tradex",
    val accessTtl: Duration = Duration.ofMinutes(15),
    val refreshTtl: Duration = Duration.ofDays(14),
    val keyId: String = "tradex-rs256",
    /** PEM 파일 경로. 없으면 기동 시 임시 키쌍 생성 (데모/테스트용, DECISIONS.md D21). */
    val privateKeyPath: String = "secrets/jwt-private.pem",
    val publicKeyPath: String = "secrets/jwt-public.pem",
)

/** 레거시 JwtKeyLoader 대체: PEM(PKCS8/X509) 로드, 파일이 없으면 임시 RSA 2048 생성. */
object JwtKeys {
    private val log = LoggerFactory.getLogger(JwtKeys::class.java)

    fun load(props: JwtProperties): KeyPair {
        val privatePath = Path.of(props.privateKeyPath)
        val publicPath = Path.of(props.publicKeyPath)
        if (Files.exists(privatePath) && Files.exists(publicPath)) {
            val keyFactory = KeyFactory.getInstance("RSA")
            val private = keyFactory.generatePrivate(PKCS8EncodedKeySpec(readPem(privatePath)))
            val public = keyFactory.generatePublic(X509EncodedKeySpec(readPem(publicPath)))
            return KeyPair(public, private)
        }
        log.warn("JWT PEM 키({}, {})가 없어 임시 키쌍을 생성합니다 — 재기동 시 기존 토큰은 전부 무효화됩니다", props.privateKeyPath, props.publicKeyPath)
        return KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private fun readPem(path: Path): ByteArray {
        val body = Files.readString(path)
            .replace(Regex("-----(BEGIN|END)[A-Z ]*-----"), "")
            .replace(Regex("\\s"), "")
        return Base64.getDecoder().decode(body)
    }
}

private enum class TokenType(val claim: String) { ACCESS("access"), REFRESH("refresh") }

class JwtTokenAdapter(
    private val props: JwtProperties,
    private val clock: Clock,
    keyPair: KeyPair = JwtKeys.load(props),
) : TokenIssuer, TokenVerifier {
    private val privateKey = keyPair.private
    private val publicKey = keyPair.public

    override fun issue(subject: TokenSubject): TokenPair {
        val now = clock.instant()
        return TokenPair(
            accessToken = build(subject, now, props.accessTtl, TokenType.ACCESS),
            refreshToken = build(subject, now, props.refreshTtl, TokenType.REFRESH),
        )
    }

    private fun build(subject: TokenSubject, now: Instant, ttl: Duration, type: TokenType): IssuedToken {
        val jti = UUID.randomUUID().toString()
        val expiresAt = now.plus(ttl)
        val value = Jwts.builder()
            .header().keyId(props.keyId).and()
            .issuer(props.issuer)
            .subject(subject.userId)
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(CLAIM_ROLE, subject.role)
            .claim(CLAIM_TYPE, type.claim)
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
        return IssuedToken(value, jti, expiresAt)
    }

    override fun verifyAccess(rawToken: String): VerifiedToken = verify(rawToken, TokenType.ACCESS)

    override fun verifyRefresh(rawToken: String): VerifiedToken = verify(rawToken, TokenType.REFRESH)

    private fun verify(rawToken: String, expected: TokenType): VerifiedToken {
        val claims: Claims = try {
            Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(props.issuer)
                .clock { Date.from(clock.instant()) }
                .build()
                .parseSignedClaims(rawToken)
                .payload
        } catch (e: JwtException) {
            throw TokenInvalidException()
        } catch (e: IllegalArgumentException) {
            throw TokenInvalidException()
        }
        if (claims[CLAIM_TYPE] != expected.claim) throw TokenInvalidException()
        val subject = TokenSubject(
            userId = claims.subject ?: throw TokenInvalidException(),
            role = claims[CLAIM_ROLE] as? String ?: throw TokenInvalidException(),
        )
        return VerifiedToken(
            subject = subject,
            jti = claims.id ?: throw TokenInvalidException(),
            expiresAt = claims.expiration?.toInstant() ?: throw TokenInvalidException(),
        )
    }

    companion object {
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_TYPE = "typ"
    }
}
