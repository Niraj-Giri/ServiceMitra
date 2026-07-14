package com.niraj.MechanicMitra;

import com.mitra.common.BadRequestException;
import com.mitra.common.RewardPointsHistoryRepository;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.taskrequests.TaskRequest;
import com.mitra.taskrequests.TaskRequestRepository;
import com.mitra.taskrequests.TaskRequestService;
import com.mitra.taskrequests.dto.CreateTaskRequest;
import com.mitra.taskrequests.dto.TaskResponse;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskRequestServiceTest {

    @Mock
    private TaskRequestRepository taskRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceListingRepository serviceListingRepository;

    @Mock
    private RewardPointsHistoryRepository rewardPointsHistoryRepository;

    @Mock
    private com.mitra.config.PlatformSettingsRepository platformSettingsRepository;

    @InjectMocks
    private TaskRequestService taskRequestService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(platformSettingsRepository.findAll()).thenReturn(Collections.singletonList(new com.mitra.config.PlatformSettings()));
    }

    @Test
    void testCreateTaskWithPointsDiscountCapping() {
        // Arrange
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .name("Niraj")
                .phone("9800000000")
                .rewardPoints(1000)
                .isActive(true)
                .build();

        ServiceListing service = ServiceListing.builder()
                .id(1L)
                .name("Plumbing")
                .category("PLUMBING")
                .basePrice(BigDecimal.valueOf(100.00))
                .isActive(true)
                .build();

        CreateTaskRequest req = new CreateTaskRequest();
        req.setServiceId(1L);
        req.setTitle("Kitchen Leaking");
        req.setDescription("Kitchen tap leaking repair");
        req.setBudgetMinNpr(BigDecimal.valueOf(120.00));
        req.setBudgetMaxNpr(BigDecimal.valueOf(150.00));
        req.setPointsToRedeem(500); // 500 points = Rs. 500 discount (redemption rate = 1)
        req.setPreferredSlots(Collections.singletonList("Morning"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceListingRepository.findById(1L)).thenReturn(Optional.of(service));
        when(taskRequestRepository.save(any(TaskRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TaskResponse response = taskRequestService.createTask(userId, req);

        // Assert: Discount must be capped by the service basePrice (Rs. 100) instead of applying Rs. 500
        assertEquals(BigDecimal.valueOf(100.00), response.getPointsDiscountNpr());
        assertEquals(100, response.getPointsRedeemed()); // 100 points deducted instead of 500
        assertEquals(900, user.getRewardPoints()); // 1000 - 100
        verify(rewardPointsHistoryRepository, times(1)).save(any());
    }

    @Test
    void testCreateTaskWithInactiveServiceThrowsException() {
        // Arrange
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .name("Niraj")
                .phone("9800000000")
                .rewardPoints(100)
                .isActive(true)
                .build();

        ServiceListing service = ServiceListing.builder()
                .id(1L)
                .name("Plumbing")
                .isActive(false) // Inactive
                .build();

        CreateTaskRequest req = new CreateTaskRequest();
        req.setServiceId(1L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(serviceListingRepository.findById(1L)).thenReturn(Optional.of(service));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> taskRequestService.createTask(userId, req));
    }
}
