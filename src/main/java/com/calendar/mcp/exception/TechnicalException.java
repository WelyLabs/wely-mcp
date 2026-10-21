package com.calendar.mcp.exception;

/** A failure of something this service depends on, not of the caller's request. */
public class TechnicalException extends RuntimeException {

    private final TechnicalErrorCode errorCode;

    public TechnicalException(TechnicalErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public TechnicalException(TechnicalErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public TechnicalErrorCode getErrorCode() {
        return errorCode;
    }
}
