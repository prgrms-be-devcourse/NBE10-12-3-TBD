package com.whattoeat.global.exception

import com.whattoeat.global.response.ErrorResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(e: UserNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(FeedNotFoundException::class)
    fun handleFeedNotFound(e: FeedNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(CommentNotFoundException::class)
    fun handleCommentNotFound(e: CommentNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(RestaurantNotFoundException::class)
    fun handleRestaurantNotFound(e: RestaurantNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(ListNotFoundException::class)
    fun handleListNotFound(e: ListNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(NotificationNotFoundException::class)
    fun handleNotificationNotFound(e: NotificationNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .firstOrNull()
            ?.let { "${it.field}: ${it.defaultMessage}" }
            ?: "입력값이 올바르지 않습니다."
        return badRequest(message)
    }

    @ExceptionHandler(FollowNotFoundException::class)
    fun handleFollowNotFound(e: FollowNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(FeedLikeNotFoundException::class)
    fun handleFeedLikeNotFound(e: FeedLikeNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(AlreadyFollowingException::class)
    fun handleAlreadyFollowing(e: AlreadyFollowingException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(AlreadyLikedFeedException::class)
    fun handleAlreadyLikedFeed(e: AlreadyLikedFeedException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(SelfFollowNotAllowedException::class)
    fun handleSelfFollowNotAllowed(e: SelfFollowNotAllowedException): ResponseEntity<ErrorResponse> =
        badRequest(e.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        badRequest("요청 본문을 읽을 수 없습니다.")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        badRequest("'${e.name}'의 값이 올바르지 않습니다: ${e.value}")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        badRequest("'${e.parameterName}' 파라미터가 필요합니다.")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.CONFLICT, "데이터 무결성 오류가 발생했습니다.")

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> =
        status(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.")

    @ExceptionHandler(DuplicateLoginIdException::class)
    fun handleDuplicateLoginId(e: DuplicateLoginIdException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(e: InvalidCredentialsException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.UNAUTHORIZED, e.message)

    @ExceptionHandler(DuplicateNicknameException::class)
    fun handleDuplicateNickname(e: DuplicateNicknameException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(e: DuplicateEmailException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(PasswordMismatchException::class)
    fun handlePasswordMismatch(e: PasswordMismatchException): ResponseEntity<ErrorResponse> =
        badRequest(e.message)

    @ExceptionHandler(KakaoApiException::class)
    fun handleKakaoApiException(e: KakaoApiException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.BAD_GATEWAY, e.message)

    @ExceptionHandler(RestaurantListItemNotFoundException::class)
    fun handleRestaurantListItemNotFound(e: RestaurantListItemNotFoundException): ResponseEntity<ErrorResponse> =
        notFound(e.message)

    @ExceptionHandler(DuplicateRestaurantListItemException::class)
    fun handleDuplicateRestaurantListItem(e: DuplicateRestaurantListItemException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(AlreadySavedRestaurantListException::class)
    fun handleAlreadySavedRestaurantList(e: AlreadySavedRestaurantListException): ResponseEntity<ErrorResponse> =
        conflict(e.message)

    @ExceptionHandler(InvalidImageFormatException::class)
    fun handleInvalidImageFormat(e: InvalidImageFormatException): ResponseEntity<ErrorResponse> =
        badRequest(e.message)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        status(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 파일은 10MB 이하로 업로드할 수 있습니다.")

    @ExceptionHandler(InvalidRecommendParameterException::class)
    fun handleInvalidRecommendParameter(e: InvalidRecommendParameterException): ResponseEntity<ErrorResponse> =
        badRequest(e.message)
    
    private fun notFound(message: String?): ResponseEntity<ErrorResponse> =
        status(HttpStatus.NOT_FOUND, message)

    private fun badRequest(message: String?): ResponseEntity<ErrorResponse> =
        status(HttpStatus.BAD_REQUEST, message)

    private fun conflict(message: String?): ResponseEntity<ErrorResponse> =
        status(HttpStatus.CONFLICT, message)

    private fun status(status: HttpStatus, message: String?): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse.of(status, message ?: ""))
}
