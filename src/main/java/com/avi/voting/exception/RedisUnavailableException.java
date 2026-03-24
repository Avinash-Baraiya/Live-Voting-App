package com.avi.voting.exception;

public class RedisUnavailableException extends RuntimeException {
    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
