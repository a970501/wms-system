package com.wms.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各类异常并返回标准化错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数验证异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        logger.warn("参数验证失败 [{}]: {}", request.getRequestURI(), errors);
        return Result.error("参数验证失败: " + errors);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBindException(BindException e, HttpServletRequest request) {
        String errors = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        logger.warn("参数绑定失败 [{}]: {}", request.getRequestURI(), errors);
        return Result.error("参数错误: " + errors);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String errors = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        logger.warn("约束验证失败 [{}]: {}", request.getRequestURI(), errors);
        return Result.error("参数验证失败: " + errors);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMissingParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        logger.warn("缺少请求参数 [{}]: {}", request.getRequestURI(), e.getParameterName());
        return Result.error("缺少必需参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logger.warn("参数类型错误 [{}]: {} 应为 {}", request.getRequestURI(), e.getName(), e.getRequiredType());
        return Result.error("参数类型错误: " + e.getName());
    }

    /**
     * 处理JSON解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleJsonParseException(HttpMessageNotReadableException e, HttpServletRequest request) {
        logger.warn("JSON解析失败 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error("请求数据格式错误");
    }

    /**
     * 处理HTTP方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<String> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.warn("HTTP方法不支持 [{}]: {}", request.getRequestURI(), e.getMethod());
        return Result.error("不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理数据库异常
     */
    @ExceptionHandler({SQLException.class, DataAccessException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleDatabaseException(Exception e, HttpServletRequest request) {
        logger.error("数据库操作失败 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error("数据库操作失败，请稍后重试");
    }

    /**
     * 处理业务异常（自定义）
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logger.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logger.error("运行时异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error("系统内部错误: " + e.getMessage());
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        logger.error("未知异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error("系统错误，请联系管理员");
    }
}
