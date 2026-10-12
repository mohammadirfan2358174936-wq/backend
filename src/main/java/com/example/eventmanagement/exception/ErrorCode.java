package com.example.eventmanagement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The canonical error-code vocabulary.
 *
 * Every controller failure is expressed as one of these, and every one of them
 * has a fixed HTTP status and user-facing message. Keeping both here means a
 * code can never be added without deciding what it looks like to the client.
 */
@Getter
public enum ErrorCode {

    /* ------------------------------------------------------------ 400 */
    VALIDATION_ERROR("The submitted data is not valid.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("The request could not be processed.", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE("The event must end after it starts.", HttpStatus.BAD_REQUEST),
    INVALID_TICKET_CONFIGURATION("The ticket configuration is invalid.", HttpStatus.BAD_REQUEST),

    /* ------------------------------------------------------------ 401 */
    UNAUTHORIZED("Authentication is required to access this resource.", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS("The email or password you entered is incorrect.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("Your session has expired. Please sign in again.", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("The provided authentication token is not valid.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("Too many failed sign-in attempts. Please try again later.", HttpStatus.LOCKED),

    /* ------------------------------------------------------------ 403 */
    FORBIDDEN("You do not have permission to perform this action.", HttpStatus.FORBIDDEN),
    NOT_EVENT_OWNER("Only the event organiser can perform this action.", HttpStatus.FORBIDDEN),

    /* ------------------------------------------------------------ 404 */
    EVENT_NOT_FOUND("The requested event does not exist.", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("The requested user does not exist.", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND("The requested category does not exist.", HttpStatus.NOT_FOUND),
    REGISTRATION_NOT_FOUND("You do not have a booking for this event.", HttpStatus.NOT_FOUND),
    TICKET_NOT_FOUND("The requested ticket does not exist.", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND("The requested resource was not found.", HttpStatus.NOT_FOUND),

    /* ------------------------------------------------------------ 409 */
    EMAIL_ALREADY_EXISTS("An account with this email already exists.", HttpStatus.CONFLICT),
    DUPLICATE_REGISTRATION("You have already registered for this event.", HttpStatus.CONFLICT),
    EVENT_FULL("This event is fully booked.", HttpStatus.CONFLICT),
    INVALID_EVENT_STATE("This action is not allowed for the event's current status.", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("Someone else changed this record first. Please reload and retry.", HttpStatus.CONFLICT),
    SLUG_ALREADY_EXISTS("An event with this title already exists.", HttpStatus.CONFLICT),
    NOTHING_TO_UNDO("There is no recent action left to undo.", HttpStatus.CONFLICT),
    CONFLICT("This change conflicts with an existing record.", HttpStatus.CONFLICT),
    TICKET_ALREADY_USED("This ticket has already been redeemed.", HttpStatus.CONFLICT),

    /* ------------------------------------------------------------ 500 */
    INTERNAL_ERROR("Something went wrong on our side. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
