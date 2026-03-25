package com.avi.voting.exception;

public class PollExpiredException extends RuntimeException {
    public PollExpiredException(String message) {
        super(message);
    }
}
