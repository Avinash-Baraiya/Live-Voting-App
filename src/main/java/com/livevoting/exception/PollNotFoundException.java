package com.livevoting.exception;

public class PollNotFoundException extends RuntimeException {

    public PollNotFoundException(Long pollId) {
        super("Poll not found with id: " + pollId);
    }
}
