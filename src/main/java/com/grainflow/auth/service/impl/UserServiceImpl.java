package com.grainflow.auth.service.impl;

import com.grainflow.auth.dto.request.CreateWorkerRequest;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse createWorker(CreateWorkerRequest request, UUID managerId) {
        // Load manager to resolve which company the worker belongs to
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.conflict("User with this email already exists");
        }

        User worker = userRepository.save(User.builder()
                .company(manager.getCompany())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                // PIN is hashed — never stored in plain text
                .pin(passwordEncoder.encode(request.pin()))
                .role(Role.WORKER)
                .employeeId(generateEmployeeId())
                .enabled(true)
                .build());

        log.info("Worker created: {} in company: {}", worker.getEmail(), manager.getCompany().getName());

        return UserResponse.from(worker);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getWorkers(UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        return userRepository
                .findAllByCompanyIdAndRole(manager.getCompany().getId(), Role.WORKER)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getWorker(UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        // Ensure the worker belongs to the manager's company
        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        return UserResponse.from(worker);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateWorker(UpdateWorkerRequest request, UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        // Apply only the fields that were provided — null means "keep existing value"
        if (request.firstName() != null) worker.setFirstName(request.firstName());
        if (request.lastName()  != null) worker.setLastName(request.lastName());
        if (request.email()     != null) {
            if (userRepository.existsByEmail(request.email()) && !worker.getEmail().equals(request.email())) {
                throw AuthException.conflict("Email is already taken");
            }
            worker.setEmail(request.email());
        }
        if (request.password()  != null) worker.setPassword(passwordEncoder.encode(request.password()));
        if (request.pin()       != null) worker.setPin(passwordEncoder.encode(request.pin()));

        User updated = userRepository.save(worker);
        log.info("Worker updated: {} by manager: {}", updated.getEmail(), manager.getEmail());

        return UserResponse.from(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorker(UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        // Soft delete — disable the account rather than removing the record
        worker.setEnabled(false);
        userRepository.save(worker);
        log.info("Worker deactivated: {} by manager: {}", worker.getEmail(), manager.getEmail());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Generates a unique employee ID in format EMP-XXXXXXXX
    private String generateEmployeeId() {
        String employeeId;
        do {
            employeeId = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (userRepository.existsByEmployeeId(employeeId));
        return employeeId;
    }
}
