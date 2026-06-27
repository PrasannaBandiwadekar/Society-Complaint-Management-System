package com.society.complaint.controller;

import com.society.complaint.dto.RegisterDto;
import com.society.complaint.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerDto", new RegisterDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registerDto") RegisterDto registerDto,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        // SECURITY: public self-registration must never be allowed to create an ADMIN
        // account. The dropdown on the register page intentionally only offers
        // RESIDENT/WORKER, but a malicious user could still POST role=ADMIN directly
        // (e.g. via curl or browser dev tools), so this is enforced again here on
        // the server, not just hidden in the UI. The first/default admin is seeded
        // by DataSeeder on startup; further admins are created by an existing admin
        // via /admin/admins/new.
        if (registerDto.getRole() != null && registerDto.getRole().equalsIgnoreCase("ADMIN")) {
            bindingResult.rejectValue("role", "error.role", "Admin accounts cannot be self-registered. Contact an existing admin.");
        }

        // Cross-field validation: flat number required for residents, specialization for workers
        if (registerDto.getRole() != null) {
            if (registerDto.getRole().equalsIgnoreCase("RESIDENT")
                    && (registerDto.getFlatNumber() == null || registerDto.getFlatNumber().isBlank())) {
                bindingResult.rejectValue("flatNumber", "error.flatNumber", "Flat number is required for residents");
            }
            if (registerDto.getRole().equalsIgnoreCase("WORKER")
                    && (registerDto.getSpecialization() == null || registerDto.getSpecialization().isBlank())) {
                bindingResult.rejectValue("specialization", "error.specialization", "Specialization is required for workers");
            }
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.registerUser(registerDto);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Registration successful! Please log in.");
        return "redirect:/login";
    }
}
