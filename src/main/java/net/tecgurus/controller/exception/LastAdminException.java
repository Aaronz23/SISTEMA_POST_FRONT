package net.tecgurus.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class LastAdminException extends RuntimeException {
    public LastAdminException(String message) {
        super(message);
    }
}
