package io.tradex.auth.api

import io.tradex.auth.application.AuthService
import io.tradex.auth.domain.TokenInvalidException
import io.tradex.auth.port.TokenPair
import io.tradex.common.BaseResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Clock
import java.time.Duration
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SignInRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class TokenResponse(val accessToken: String, val expiresIn: Long)
data class MeResponse(val userId: String, val role: String)

object AuthCookies {
    const val REFRESH_COOKIE_NAME = "refresh_token"
    private const val REFRESH_COOKIE_PATH = "/api/v1/auth"

    fun writeRefresh(response: HttpServletResponse, value: String, ttl: Duration) {
        response.addCookie(
            Cookie(REFRESH_COOKIE_NAME, value).apply {
                isHttpOnly = true
                secure = true
                path = REFRESH_COOKIE_PATH
                maxAge = ttl.seconds.toInt()
                setAttribute("SameSite", "Strict")
            },
        )
    }

    fun expireRefresh(response: HttpServletResponse) {
        response.addCookie(
            Cookie(REFRESH_COOKIE_NAME, "").apply {
                isHttpOnly = true
                secure = true
                path = REFRESH_COOKIE_PATH
                maxAge = 0
                setAttribute("SameSite", "Strict")
            },
        )
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val clock: Clock,
) {
    @PostMapping("/sign-in")
    fun signIn(@Valid @RequestBody request: SignInRequest, response: HttpServletResponse): BaseResponse<TokenResponse> =
        BaseResponse.ok(writeTokens(authService.signIn(request.email, request.password), response))

    @PostMapping("/reissue")
    fun reissue(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): BaseResponse<TokenResponse> {
        val raw = refreshToken ?: throw TokenInvalidException()
        return BaseResponse.ok(writeTokens(authService.reissue(raw), response))
    }

    @PostMapping("/sign-out")
    fun signOut(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false) refreshToken: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): BaseResponse<Unit> {
        val refresh = refreshToken ?: throw TokenInvalidException()
        val access = extractBearer(request) ?: throw TokenInvalidException()
        authService.signOut(access, refresh)
        AuthCookies.expireRefresh(response)
        return BaseResponse.ok()
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): BaseResponse<MeResponse> {
        val access = extractBearer(request) ?: throw TokenInvalidException()
        val subject = authService.validateAccess(access)
        return BaseResponse.ok(MeResponse(subject.userId, subject.role))
    }

    private fun writeTokens(tokenPair: TokenPair, response: HttpServletResponse): TokenResponse {
        val now = clock.instant()
        AuthCookies.writeRefresh(
            response,
            tokenPair.refreshToken.value,
            Duration.between(now, tokenPair.refreshToken.expiresAt),
        )
        return TokenResponse(
            accessToken = tokenPair.accessToken.value,
            expiresIn = Duration.between(now, tokenPair.accessToken.expiresAt).seconds,
        )
    }

    private fun extractBearer(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.substring(BEARER_PREFIX.length).trim().ifBlank { null }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
