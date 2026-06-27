package com.society.complaint.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Used only by AdminController's "create new admin" form, which is reachable
 * solely by an already-authenticated admin (/admin/** requires ROLE_ADMIN).
 * Deliberately separate from RegisterDto: an admin account has no role
 * dropdown (it's always ADMIN), no flat number, and no specialization, so
 * reusing RegisterDto would mean either fighting its @NotBlank role validation
 * or quietly allowing role spoofing - a dedicated DTO avoids both problems.
 */
@Data
public class AdminCreateDto {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
