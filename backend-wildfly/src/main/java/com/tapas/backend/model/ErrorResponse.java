package com.tapas.backend.model;

import java.util.List;

public class ErrorResponse {
    public List<String> errors;

    public ErrorResponse() {
    }

    public ErrorResponse(List<String> errors) {
        this.errors = errors;
    }
}
