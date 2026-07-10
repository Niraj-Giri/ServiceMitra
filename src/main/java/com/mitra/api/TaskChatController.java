package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.chat.ChatMessage;
import com.mitra.chat.ChatMessageRepository;
import com.mitra.common.ApiResponse;
import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.taskrequests.TaskRequest;
import com.mitra.taskrequests.TaskRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/tasks/{taskId}/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final TaskRequestRepository taskRequestRepository;
    private final AuthService authService;

    /**
     * GET /api/v1/chat/tasks/{taskId}/messages
     * Returns all chat messages for a task request.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getMessages(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String role = authService.extractRoleFromToken(httpRequest);
        getAndVerifyAccess(taskId, userId, role);

        List<ChatMessage> messages = chatMessageRepository.findByTaskRequestIdOrderByCreatedAtAsc(taskId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    /**
     * POST /api/v1/chat/tasks/{taskId}/messages
     * Send a message.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            HttpServletRequest httpRequest,
            @PathVariable Long taskId,
            @Valid @RequestBody ChatController.SendMessageRequest request) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String role = authService.extractRoleFromToken(httpRequest);
        TaskRequest task = getAndVerifyAccess(taskId, userId, role);

        if (task.getStatus().isTerminal()) {
            throw new BadRequestException("Chat is disabled for completed, cancelled or expired tasks.");
        }

        ChatMessage message = ChatMessage.builder()
                .taskRequest(task)
                .senderId(userId)
                .senderRole(role.toUpperCase())
                .content(request.getContent().trim())
                .build();

        chatMessageRepository.save(message);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(message));
    }

    private TaskRequest getAndVerifyAccess(Long taskId, Long userId, String role) {
        TaskRequest task = taskRequestRepository.findById(taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));

        boolean isCustomer = "CUSTOMER".equalsIgnoreCase(role)
                && task.getUser() != null
                && task.getUser().getId().equals(userId);

        boolean isProvider = "PROVIDER".equalsIgnoreCase(role);
        if (isProvider) {
            // Must have a bid or be assigned
            boolean hasQuote = task.getQuotes() != null && task.getQuotes().stream()
                    .anyMatch(q -> q.getProvider() != null && q.getProvider().getId().equals(userId));
            if (!hasQuote) {
                throw ForbiddenException.notYourResource("chat");
            }
        } else if (!isCustomer) {
            throw ForbiddenException.notYourResource("chat");
        }

        return task;
    }
}
