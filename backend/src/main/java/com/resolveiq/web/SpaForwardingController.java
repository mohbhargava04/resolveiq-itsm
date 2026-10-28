package com.resolveiq.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page app. Browser routes such as {@code /incidents/INC-1042} have no file on
 * the server, so they are forwarded to {@code index.html} and the client-side router takes over.
 * Only the app's own routes are listed, so static assets and the API are never intercepted.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/register", "/report", "/queue", "/services", "/reports",
            "/incidents", "/incidents/{number}", "/admin/{page}"})
    public String forward() {
        return "forward:/index.html";
    }
}
