package com.grainflow.auth.service;

import com.grainflow.auth.dto.request.ChangePasswordRequest;
import com.grainflow.auth.dto.request.ConfirmEmailChangeRequest;
import com.grainflow.auth.dto.request.CreateWorkerRequest;
import com.grainflow.auth.dto.request.RequestEmailChangeRequest;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.request.UserFilterRequest;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.RequestEmailChangeResponse;
import com.grainflow.auth.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    // Create a new worker account under the manager's company
    UserResponse createWorker(CreateWorkerRequest request, UUID managerId);

    // Get paginated + filtered workers belonging to the manager's company
    Page<UserResponse> getWorkers(UUID managerId, UserFilterRequest filter, Pageable pageable);

    // Get a single worker — scoped to the manager's company
    UserResponse getWorker(UUID workerId, UUID managerId);

    // Partially update a worker — null fields are ignored
    UserResponse updateWorker(UpdateWorkerRequest request, UUID workerId, UUID managerId);

    // Deactivate a worker — sets enabled=false, does not delete the record
    void deleteWorker(UUID workerId, UUID managerId);

    // Change the authenticated user's own password.
    // Verifies current password, hashes the new one, revokes all refresh tokens
    // (logs out every other device), then issues a fresh access+refresh pair so
    // the current session stays alive.
    AuthResponse changePassword(UUID userId, ChangePasswordRequest request);

    // Step 1 of email change.
    //   • If the company is UNVERIFIED → email is changed immediately and fresh
    //     tokens are returned (status=CHANGED). For a manager, a new verification
    //     link is also sent to the new address.
    //   • If the company is VERIFIED → a 6-digit code is sent to the OLD email
    //     and an EmailChangeCode row is stored (status=CODE_SENT). The user must
    //     then call confirmEmailChange().
    RequestEmailChangeResponse requestEmailChange(UUID userId, RequestEmailChangeRequest request);

    // Step 2 of email change (only for the VERIFIED path).
    // Verifies the code against the stored hash, applies the new email, deletes
    // the code, and issues fresh tokens (other devices logged out).
    AuthResponse confirmEmailChange(UUID userId, ConfirmEmailChangeRequest request);
}
