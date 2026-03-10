package com.livevoting.exception;

public class DuplicateVoteException extends RuntimeException {

    public DuplicateVoteException(Long userId, Long pollId) {
        super("User " + userId + " has already voted on poll " + pollId);
    }
}
