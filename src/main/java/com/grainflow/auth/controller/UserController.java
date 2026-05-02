package com.grainflow.auth.controller;

import com.grainflow.auth.dto.request.ChangePasswordRequest;
import com.grainflow.auth.dto.request.ConfirmEmailChangeRequest;
import com.grainflow.auth.dto.request.CreateWorkerRequest;
import com.grainflow.auth.dto.request.RequestEmailChangeRequest;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.request.UserFilterRequest;
import com.grainflow.auth.dto.response.ApiResponse;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.RequestEmailChangeResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    // Returns the currently authenticated user — useful for profile display and token verification
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns profile of the authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(currentUser), "Current user retrieved"));
    }
    // Change own password — verifies current, sets new, returns fresh tokens.
    // Frontend should overwrite the stored access + refresh tokens with the response.
    @PatchMapping("/me/password")
    @Operation(summary = "Change own password",
               description = "Verifies the current password and sets a new one. " +
                             "Other devices are logged out; this device receives a fresh token pair.")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser) {
        AuthResponse tokens = userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(tokens, "Password changed successfully"));
    }

    // Step 1 — request email change.
    // Response status="CHANGED" means email was updated immediately (UNVERIFIED company);
    // tokens are included. status="CODE_SENT" means a 6-digit code was sent to the
    // current email and the frontend should now show the code-input screen.
    @PostMapping("/me/email/request-change")
    @Operation(summary = "Request email change",
               description = "If the company is unverified, the email is changed immediately. " +
                             "Otherwise a 6-digit code is sent to the current email.")
    public ResponseEntity<ApiResponse<RequestEmailChangeResponse>> requestEmailChange(
            @Valid @RequestBody RequestEmailChangeRequest request,
            @AuthenticationPrincipal User currentUser) {
        RequestEmailChangeResponse result = userService.requestEmailChange(currentUser.getId(), request);
        String msg = "CODE_SENT".equals(result.status())
                ? "Verification code sent to your current email"
                : "Email changed successfully";
        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }

    // Step 2 — confirm email change with the 6-digit code.
    // On success the email is updated, all refresh tokens are revoked, and a fresh
    // token pair is returned for the current device.
    @PostMapping("/me/email/confirm-change")
    @Operation(summary = "Confirm email change",
               description = "Confirms the pending email change with the 6-digit code from the old email.")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmEmailChange(
            @Valid @RequestBody ConfirmEmailChangeRequest request,
            @AuthenticationPrincipal User currentUser) {
        AuthResponse tokens = userService.confirmEmailChange(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(tokens, "Email changed successfully"));
    }

    // ── Workers ───────────────────────────────────────────────────────────────

    @PostMapping("/workers")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create worker", description = "Manager creates a new worker account in their company")
    public ResponseEntity<ApiResponse<UserResponse>> createWorker(
            @Valid @RequestBody CreateWorkerRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        userService.createWorker(request, currentUser.getId()),
                        "Worker created successfully"
                ));
    }

    @GetMapping("/workers")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get workers", description = "Returns paginated + filtered workers in the manager's company")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getWorkers(
            @ModelAttribute UserFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getWorkers(currentUser.getId(), filter, pageable),
                "Workers retrieved successfully"
        ));
    }

    @GetMapping("/workers/{workerId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get worker", description = "Returns a single worker by ID — must belong to the manager's company")
    public ResponseEntity<ApiResponse<UserResponse>> getWorker(
            @PathVariable UUID workerId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getWorker(workerId, currentUser.getId()),
                "Worker retrieved successfully"
        ));
    }

    @PatchMapping("/workers/{workerId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update worker", description = "Partially updates a worker — only provided fields are changed")
    public ResponseEntity<ApiResponse<UserResponse>> updateWorker(
            @PathVariable UUID workerId,
            @Valid @RequestBody UpdateWorkerRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateWorker(request, workerId, currentUser.getId()),
                "Worker updated successfully"
        ));
    }

    @DeleteMapping("/workers/{workerId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Deactivate worker", description = "Disables the worker account — does not delete the record")
    public ResponseEntity<ApiResponse<Void>> deleteWorker(
            @PathVariable UUID workerId,
            @AuthenticationPrincipal User currentUser) {
        userService.deleteWorker(workerId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Worker deactivated successfully"));
    }
}
