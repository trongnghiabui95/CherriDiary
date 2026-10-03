package com.cherri.diary.api

import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.Instant

class ApiException(val status: HttpStatus, override val message: String) : RuntimeException(message)
fun invalid(message: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, message)
fun conflict(message: String): Nothing = throw ApiException(HttpStatus.CONFLICT, message)
fun missing(message: String): Nothing = throw ApiException(HttpStatus.NOT_FOUND, message)
data class ApiError(val status: Int, val message: String, val timestamp: Instant = Instant.now())

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException) = ResponseEntity.status(e.status).body(ApiError(e.status.value(), e.message))
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException) = ResponseEntity.badRequest().body(
        ApiError(400, e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }))
    @ExceptionHandler(HttpMessageNotReadableException::class, ConstraintViolationException::class)
    fun malformed() = ResponseEntity.badRequest().body(ApiError(400, "Dữ liệu yêu cầu không hợp lệ"))
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun duplicate() = ResponseEntity.status(409).body(ApiError(409, "Dữ liệu trùng hoặc vi phạm ràng buộc. Kiểm tra và gửi lại cùng requestId."))
    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun lock() = ResponseEntity.status(409).body(ApiError(409, "Đơn đang được xử lý. Gửi lại cùng requestId."))
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun upload() = ResponseEntity.status(413).body(ApiError(413, "Ảnh tối đa 5 MB"))
    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden() = ResponseEntity.status(403).body(ApiError(403, "Không có quyền thực hiện"))
}
