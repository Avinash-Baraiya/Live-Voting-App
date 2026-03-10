package com.livevoting.exception;

public class InvalidOptionException extends RuntimeException {

    public InvalidOptionException(Long optionId, Long pollId) {
        super("Option " + optionId + " does not belong to poll " + pollId);
    }
}
