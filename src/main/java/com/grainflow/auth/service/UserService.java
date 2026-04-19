package com.grainflow.auth.service;

import com.grainflow.auth.dto.request.CreateWorkerRequest;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.request.UserFilterRequest;
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
}
