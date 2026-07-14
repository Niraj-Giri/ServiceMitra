package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.taskrequests.TaskRequestService;
import com.mitra.taskrequests.TaskRequestStatus;
import com.mitra.taskrequests.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Task Request REST controller - Airtasker-style marketplace endpoints.
 *
 * Customer endpoints:  POST a task, view tasks, cancel, accept/counter quotes
 * Provider endpoints:  View available tasks, submit/withdraw quotes, respond to counters, start/complete job
 * Admin endpoints:     View all tasks, cancel any task
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
// SEC-05: @CrossOrigin removed - CORS is centrally managed in SecurityConfig
public class TaskRequestController {

    private final TaskRequestService taskRequestService;
    private final AuthService authService;

    // -----------------------------------------------------------------------------
    // CUSTOMER ENDPOINTS
    // -----------------------------------------------------------------------------

    /**
     * POST /api/v1/tasks
     * Customer posts a new task request.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")  // SEC-03: Only customers can post tasks
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            HttpServletRequest httpRequest,
            @Valid @RequestBody CreateTaskRequest req) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        TaskResponse task = taskRequestService.createTask(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(task, "Task posted successfully. Nearby providers will be notified."));
    }

    /**
     * GET /api/v1/tasks/my
     * Customer views their own task requests.
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")  // SEC-03: Only customers can view their own tasks
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMyTasks(HttpServletRequest httpRequest) {
        Long userId = authService.extractUserIdFromToken(httpRequest);
        return ResponseEntity.ok(ApiResponse.success(taskRequestService.getTasksForCustomer(userId)));
    }

    /**
     * GET /api/v1/tasks/provider
     * Provider views task requests assigned to them.
     */
    @GetMapping("/provider")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can view their assigned tasks
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getProviderTasks(HttpServletRequest httpRequest) {
        Long providerId = authService.extractUserIdFromToken(httpRequest);
        return ResponseEntity.ok(ApiResponse.success(taskRequestService.getTasksForProvider(providerId)));
    }

    /**
     * GET /api/v1/tasks/{id}
     * Get task detail (customer or provider). Customer gets OTP if status = ACCEPTED.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean asProvider) {

        Long requestingUserId = authService.extractUserIdFromToken(httpRequest);
        TaskResponse task = taskRequestService.getTaskById(id, requestingUserId, asProvider);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    /**
     * DELETE /api/v1/tasks/{id}
     * Customer cancels an open task.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> cancelTask(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        TaskResponse task = taskRequestService.cancelTask(userId, id, reason);
        return ResponseEntity.ok(ApiResponse.success(task, "Task cancelled."));
    }

    /**
     * PUT /api/v1/tasks/{taskId}/quotes/{quoteId}/accept
     * Customer directly accepts a provider's quote.
     */
    @PutMapping("/{taskId}/quotes/{quoteId}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")  // SEC-03: Only customers can accept quotes
    public ResponseEntity<ApiResponse<TaskResponse>> acceptQuote(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @PathVariable Long quoteId) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        TaskResponse task = taskRequestService.acceptQuote(userId, taskId, quoteId);
        return ResponseEntity.ok(ApiResponse.success(task, "Quote accepted! Share the OTP with the provider to start the job."));
    }

    /**
     * POST /api/v1/tasks/{taskId}/quotes/{quoteId}/checkout
     * Customer checks out and accepts quote with coupon/points discount.
     */
    @PostMapping("/{taskId}/quotes/{quoteId}/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")  // SEC-03: Only customers can checkout
    public ResponseEntity<ApiResponse<TaskResponse>> checkoutQuote(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @PathVariable Long quoteId,
            @RequestBody TaskCheckoutRequest req) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        TaskResponse task = taskRequestService.checkoutTaskRequest(userId, taskId, quoteId, req);
        return ResponseEntity.ok(ApiResponse.success(task, "Booking confirmed successfully! Share OTP to start the job."));
    }

    /**
     * PUT /api/v1/tasks/{taskId}/quotes/{quoteId}/counter
     * Customer makes a counter-offer on a quote.
     */
    @PutMapping("/{taskId}/quotes/{quoteId}/counter")
    @PreAuthorize("hasRole('CUSTOMER')")  // SEC-03: Only customers can make counter-offers
    public ResponseEntity<ApiResponse<QuoteResponse>> counterOffer(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @PathVariable Long quoteId,
            @Valid @RequestBody CounterOfferRequest req) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        QuoteResponse quote = taskRequestService.counterOffer(userId, taskId, quoteId, req);
        return ResponseEntity.ok(ApiResponse.success(quote, "Counter-offer sent to provider."));
    }

    // -----------------------------------------------------------------------------
    // PROVIDER ENDPOINTS
    // -----------------------------------------------------------------------------

    /**
     * GET /api/v1/tasks/available
     * Provider sees all open task requests near them (within configured radius).
     */
    @GetMapping("/available")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers see available tasks
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getAvailableTasks(HttpServletRequest httpRequest) {
        Long providerId = authService.extractUserIdFromToken(httpRequest);
        List<TaskResponse> tasks = taskRequestService.getAvailableTasksForProvider(providerId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    /**
     * GET /api/v1/tasks/my-quotes
     * Provider views all their submitted quotes.
     */
    @GetMapping("/my-quotes")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can view their quotes
    public ResponseEntity<ApiResponse<List<QuoteResponse>>> getMyQuotes(HttpServletRequest httpRequest) {
        Long providerId = authService.extractUserIdFromToken(httpRequest);
        return ResponseEntity.ok(ApiResponse.success(taskRequestService.getMyQuotes(providerId)));
    }

    /**
     * POST /api/v1/tasks/{taskId}/quotes
     * Provider submits a quote for an open task.
     */
    @PostMapping("/{taskId}/quotes")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can submit quotes
    public ResponseEntity<ApiResponse<QuoteResponse>> submitQuote(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitQuoteRequest req) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        QuoteResponse quote = taskRequestService.submitQuote(providerId, taskId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(quote, "Quote submitted successfully."));
    }

    /**
     * PUT /api/v1/tasks/{taskId}/quotes/{quoteId}/respond
     * Provider accepts or rejects a customer's counter-offer.
     * Body: { "accept": true/false }
     */
    @PutMapping("/{taskId}/quotes/{quoteId}/respond")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can respond to counter-offers
    public ResponseEntity<ApiResponse<TaskResponse>> respondToCounter(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @PathVariable Long quoteId,
            @RequestBody Map<String, Boolean> body) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        TaskResponse task = taskRequestService.respondToCounter(providerId, taskId, quoteId, accept);
        String msg = accept ? "Counter-offer accepted! OTP has been generated." : "Counter-offer declined.";
        return ResponseEntity.ok(ApiResponse.success(task, msg));
    }

    /**
     * DELETE /api/v1/tasks/{taskId}/quotes/{quoteId}
     * Provider withdraws their own quote.
     */
    @DeleteMapping("/{taskId}/quotes/{quoteId}")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can withdraw their own quotes
    public ResponseEntity<ApiResponse<QuoteResponse>> withdrawQuote(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @PathVariable Long quoteId) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        QuoteResponse quote = taskRequestService.withdrawQuote(providerId, taskId, quoteId);
        return ResponseEntity.ok(ApiResponse.success(quote, "Quote withdrawn."));
    }

    /**
     * POST /api/v1/tasks/{taskId}/start
     * Provider starts the job directly.
     */
    @PostMapping("/{taskId}/start")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can start jobs
    public ResponseEntity<ApiResponse<TaskResponse>> startTask(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        String otp = body != null ? body.get("otp") : null;
        if (otp == null || otp.isBlank()) {
            throw new com.mitra.common.BadRequestException("OTP is required to start the job.");
        }
        TaskResponse task = taskRequestService.startTask(providerId, taskId, otp);
        return ResponseEntity.ok(ApiResponse.success(task, "Job started successfully!"));
    }

    /**
     * POST /api/v1/tasks/{taskId}/complete
     * Provider marks the job as complete. Triggers earnings and reward points.
     */
    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can complete jobs
    public ResponseEntity<ApiResponse<TaskResponse>> completeTask(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        TaskResponse task = taskRequestService.completeTask(providerId, taskId);
        return ResponseEntity.ok(ApiResponse.success(task, "Job completed! Earnings have been credited."));
    }

    /**
     * POST /api/v1/tasks/{taskId}/provider-cancel
     * Provider cancels an accepted task.
     */
    @PostMapping("/{taskId}/provider-cancel")
    @PreAuthorize("hasRole('PROVIDER')")  // SEC-03: Only providers can cancel their accepted tasks
    public ResponseEntity<ApiResponse<TaskResponse>> providerCancelTask(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, String> body) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        TaskResponse task = taskRequestService.cancelTaskByProvider(providerId, taskId, reason);
        return ResponseEntity.ok(ApiResponse.success(task, "Task cancelled."));
    }

    // -----------------------------------------------------------------------------
    // ADMIN ENDPOINTS
    // -----------------------------------------------------------------------------

    /**
     * GET /api/v1/tasks/admin/all
     * Admin views all task requests (optionally filtered by status).
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> adminGetAllTasks(
            @RequestParam(required = false) String status) {

        List<TaskResponse> tasks;
        if (status != null && !status.isBlank()) {
            TaskRequestStatus statusEnum = TaskRequestStatus.valueOf(status.toUpperCase());
            tasks = taskRequestService.getTasksByStatusForAdmin(statusEnum);
        } else {
            tasks = taskRequestService.getAllTasksForAdmin();
        }
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    /**
     * PUT /api/v1/tasks/admin/{id}/cancel
     * Admin force-cancels any task.
     */
    @PutMapping("/admin/{id}/cancel")
    public ResponseEntity<ApiResponse<TaskResponse>> adminCancelTask(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : "Cancelled by admin";
        TaskResponse task = taskRequestService.adminCancelTask(id, reason);
        return ResponseEntity.ok(ApiResponse.success(task, "Task cancelled by admin."));
    }
}
