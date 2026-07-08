package com.zerith.tradexserver.auth.interfaces

import com.zerith.tradexserver.auth.application.ReissueTokenUseCase
import com.zerith.tradexserver.auth.application.SignInUseCase
import com.zerith.tradexserver.auth.application.SignOutUseCase
import com.zerith.tradexserver.auth.application.command.ReissueTokenCommand
import com.zerith.tradexserver.auth.application.command.SignInCommand
import com.zerith.tradexserver.auth.application.command.SignOutCommand
import com.zerith.tradexserver.auth.data.ReissueTokenResponse
import com.zerith.tradexserver.auth.data.SignInRequest
import com.zerith.tradexserver.auth.data.SignInResponse
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.common.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val signInUseCase: SignInUseCase,
    private val reissueTokenUseCase: ReissueTokenUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val clock: Clock
) {

    @PostMapping("/sign-in")
    fun signIn(
        @Valid
        @RequestBody
        request: SignInRequest,
        response: HttpServletResponse
    ): BaseResponse<SignInResponse> {
        val tokenPair = signInUseCase.execute(
            SignInCommand.of(request.email, request.password)
        )

        val refreshTtl = Duration.between(clock.instant(), tokenPair.refreshToken.expiresAt)
        AuthCookies.writeRefresh(response, tokenPair.refreshToken.value, refreshTtl)

        val accessExpiresIn = Duration.between(clock.instant(), tokenPair.accessToken.expiresAt).seconds

        return BaseResponse.ok(
            SignInResponse.of(tokenPair.accessToken.value, accessExpiresIn)
        )
    }

    @PostMapping("/reissue")
    fun reissue(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false)
        refreshToken: String?,
        response: HttpServletResponse
    ): BaseResponse<ReissueTokenResponse> {
        val raw = refreshToken ?: throw TokenInvalidException()

        val tokenPair = reissueTokenUseCase.execute(
            ReissueTokenCommand.of(raw)
        )

        val refreshTtl = Duration.between(clock.instant(), tokenPair.refreshToken.expiresAt)
        AuthCookies.writeRefresh(response, tokenPair.refreshToken.value, refreshTtl)

        val accessExpiresIn = Duration.between(clock.instant(), tokenPair.accessToken.expiresAt).seconds

        return BaseResponse.ok(
            ReissueTokenResponse.of(tokenPair.accessToken.value, accessExpiresIn)
        )
    }

    @PostMapping("/sign-out")
    fun signOut(
        @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false)
        refreshToken: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): BaseResponse<Unit> {
        val refresh = refreshToken ?: throw TokenInvalidException()
        val access = extractBearer(request) ?: throw TokenInvalidException()

        signOutUseCase.execute(
            SignOutCommand.of(access, refresh)
        )

        AuthCookies.expireRefresh(response)
        return BaseResponse.ok()
    }

    private fun extractBearer(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) {
            return null
        }
        return header.substring(BEARER_PREFIX.length).trim().ifBlank {
            null
        }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}