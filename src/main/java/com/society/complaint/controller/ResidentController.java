package com.society.complaint.controller;

import com.society.complaint.config.CustomUserDetails;
import com.society.complaint.dto.ComplaintDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintCategory;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.enums.Priority;
import com.society.complaint.service.ComplaintService;
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
@RequestMapping("/resident")
@RequiredArgsConstructor
public class ResidentController {

    private final ComplaintService complaintService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User resident = principal.getUser();
        List<Complaint> complaints = complaintService.getComplaintsForResident(resident);

        model.addAttribute("complaints", complaints);
        model.addAttribute("resident", resident);
        model.addAttribute("openCount", complaints.stream().filter(c -> c.getStatus() != ComplaintStatus.RESOLVED && c.getStatus() != ComplaintStatus.CLOSED).count());
        model.addAttribute("resolvedCount", complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED).count());
        model.addAttribute("overdueCount", complaints.stream().filter(Complaint::isOverdue).count());

        return "resident/dashboard";
    }

    @GetMapping("/complaints/new")
    public String newComplaintForm(Model model) {
        model.addAttribute("complaintDto", new ComplaintDto());
        model.addAttribute("categories", ComplaintCategory.values());
        model.addAttribute("priorities", Priority.values());
        return "resident/new-complaint";
    }

    @PostMapping("/complaints/new")
    public String createComplaint(@Valid @ModelAttribute("complaintDto") ComplaintDto complaintDto,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal CustomUserDetails principal,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", ComplaintCategory.values());
            model.addAttribute("priorities", Priority.values());
            return "resident/new-complaint";
        }

        Complaint created = complaintService.createComplaint(complaintDto, principal.getUser());
        redirectAttributes.addFlashAttribute("successMessage",
                "Complaint #" + created.getId() + " raised successfully. We'll notify you on updates.");
        return "redirect:/resident/dashboard";
    }

    @GetMapping("/complaints/{id}")
    public String viewComplaint(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 Model model) {
        Complaint complaint = complaintService.getById(id);

        // Authorization check: a resident can only view their own complaints
        if (!complaint.getResident().getId().equals(principal.getUser().getId())) {
            return "redirect:/resident/dashboard?error=unauthorized";
        }

        model.addAttribute("complaint", complaint);
        return "resident/complaint-detail";
    }

    @PostMapping("/complaints/{id}/reopen")
    public String reopenComplaint(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails principal,
                                   RedirectAttributes redirectAttributes) {
        Complaint complaint = complaintService.getById(id);

        if (!complaint.getResident().getId().equals(principal.getUser().getId())) {
            return "redirect:/resident/dashboard?error=unauthorized";
        }

        complaintService.updateStatus(id, ComplaintStatus.REOPENED,
                "Resident reopened the complaint - issue not fully resolved",
                principal.getUser().getEmail());

        redirectAttributes.addFlashAttribute("successMessage", "Complaint reopened. Admin has been notified.");
        return "redirect:/resident/complaints/" + id;
    }
}
