package com.example.ticket_system.config.utils;

import lombok.Data;
import lombok.NoArgsConstructor;

//这个函数是规范操作状态码和定义返回信息
@Data
@NoArgsConstructor
public class Result <T> {
    private int code;
    private String message;
    private T data;

    public Result(int code, String message, T data){
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public static <T> Result<T> success(){
        return new Result<>(200,"操作成功！",null);
    }
    public static <T> Result<T> success(String message){
        return new Result<>(200,message,null);
    }
    public static <T>Result<T> success(String message,T data){
        return new Result<>(200,message,data);
    }
    public static <T>Result<T> success(T data){
        return new Result<>(200,"操作成功！",data);
    }
    public static <T> Result<T> error(String message){
        return new Result<>(500,message,null);
    }
    public static <T> Result<T> error(int code, String message){
        return new Result<>(code,message,null);
    }
}
