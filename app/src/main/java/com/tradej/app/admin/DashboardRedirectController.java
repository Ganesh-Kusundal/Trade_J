package com.tradej.app.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects the application root ({@code /}) and console directory paths
 * to the operator console SPA ({@code /console/index.html}) served from
 * {@code classpath:/static/console/}.
 */
@Controller
public class DashboardRedirectController {

    @GetMapping({"/", "/console", "/console/"})
    public String redirectToDashboard() {
        return "redirect:/console/index.html";
    }
}
