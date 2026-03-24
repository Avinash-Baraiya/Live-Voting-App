package com.avi.voting.exception;

public class PollNotActiveException extends RuntimeException {
    public PollNotActiveException(String message) {
        super(message);
    }
}
