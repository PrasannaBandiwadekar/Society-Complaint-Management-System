package com.society.complaint.controller;

import com.society.complaint.config.CustomUserDetails;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final ComplaintService complaintService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User worker = principal.getUser();
        List<Complaint> assigned = complaintService.getComplaintsForWorker(worker);

        model.addAttribute("complaints", assigned);
        model.addAttribute("worker", worker);
        model.addAttribute("pendingCount", assigned.stream()
                .filter(c -> c.getStatus() != ComplaintStatus.RESOLVED && c.getStatus() != ComplaintStatus.CLOSED).count());
        model.addAttribute("resolvedCount", assigned.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED).count());
        model.addAttribute("overdueCount", assigned.stream().filter(Complaint::isOverdue).count());

        return "worker/dashboard";
    }

    @GetMapping("/complaints/{id}")
    public String viewComplaint(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 Model model) {
        Complaint complaint = complaintService.getById(id);

        // Authorization check: a worker can only act on complaints assigned to them
        if (complaint.getWorker() == null || !complaint.getWorker().getId().equals(principal.getUser().getId())) {
            return "redirect:/worker/dashboard?error=unauthorized";
        }

        model.addAttribute("complaint", complaint);
        return "worker/complaint-detail";
    }

    @PostMapping("/complaints/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam ComplaintStatus status,
                                @RequestParam(required = false) String remarks,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes redirectAttributes) {

        Complaint complaint = complaintService.getById(id);
        if (complaint.getWorker() == null || !complaint.getWorker().getId().equals(principal.getUser().getId())) {
            return "redirect:/worker/dashboard?error=unauthorized";
        }

        complaintService.updateStatus(id, status, remarks, principal.getUser().getEmail());
        redirectAttributes.addFlashAttribute("successMessage", "Status updated to " + status);
        return "redirect:/worker/complaints/" + id;
    }
}
