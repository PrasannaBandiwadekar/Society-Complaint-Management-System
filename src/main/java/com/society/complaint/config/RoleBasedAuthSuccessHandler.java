package com.society.complaint.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * After successful login, sends ADMIN, RESIDENT, and WORKER users
 * to their own dashboard rather than one generic home page.
 */
public class RoleBasedAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        String redirectUrl = "/login";

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.equals("ROLE_ADMIN")) {
                redirectUrl = "/admin/dashboard";
                break;
            } else if (role.equals("ROLE_RESIDENT")) {
                redirectUrl = "/resident/dashboard";
                break;
            } else if (role.equals("ROLE_WORKER")) {
                redirectUrl = "/worker/dashboard";
                break;
            }
        }

        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}
