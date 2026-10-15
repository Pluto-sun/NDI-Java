package com.pluto.ndi.shared.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

final class RequestUri {

    private RequestUri() {
    }

    static URI from(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
