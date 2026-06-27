package com.society.complaint.entity;

import com.society.complaint.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * NOTE on Lombok choice: we deliberately use @Getter/@Setter instead of @Data here.
 * @Data would generate equals()/hashCode()/toString() using ALL fields, including
 * the password hash (security leak in logs), and bidirectional JPA relationships
 * combined with @Data on both sides is a classic StackOverflowError trap
 * (infinite recursion: User.toString() -> Complaint.toString() -> User.toString() -> ...).
 * @EqualsAndHashCode(of = "id") keeps equality based on primary key only, which is
 * also the JPA-recommended approach for entities.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "fullName", "email", "role"}) // password deliberately excluded
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String fullName;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password; // stored as BCrypt hash, never plain text

    @NotBlank
    @Column(nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Only relevant for RESIDENT role
    private String flatNumber;

    // Only relevant for WORKER role - e.g. "Plumber", "Electrician"
    private String specialization;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
