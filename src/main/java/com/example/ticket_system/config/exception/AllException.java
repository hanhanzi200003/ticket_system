package com.example.ticket_system.config.exception;

import lombok.Getter;

@Getter
public class AllException extends RuntimeException {
    private int code;
    public AllException(int code, String message){
        super (message);
        this.code = code;
    }
}
