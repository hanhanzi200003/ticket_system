package com.example.ticket_system.config.exception;

import com.example.ticket_system.config.utils.Result;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    //全局异常返回500
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        return Result.error(e.getMessage());
    }
    //异常返回
    @ExceptionHandler(AllException.class)
    public Result<?> handleAllException(AllException e){
        return Result.error(e.getCode(),e.getMessage());
    }
    //登录数据异常返回
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e){
        //获取结果对象
        BindingResult bindingResult = e.getBindingResult();
        //获取所有字段错误列表
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        //提取第一个错误信息
        String message = "参数校验失败！";
        if (!fieldErrors.isEmpty()){
            message = fieldErrors.get(0).getDefaultMessage();
        }
        return Result.error(400,e.getMessage());
    }
}
