package com.subject77.docshare.exceptions;

public class PermissionTamperedException extends RuntimeException {
    public PermissionTamperedException(String message) {
        super(message);
    }
}
