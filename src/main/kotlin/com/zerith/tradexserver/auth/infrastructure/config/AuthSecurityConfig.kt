package com.zerith.tradexserver.auth.infrastructure.config

import com.zerith.tradexserver.auth.interfaces.filter.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class AuthSecurityConfig {

    @Bean
    fun authSecurityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter
    ): SecurityFilterChain {
        http
            .csrf {
                it.disable()
            }
            .cors {
            }
            .formLogin {
                it.disable()
            }
            .httpBasic {
                it.disable()
            }
            .logout {
                it.disable()
            }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/registration").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/sign-in").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/reissue").permitAll()
                it.requestMatchers("/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                }
            }

        return http.build()
    }
}