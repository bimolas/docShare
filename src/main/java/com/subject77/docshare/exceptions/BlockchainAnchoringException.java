package com.subject77.docshare.exceptions;

public class BlockchainAnchoringException extends RuntimeException {
    public BlockchainAnchoringException(String message) {
        super(message);
    }

    public BlockchainAnchoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
