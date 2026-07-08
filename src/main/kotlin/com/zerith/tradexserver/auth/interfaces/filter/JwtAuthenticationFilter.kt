package com.zerith.tradexserver.auth.interfaces.filter

import com.zerith.tradexserver.auth.application.ValidateAccessTokenUseCase
import com.zerith.tradexserver.auth.application.command.ValidateAccessTokenCommand
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.token.TokenSubject
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val validateAccessTokenUseCase: ValidateAccessTokenUseCase
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractBearer(request)

        if (token != null) {
            try {
                val subject = validateAccessTokenUseCase.execute(
                    ValidateAccessTokenCommand.of(token)
                )
                SecurityContextHolder.getContext().authentication = toAuthentication(subject)
            } catch (e: TokenInvalidException) {
                SecurityContextHolder.clearContext()
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }
        }

        filterChain.doFilter(request, response)
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

    private fun toAuthentication(subject: TokenSubject): UsernamePasswordAuthenticationToken {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${subject.role}"))
        return UsernamePasswordAuthenticationToken(
            subject.userId.asString(),
            null,
            authorities
        )
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}