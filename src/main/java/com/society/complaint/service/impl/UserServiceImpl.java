package com.society.complaint.service.impl;

import com.society.complaint.dto.AdminCreateDto;
import com.society.complaint.dto.RegisterDto;
import com.society.complaint.entity.User;
import com.society.complaint.enums.Role;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User registerUser(RegisterDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        Role role = Role.valueOf(dto.getRole().toUpperCase());

        User user = User.builder()
                .fullName(dto.getFullName())
                .email(dto.getEmail().toLowerCase())
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(dto.getPhoneNumber())
                .role(role)
                .flatNumber(role == Role.RESIDENT ? dto.getFlatNumber() : null)
                .specialization(role == Role.WORKER ? dto.getSpecialization() : null)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User createAdmin(AdminCreateDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User admin = User.builder()
                .fullName(dto.getFullName())
                .email(dto.getEmail().toLowerCase())
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(dto.getPhoneNumber())
                .role(Role.ADMIN) // forced - this method is only reachable by an existing admin
                .enabled(true)
                .build();

        return userRepository.save(admin);
    }

    @Override
    public List<User> getAllWorkers() {
        return userRepository.findByRole(Role.WORKER);
    }

    @Override
    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Override
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }
}
