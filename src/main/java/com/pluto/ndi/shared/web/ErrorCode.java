package com.pluto.ndi.shared.web;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus status();

    String code();

    String title();

    String message();
}
