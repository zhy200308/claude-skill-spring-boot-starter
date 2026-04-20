package com.claude.skill.web;

import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.model.ApiResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Starter 全局异常处理
 * 集成方如果有自己的 @RestControllerAdvice，可将此类排除或覆盖
 */
@RestControllerAdvice
@ConditionalOnWebApplication
public class ClaudeExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNotFound(NoSuchElementException ex) {
        return ApiResult.fail("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ClaudeClient.ClaudeApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResult<Void> handleClaudeApi(ClaudeClient.ClaudeApiException ex) {
        return ApiResult.fail("CLAUDE_API_ERROR",
            "Claude API returned " + ex.getStatusCode() + ": " + ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBadRequest(IllegalArgumentException ex) {
        return ApiResult.fail("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleGeneral(Exception ex) {
        return ApiResult.fail("INTERNAL_ERROR", ex.getMessage());
    }
}
