package com.openchord.server.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts validation failures from admin services into the admin API's {@code 400} error shape.
 *
 * <p>The advice is deliberately scoped to the admin package so GraphQL and media endpoints retain
 * their transport-specific error handling.
 */
@RestControllerAdvice(basePackages = "com.openchord.server.admin")
public class AdminExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AdminController.ErrorView badRequest(IllegalArgumentException exception) {
        return new AdminController.ErrorView(exception.getMessage());
    }
}
