package com.openchord.server.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.openchord.server.admin")
public class AdminExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AdminController.ErrorView badRequest(IllegalArgumentException exception) {
        return new AdminController.ErrorView(exception.getMessage());
    }
}
