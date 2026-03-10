package com.livevoting.exception;

public class PollNotActiveException extends RuntimeException {

    public PollNotActiveException(Long pollId) {
        super("Poll is not active: " + pollId);
    }
}
