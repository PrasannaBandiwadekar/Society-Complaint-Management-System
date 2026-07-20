package com.society.complaint.api;

import com.society.complaint.config.CustomUserDetailsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST login endpoint.
 *
 * Usage (e.g. via Postman or curl):
 *   POST /api/auth/login
 *   Content-Type: application/json
 *   Body: { "email": "admin@society.com", "password": "Admin@123" }
 *
 * Response:
 *   { "token": "eyJhbGci...", "email": "admin@society.com", "role": "ADMIN" }
 *
 * Use the returned token in subsequent API requests:
 *   GET /api/complaints
 *   Authorization: Bearer eyJhbGci...
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            // Verify email + password using Spring Security's auth manager
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(request.getEmail());
            String token = jwtService.generateToken(userDetails);

            return ResponseEntity.ok(new LoginResponse(
                    token,
                    request.getEmail(),
                    userDetails.getAuthorities().iterator().next()
                            .getAuthority().replace("ROLE_", "")
            ));

        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        }
    }

    // ---- Request / Response DTOs ----

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    record LoginResponse(String token, String email, String role) {}
    record ErrorResponse(String message) {}
}
