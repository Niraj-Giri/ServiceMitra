package com.mitra.taskrequests;

import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.common.RewardPointsHistory;
import com.mitra.common.RewardPointsHistoryRepository;
import com.mitra.config.PlatformSettings;
import com.mitra.config.PlatformSettingsRepository;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.taskrequests.dto.*;
import com.mitra.users.Provider;
import com.mitra.users.ProviderIncentive;
import com.mitra.users.ProviderIncentiveRepository;
import com.mitra.users.ProviderRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for the Airtasker-style task/quote marketplace.
 *
 * Flow:
 * 1. Customer creates a TaskRequest (OPEN)
 * 2. Providers see open tasks in their area and submit Quotes
 * 3. Customer accepts a quote directly OR makes a counter-offer
 * 4. If counter: provider accepts/rejects
 * 5. On agreement: ACCEPTED → OTP generated
 * 6. Provider starts job (OTP verify) → completes job → earnings + points awarded
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRequestService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");
    private static final int OTP_EXPIRY_MINUTES = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TaskRequestRepository taskRequestRepository;
    private final QuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final RewardPointsHistoryRepository rewardPointsHistoryRepository;
    private final ProviderIncentiveRepository providerIncentiveRepository;

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER: CREATE TASK
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse createTask(Long userId, CreateTaskRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BadRequestException("Your account is suspended. Please contact support.");
        }

        ServiceListing service = serviceListingRepository.findById(req.getServiceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service", req.getServiceId()));

        if (Boolean.FALSE.equals(service.getIsActive())) {
            throw new BadRequestException("This service is currently unavailable.");
        }

        if (req.getBudgetMinNpr().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Budget must be greater than zero.");
        }
        if (req.getBudgetMaxNpr().compareTo(req.getBudgetMinNpr()) < 0) {
            throw new BadRequestException("Maximum budget must be >= minimum budget.");
        }

        // Handle reward points redemption
        int pointsToRedeem = req.getPointsToRedeem() != null ? req.getPointsToRedeem() : 0;
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        if (pointsToRedeem > 0) {
            if (pointsToRedeem > user.getRewardPoints()) {
                throw new BadRequestException("Insufficient reward points. You have " + user.getRewardPoints() + " points.");
            }
            PlatformSettings settings = getPlatformSettings();
            pointsDiscount = BigDecimal.valueOf(pointsToRedeem)
                    .multiply(settings.getPointsRedemptionRate())
                    .setScale(2, RoundingMode.HALF_UP);

            user.setRewardPoints(user.getRewardPoints() - pointsToRedeem);
            rewardPointsHistoryRepository.save(RewardPointsHistory.builder()
                    .userId(userId)
                    .points(-pointsToRedeem)
                    .actionType("BOOKING_REDEEMED")
                    .description("Redeemed " + pointsToRedeem + " points for task discount")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        TaskRequest task = TaskRequest.builder()
                .user(user)
                .serviceId(service.getId())
                .serviceName(service.getName())
                .category(service.getCategory())
                .title(req.getTitle())
                .description(req.getDescription())
                .budgetMinNpr(req.getBudgetMinNpr())
                .budgetMaxNpr(req.getBudgetMaxNpr())
                .address(req.getAddress())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .preferredDate(req.getPreferredDate())
                .status(TaskRequestStatus.OPEN)
                .pointsRedeemed(pointsToRedeem)
                .pointsDiscountNpr(pointsDiscount)
                .build();

        task = taskRequestRepository.save(task);
        log.info("Task {} created by user {} for service '{}' ({})", task.getId(), userId, service.getName(), service.getCategory());
        return TaskResponse.from(task, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER: VIEW OWN TASKS
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksForCustomer(Long userId) {
        return taskRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(tr -> TaskResponse.from(tr, true))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long requestingUserId, boolean isProvider) {
        TaskRequest task = getTaskOrThrow(taskId);
        // Customers can only see their own tasks; providers can see any open/relevant task
        if (!isProvider && !task.getUser().getId().equals(requestingUserId)) {
            throw new ForbiddenException("You do not have access to this task.");
        }
        boolean showOtp = !isProvider && task.getUser().getId().equals(requestingUserId);
        return TaskResponse.from(task, showOtp);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER: CANCEL TASK
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse cancelTask(Long userId, Long taskId, String reason) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertOwner(task, userId);

        if (task.getStatus() == TaskRequestStatus.ACCEPTED || task.getStatus() == TaskRequestStatus.STARTED) {
            throw new BadRequestException("Cannot cancel a task that has already started. Please contact support.");
        }
        if (task.getStatus().isTerminal()) {
            throw new BadRequestException("Task is already " + task.getStatus().name().toLowerCase() + ".");
        }

        // Reject all pending quotes
        List<Quote> activeQuotes = quoteRepository.findByTaskRequestIdAndStatusIn(
                taskId, List.of(QuoteStatus.PENDING, QuoteStatus.COUNTER_OFFERED));
        activeQuotes.forEach(q -> q.setStatus(QuoteStatus.REJECTED));
        quoteRepository.saveAll(activeQuotes);

        task.setStatus(TaskRequestStatus.CANCELLED);
        task.setCancelledBy("CUSTOMER");
        task.setCancellationReason(reason);
        task.setCancelledAt(LocalDateTime.now());

        // Refund reward points if any were redeemed
        if (task.getPointsRedeemed() != null && task.getPointsRedeemed() > 0) {
            User user = task.getUser();
            user.setRewardPoints(user.getRewardPoints() + task.getPointsRedeemed());
            rewardPointsHistoryRepository.save(RewardPointsHistory.builder()
                    .userId(userId)
                    .points(task.getPointsRedeemed())
                    .actionType("COMPENSATION")
                    .description("Refunded " + task.getPointsRedeemed() + " points for cancelled task #" + taskId)
                    .bookingId(taskId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return TaskResponse.from(taskRequestRepository.save(task), false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: VIEW NEARBY OPEN TASKS
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> getAvailableTasksForProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));

        if (!"APPROVED".equals(provider.getStatus())) {
            throw new ForbiddenException("Your account is not approved yet.");
        }
        if (provider.getLatitude() == null || provider.getLongitude() == null) {
            throw new BadRequestException("Please set your location in profile settings to see nearby tasks.");
        }

        PlatformSettings settings = getPlatformSettings();
        double radius = settings.getBookingRadius() != null ? settings.getBookingRadius() : 15.0;

        List<TaskRequest> tasks = taskRequestRepository.findOpenTasksNearProvider(
                provider.getServiceCategory(),
                provider.getLatitude(),
                provider.getLongitude(),
                radius,
                providerId
        );

        return tasks.stream().map(tr -> TaskResponse.from(tr, false)).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: SUBMIT QUOTE
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public QuoteResponse submitQuote(Long providerId, Long taskId, SubmitQuoteRequest req) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));

        if (!"APPROVED".equals(provider.getStatus())) {
            throw new ForbiddenException("Your account is not approved to submit quotes.");
        }

        TaskRequest task = getTaskOrThrow(taskId);

        if (!task.getStatus().allowsNewQuotes()) {
            throw new BadRequestException("This task is no longer accepting quotes (status: " + task.getStatus() + ").");
        }

        // Check for duplicate active quote
        boolean alreadyQuoted = quoteRepository.existsByTaskRequestIdAndProviderIdAndStatusNotIn(
                taskId, providerId, List.of(QuoteStatus.WITHDRAWN, QuoteStatus.REJECTED, QuoteStatus.EXPIRED));
        if (alreadyQuoted) {
            throw new BadRequestException("You already have an active quote for this task.");
        }

        if (req.getQuotedPriceNpr().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quote price must be greater than zero.");
        }

        Quote quote = Quote.builder()
                .taskRequest(task)
                .provider(provider)
                .quotedPriceNpr(req.getQuotedPriceNpr())
                .message(req.getMessage())
                .status(QuoteStatus.PENDING)
                .build();

        quote = quoteRepository.save(quote);

        // Move task to QUOTING if it was OPEN
        if (task.getStatus() == TaskRequestStatus.OPEN) {
            task.setStatus(TaskRequestStatus.QUOTING);
            taskRequestRepository.save(task);
        }

        log.info("Provider {} submitted quote {} (NPR {}) for task {}", providerId, quote.getId(), req.getQuotedPriceNpr(), taskId);
        return QuoteResponse.from(quote);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER: ACCEPT A QUOTE
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse acceptQuote(Long userId, Long taskId, Long quoteId) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertOwner(task, userId);

        if (task.getStatus() != TaskRequestStatus.QUOTING) {
            throw new BadRequestException("Task must be in QUOTING state to accept a quote.");
        }

        Quote quote = getQuoteOrThrow(quoteId);
        assertQuoteBelongsToTask(quote, taskId);

        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new BadRequestException("Only PENDING quotes can be accepted directly.");
        }

        // Accept this quote
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setFinalPriceNpr(quote.getQuotedPriceNpr());
        quoteRepository.save(quote);

        // Reject all other active quotes for this task
        rejectOtherQuotes(taskId, quoteId);

        // Update task
        task.setStatus(TaskRequestStatus.ACCEPTED);
        task.setAcceptedQuoteId(quoteId);
        task.setFinalAmountNpr(quote.getQuotedPriceNpr());
        task.setStartOtp(generateOtp());
        task.setOtpGeneratedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        task = taskRequestRepository.save(task);
        log.info("Customer {} accepted quote {} for task {}. OTP generated.", userId, quoteId, taskId);
        return TaskResponse.from(task, true);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER: COUNTER-OFFER A QUOTE
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public QuoteResponse counterOffer(Long userId, Long taskId, Long quoteId, CounterOfferRequest req) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertOwner(task, userId);

        if (task.getStatus() != TaskRequestStatus.QUOTING) {
            throw new BadRequestException("Task must be in QUOTING state to counter-offer.");
        }

        Quote quote = getQuoteOrThrow(quoteId);
        assertQuoteBelongsToTask(quote, taskId);

        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new BadRequestException("Can only counter-offer a PENDING quote.");
        }

        if (req.getCounterPriceNpr().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Counter-offer price must be greater than zero.");
        }

        quote.setCounterPriceNpr(req.getCounterPriceNpr());
        quote.setStatus(QuoteStatus.COUNTER_OFFERED);
        quote.setUpdatedAt(LocalDateTime.now());

        quote = quoteRepository.save(quote);
        log.info("Customer {} counter-offered NPR {} on quote {} (task {})", userId, req.getCounterPriceNpr(), quoteId, taskId);
        return QuoteResponse.from(quote);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: RESPOND TO COUNTER-OFFER
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse respondToCounter(Long providerId, Long taskId, Long quoteId, boolean accept) {
        TaskRequest task = getTaskOrThrow(taskId);
        Quote quote = getQuoteOrThrow(quoteId);
        assertQuoteBelongsToTask(quote, taskId);

        if (!quote.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("This is not your quote.");
        }

        if (quote.getStatus() != QuoteStatus.COUNTER_OFFERED) {
            throw new BadRequestException("This quote is not awaiting a counter-offer response.");
        }

        if (accept) {
            // Provider agrees to customer's counter price
            quote.setStatus(QuoteStatus.ACCEPTED);
            quote.setFinalPriceNpr(quote.getCounterPriceNpr());
            quoteRepository.save(quote);

            rejectOtherQuotes(taskId, quoteId);

            task.setStatus(TaskRequestStatus.ACCEPTED);
            task.setAcceptedQuoteId(quoteId);
            task.setFinalAmountNpr(quote.getCounterPriceNpr());
            task.setStartOtp(generateOtp());
            task.setOtpGeneratedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            task = taskRequestRepository.save(task);

            log.info("Provider {} accepted counter-offer on quote {} for task {}. OTP generated.", providerId, quoteId, taskId);
        } else {
            // Provider rejects counter-offer — quote is rejected
            quote.setStatus(QuoteStatus.REJECTED);
            quote.setUpdatedAt(LocalDateTime.now());
            quoteRepository.save(quote);

            log.info("Provider {} rejected counter-offer on quote {} for task {}.", providerId, quoteId, taskId);

            // Check if any other active quotes remain; if not, revert task to OPEN
            List<Quote> remaining = quoteRepository.findByTaskRequestIdAndStatusIn(
                    taskId, List.of(QuoteStatus.PENDING, QuoteStatus.COUNTER_OFFERED));
            if (remaining.isEmpty()) {
                task.setStatus(TaskRequestStatus.OPEN);
                task.setUpdatedAt(LocalDateTime.now());
                taskRequestRepository.save(task);
            }
        }

        return TaskResponse.from(task, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: WITHDRAW OWN QUOTE
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public QuoteResponse withdrawQuote(Long providerId, Long taskId, Long quoteId) {
        Quote quote = getQuoteOrThrow(quoteId);
        assertQuoteBelongsToTask(quote, taskId);

        if (!quote.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("This is not your quote.");
        }
        if (!quote.getStatus().isActive()) {
            throw new BadRequestException("Can only withdraw an active quote.");
        }

        quote.setStatus(QuoteStatus.WITHDRAWN);
        quote.setUpdatedAt(LocalDateTime.now());
        quote = quoteRepository.save(quote);
        log.info("Provider {} withdrew quote {} from task {}", providerId, quoteId, taskId);
        return QuoteResponse.from(quote);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: START JOB (OTP verify)
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse startTask(Long providerId, Long taskId) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertAcceptedProvider(task, providerId);

        if (task.getStatus() != TaskRequestStatus.ACCEPTED) {
            throw new BadRequestException("Job can only be started when status is ACCEPTED.");
        }

        task.setStatus(TaskRequestStatus.STARTED);
        task.setStartedAt(LocalDateTime.now());
        task.setStartOtp(null); 
        task.setUpdatedAt(LocalDateTime.now());

        task = taskRequestRepository.save(task);
        log.info("Task {} started by provider {}", taskId, providerId);
        return TaskResponse.from(task, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: COMPLETE JOB
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse completeTask(Long providerId, Long taskId) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertAcceptedProvider(task, providerId);

        if (task.getStatus() != TaskRequestStatus.STARTED) {
            throw new BadRequestException("Job can only be completed when status is STARTED.");
        }

        BigDecimal finalAmount = task.getFinalAmountNpr();
        if (finalAmount == null) finalAmount = BigDecimal.ZERO;

        // Apply points discount
        BigDecimal discount = task.getPointsDiscountNpr() != null ? task.getPointsDiscountNpr() : BigDecimal.ZERO;
        BigDecimal billable = finalAmount.subtract(discount).max(BigDecimal.ZERO);

        BigDecimal platformFee = billable.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal providerEarnings = billable.subtract(platformFee);

        task.setStatus(TaskRequestStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setPlatformFee(platformFee);
        task.setFinalAmountNpr(billable);
        task.setUpdatedAt(LocalDateTime.now());
        task = taskRequestRepository.save(task);

        // Award reward points to customer (0.10 points per NPR spent)
        PlatformSettings settings = getPlatformSettings();
        int pointsEarned = billable.multiply(settings.getPointsPerNprSpent())
                .setScale(0, RoundingMode.DOWN).intValue();
        if (pointsEarned > 0) {
            User customer = task.getUser();
            customer.setRewardPoints(customer.getRewardPoints() + pointsEarned);
            rewardPointsHistoryRepository.save(RewardPointsHistory.builder()
                    .userId(customer.getId())
                    .points(pointsEarned)
                    .actionType("BOOKING_EARNED")
                    .description("Earned " + pointsEarned + " points for task #" + taskId)
                    .bookingId(taskId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // Update provider stats
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));
        provider.setTotalJobs(provider.getTotalJobs() + 1);
        providerRepository.save(provider);

        // Milestone incentive: Rs. 500 bonus every 5 completed jobs
        if (provider.getTotalJobs() % 5 == 0) {
            providerIncentiveRepository.save(ProviderIncentive.builder()
                    .providerId(providerId)
                    .amount(new BigDecimal("500.00"))
                    .bookingId(taskId)
                    .reason("COMPLETED_BOOKINGS_MILESTONE")
                    .description("Milestone bonus: " + provider.getTotalJobs() + " completed jobs")
                    .status("PENDING_PAYOUT")
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("Milestone incentive awarded to provider {} (total jobs: {})", providerId, provider.getTotalJobs());
        }

        log.info("Task {} completed. Provider {} earned NPR {}. Customer earned {} points.",
                taskId, providerId, providerEarnings, pointsEarned);
        return TaskResponse.from(task, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: CANCEL ACCEPTED TASK
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse cancelTaskByProvider(Long providerId, Long taskId, String reason) {
        TaskRequest task = getTaskOrThrow(taskId);
        assertAcceptedProvider(task, providerId);

        if (task.getStatus() == TaskRequestStatus.STARTED) {
            throw new BadRequestException("Cannot cancel a task that has already started.");
        }
        if (task.getStatus() != TaskRequestStatus.ACCEPTED) {
            throw new BadRequestException("Task is not in a cancellable state.");
        }

        // Reject the accepted quote
        if (task.getAcceptedQuoteId() != null) {
            quoteRepository.findById(task.getAcceptedQuoteId()).ifPresent(q -> {
                q.setStatus(QuoteStatus.REJECTED);
                quoteRepository.save(q);
            });
        }

        task.setStatus(TaskRequestStatus.CANCELLED);
        task.setCancelledBy("PROVIDER");
        task.setCancellationReason(reason);
        task.setCancelledAt(LocalDateTime.now());
        task.setAcceptedQuoteId(null);
        task.setStartOtp(null);
        task.setUpdatedAt(LocalDateTime.now());

        return TaskResponse.from(taskRequestRepository.save(task), false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER: MY QUOTES
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuoteResponse> getMyQuotes(Long providerId) {
        return quoteRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                .stream().map(QuoteResponse::from).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaskResponse> getAllTasksForAdmin() {
        return taskRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(tr -> TaskResponse.from(tr, false)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByStatusForAdmin(TaskRequestStatus status) {
        return taskRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(tr -> TaskResponse.from(tr, false)).collect(Collectors.toList());
    }

    @Transactional
    public TaskResponse adminCancelTask(Long taskId, String reason) {
        TaskRequest task = getTaskOrThrow(taskId);
        if (task.getStatus().isTerminal()) {
            throw new BadRequestException("Task is already in a terminal state: " + task.getStatus());
        }

        List<Quote> activeQuotes = quoteRepository.findByTaskRequestIdAndStatusIn(
                taskId, List.of(QuoteStatus.PENDING, QuoteStatus.COUNTER_OFFERED));
        activeQuotes.forEach(q -> q.setStatus(QuoteStatus.REJECTED));
        quoteRepository.saveAll(activeQuotes);

        task.setStatus(TaskRequestStatus.CANCELLED);
        task.setCancelledBy("ADMIN");
        task.setCancellationReason(reason);
        task.setCancelledAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        return TaskResponse.from(taskRequestRepository.save(task), false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private TaskRequest getTaskOrThrow(Long taskId) {
        return taskRequestRepository.findById(taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
    }

    private Quote getQuoteOrThrow(Long quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Quote", quoteId));
    }

    private void assertOwner(TaskRequest task, Long userId) {
        if (!task.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this task.");
        }
    }

    private void assertQuoteBelongsToTask(Quote quote, Long taskId) {
        if (!quote.getTaskRequest().getId().equals(taskId)) {
            throw new BadRequestException("Quote does not belong to this task.");
        }
    }

    private void assertAcceptedProvider(TaskRequest task, Long providerId) {
        if (task.getAcceptedQuoteId() == null) {
            throw new ForbiddenException("No provider is assigned to this task.");
        }
        Quote acceptedQuote = quoteRepository.findById(task.getAcceptedQuoteId())
                .orElseThrow(() -> new IllegalStateException("Accepted quote not found"));
        if (!acceptedQuote.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("You are not the assigned provider for this task.");
        }
    }

    private void rejectOtherQuotes(Long taskId, Long acceptedQuoteId) {
        List<Quote> others = quoteRepository.findByTaskRequestIdAndStatusIn(
                taskId, List.of(QuoteStatus.PENDING, QuoteStatus.COUNTER_OFFERED));
        others.stream()
                .filter(q -> !q.getId().equals(acceptedQuoteId))
                .forEach(q -> q.setStatus(QuoteStatus.REJECTED));
        quoteRepository.saveAll(others);
    }

    public List<TaskResponse> getTasksForProvider(Long providerId) {
        List<TaskRequest> tasks = taskRequestRepository.findAssignedTasksForProvider(providerId);
        return tasks.stream()
                .map(t -> TaskResponse.from(t, false))
                .collect(Collectors.toList());
    }

    private String generateOtp() {
        return String.format("%04d", 1000 + SECURE_RANDOM.nextInt(9000));
    }

    private PlatformSettings getPlatformSettings() {
        return platformSettingsRepository.findAll().stream().findFirst()
                .orElseGet(PlatformSettings::new);
    }
}
