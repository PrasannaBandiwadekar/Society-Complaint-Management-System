package com.society.complaint.dto;

import com.society.complaint.enums.ComplaintCategory;
import com.society.complaint.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplaintDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Please select a category")
    private ComplaintCategory category;

    @NotNull(message = "Please select a priority")
    private Priority priority;

    private String flatNumber;
}
