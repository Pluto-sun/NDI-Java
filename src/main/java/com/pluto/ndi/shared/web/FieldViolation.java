package com.pluto.ndi.shared.web;

import java.util.Objects;

public record FieldViolation(String field, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
