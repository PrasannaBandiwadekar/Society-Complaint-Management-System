package com.society.complaint.controller;

import com.society.complaint.config.CustomUserDetails;
import com.society.complaint.dto.AdminCreateDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.enums.Role;
import com.society.complaint.service.ComplaintService;
import com.society.complaint.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ComplaintService complaintService;
    private final UserService userService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Complaint> allComplaints = complaintService.getAllComplaints();

        model.addAttribute("complaints", allComplaints);
        model.addAttribute("totalCount", allComplaints.size());
        model.addAttribute("openCount", complaintService.countByStatus(ComplaintStatus.OPEN));
        model.addAttribute("assignedCount", complaintService.countByStatus(ComplaintStatus.ASSIGNED));
        model.addAttribute("inProgressCount", complaintService.countByStatus(ComplaintStatus.IN_PROGRESS));
        model.addAttribute("resolvedCount", complaintService.countByStatus(ComplaintStatus.RESOLVED));
        model.addAttribute("overdueCount", complaintService.countOverdue());

        return "admin/dashboard";
    }

    @GetMapping("/complaints/overdue")
    public String overdueComplaints(Model model) {
        model.addAttribute("complaints", complaintService.getOverdueComplaints());
        model.addAttribute("pageTitle", "Overdue Complaints (SLA Breached)");
        return "admin/complaint-list";
    }

    @GetMapping("/complaints/{id}")
    public String viewComplaint(@PathVariable Long id, Model model) {
        Complaint complaint = complaintService.getById(id);
        List<User> workers = userService.getAllWorkers();

        model.addAttribute("complaint", complaint);
        model.addAttribute("workers", workers);
        return "admin/complaint-detail";
    }

    @PostMapping("/complaints/{id}/assign")
    public String assignWorker(@PathVariable Long id,
                                @RequestParam Long workerId,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes redirectAttributes) {

        complaintService.assignWorker(id, workerId, principal.getUser().getEmail());
        redirectAttributes.addFlashAttribute("successMessage", "Worker assigned successfully. Notifications sent.");
        return "redirect:/admin/complaints/" + id;
    }

    @PostMapping("/complaints/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam ComplaintStatus status,
                                @RequestParam(required = false) String remarks,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes redirectAttributes) {

        complaintService.updateStatus(id, status, remarks, principal.getUser().getEmail());
        redirectAttributes.addFlashAttribute("successMessage", "Status updated to " + status);
        return "redirect:/admin/complaints/" + id;
    }

    @GetMapping("/workers")
    public String listWorkers(Model model) {
        model.addAttribute("workers", userService.getUsersByRole(Role.WORKER));
        return "admin/workers";
    }

    @GetMapping("/residents")
    public String listResidents(Model model) {
        model.addAttribute("residents", userService.getUsersByRole(Role.RESIDENT));
        return "admin/residents";
    }

    // ===================== Admin management (multiple admins) =====================
    // Only an already-authenticated admin can reach these (/admin/** is restricted
    // to ROLE_ADMIN in SecurityConfig), which is the correct way to let there be
    // more than one admin without exposing ADMIN as a public self-registration option.

    @GetMapping("/admins")
    public String listAdmins(Model model) {
        model.addAttribute("admins", userService.getUsersByRole(Role.ADMIN));
        return "admin/admins";
    }

    @GetMapping("/admins/new")
    public String newAdminForm(Model model) {
        model.addAttribute("adminCreateDto", new AdminCreateDto());
        return "admin/new-admin";
    }

    @PostMapping("/admins/new")
    public String createAdmin(@Valid @ModelAttribute("adminCreateDto") AdminCreateDto adminCreateDto,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "admin/new-admin";
        }

        try {
            userService.createAdmin(adminCreateDto);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/new-admin";
        }

        redirectAttributes.addFlashAttribute("successMessage", "New admin account created successfully.");
        return "redirect:/admin/admins";
    }
}
