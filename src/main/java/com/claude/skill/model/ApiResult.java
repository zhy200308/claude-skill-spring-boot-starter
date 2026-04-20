package com.claude.skill.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应包装
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        ApiResult<T> r = new ApiResult<>();
        r.success = true;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> fail(String message) {
        ApiResult<T> r = new ApiResult<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public static <T> ApiResult<T> fail(String errorCode, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.success = false;
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getErrorCode() { return errorCode; }
}

