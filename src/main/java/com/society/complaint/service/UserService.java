package com.society.complaint.service;

import com.society.complaint.dto.AdminCreateDto;
import com.society.complaint.dto.RegisterDto;
import com.society.complaint.entity.User;
import com.society.complaint.enums.Role;

import java.util.List;

public interface UserService {

    User registerUser(RegisterDto dto);

    /**
     * Creates a new ADMIN account. Unlike registerUser(), this is only ever
     * called from an admin-only controller endpoint (protected by hasRole('ADMIN')
     * in SecurityConfig), so the role is always forced to ADMIN regardless of
     * what's passed in - there's no role field on AdminCreateDto to spoof.
     */
    User createAdmin(AdminCreateDto dto);

    List<User> getAllWorkers();

    List<User> getUsersByRole(Role role);

    User getByEmail(String email);
}
