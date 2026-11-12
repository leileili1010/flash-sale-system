package com.flashsale.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of every error
 * response. Clients branch on these values rather than on HTTP status or message text.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request is invalid."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this resource."),

    DROP_NOT_STARTED(HttpStatus.CONFLICT, "This drop has not started yet."),
    DROP_ENDED(HttpStatus.CONFLICT, "This drop has ended."),
    SOLD_OUT(HttpStatus.CONFLICT, "This item is sold out."),
    ALREADY_PURCHASED(HttpStatus.CONFLICT, "You have already purchased this item."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "The payment window for this order has expired."),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, "The order cannot be updated in its current state."),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again shortly."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
