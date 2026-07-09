package io.chronos.example.order.api

import io.chronos.cell.CellDownException
import io.chronos.cell.MigrationInProgressException
import io.chronos.core.store.OptimisticConcurrencyException
import io.chronos.example.order.OrderNotFoundException
import io.chronos.router.InvalidSessionTokenException
import io.chronos.router.ProjectionLagTimeoutException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiErrorHandler {
    /** RYW 대기 타임아웃 → 명세대로 503 + Retry-After. */
    @ExceptionHandler(ProjectionLagTimeoutException::class)
    fun projectionLag(e: ProjectionLagTimeoutException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", e.retryAfter.inWholeSeconds.coerceAtLeast(1).toString())
            .body(mapOf("error" to e.message))

    @ExceptionHandler(OrderNotFoundException::class)
    fun notFound(e: OrderNotFoundException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))

    @ExceptionHandler(OptimisticConcurrencyException::class, MigrationInProgressException::class)
    fun conflict(e: RuntimeException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))

    @ExceptionHandler(CellDownException::class)
    fun cellDown(e: CellDownException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class, InvalidSessionTokenException::class)
    fun badRequest(e: RuntimeException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
}
