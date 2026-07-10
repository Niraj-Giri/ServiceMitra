-- Make booking_id nullable in chat_messages
ALTER TABLE chat_messages MODIFY COLUMN booking_id BIGINT NULL;

-- Add task_request_id column to chat_messages
ALTER TABLE chat_messages ADD COLUMN task_request_id BIGINT NULL;

-- Add foreign key constraint for task_request_id
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_task FOREIGN KEY (task_request_id) REFERENCES task_requests(id);

-- Add index
CREATE INDEX idx_chat_task ON chat_messages(task_request_id);
