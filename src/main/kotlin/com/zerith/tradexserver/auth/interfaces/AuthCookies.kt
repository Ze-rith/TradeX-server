package com.zerith.tradexserver.auth.interfaces

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration

object AuthCookies {

    const val REFRESH_COOKIE_NAME = "refresh_token"
    private const val REFRESH_COOKIE_PATH = "/api/v1/auth"

    fun writeRefresh(
        response: HttpServletResponse,
        value: String,
        ttl: Duration
    ) {
        val cookie = Cookie(REFRESH_COOKIE_NAME, value)
        cookie.isHttpOnly = true
        cookie.secure = true
        cookie.path = REFRESH_COOKIE_PATH
        cookie.maxAge = ttl.seconds.toInt()
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }

    fun expireRefresh(response: HttpServletResponse) {
        val cookie = Cookie(REFRESH_COOKIE_NAME, "")
        cookie.isHttpOnly = true
        cookie.secure = true
        cookie.path = REFRESH_COOKIE_PATH
        cookie.maxAge = 0
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }
}