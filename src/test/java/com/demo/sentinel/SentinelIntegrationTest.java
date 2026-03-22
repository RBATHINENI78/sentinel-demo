package com.demo.sentinel;

import com.demo.sentinel.dto.CreateWorkTaskRequest;
import com.demo.sentinel.dto.UpdateWorkTaskRequest;
import com.demo.sentinel.dto.WorkTaskResponse;
import com.demo.sentinel.entity.WorkTask.TaskStatus;
import com.demo.sentinel.repository.RequestLockRepository;
import com.demo.sentinel.repository.WorkTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Sentinel Integration Tests")
class SentinelIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkTaskRepository workTaskRepository;
    @Autowired RequestLockRepository lockRepository;

    @BeforeEach
    void cleanUp() {
        lockRepository.deleteAll();
        workTaskRepository.deleteAll();
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create task successfully")
    void createTask_success() throws Exception {
        UUID recipientId = UUID.randomUUID();
        CreateWorkTaskRequest request = new CreateWorkTaskRequest(
            recipientId, "Fix login bug", "Users cannot login", "admin");

        mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.recipientId").value(recipientId.toString()))
            .andExpect(jsonPath("$.title").value("Fix login bug"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Should return 409 on second identical request (duplicate prevention)")
    void createTask_duplicateBlocked() throws Exception {
        UUID recipientId = UUID.randomUUID();
        CreateWorkTaskRequest request = new CreateWorkTaskRequest(
            recipientId, "Fix login bug", "Users cannot login", "admin");

        // First request — should succeed
        mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Second request with same recipientId — must be blocked
        mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));

        // Verify only one task was created
        assertThat(workTaskRepository.findByStatus(TaskStatus.ACTIVE)).hasSize(1);
    }

    @Test
    @DisplayName("Should allow new task after previous task is completed")
    void createTask_allowedAfterCompletion() throws Exception {
        UUID recipientId = UUID.randomUUID();
        CreateWorkTaskRequest request = new CreateWorkTaskRequest(
            recipientId, "Task 1", "Description", "admin");

        // Create first task
        MvcResult result = mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        WorkTaskResponse created = objectMapper.readValue(
            result.getResponse().getContentAsString(), WorkTaskResponse.class);

        // Complete the task
        mockMvc.perform(patch("/api/v1/work-tasks/" + created.id() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateWorkTaskRequest(TaskStatus.COMPLETED))))
            .andExpect(status().isOk());

        // Clear the lock manually (simulating TTL expiry)
        lockRepository.deleteAll();

        // Second request with same recipientId — should now succeed
        CreateWorkTaskRequest request2 = new CreateWorkTaskRequest(
            recipientId, "Task 2", "New task", "admin");
        mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should return 400 on missing required fields")
    void createTask_validationError() throws Exception {
        String invalidBody = """
            {
              "title": "Missing recipientId"
            }
            """;

        mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.recipientId").exists());
    }

    // ─── CONCURRENT DUPLICATE TEST ────────────────────────────────────────────

    @Test
    @DisplayName("Should allow exactly ONE task when 5 concurrent requests hit the same recipientId")
    void createTask_concurrentDuplicatesPrevented() throws Exception {
        UUID recipientId = UUID.randomUUID();
        CreateWorkTaskRequest request = new CreateWorkTaskRequest(
            recipientId, "Concurrent task", "Race condition test", "admin");
        String body = objectMapper.writeValueAsString(request);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post("/api/v1/work-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) successCount.incrementAndGet();
            else if (status == 409) conflictCount.incrementAndGet();
        }

        System.out.println("Concurrent test results: " + successCount.get()
                           + " success, " + conflictCount.get() + " conflicts");

        // Exactly one request must succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        // Exactly one row in the database
        assertThat(workTaskRepository.findByStatus(TaskStatus.ACTIVE)).hasSize(1);
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should fetch task by ID")
    void getTask_byId() throws Exception {
        UUID recipientId = UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateWorkTaskRequest(recipientId, "Test", null, "admin"))))
            .andExpect(status().isCreated())
            .andReturn();

        WorkTaskResponse response = objectMapper.readValue(
            created.getResponse().getContentAsString(), WorkTaskResponse.class);

        mockMvc.perform(get("/api/v1/work-tasks/" + response.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    @DisplayName("Should return 404 for unknown task ID")
    void getTask_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/work-tasks/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Should list ACTIVE tasks without being blocked by write locks")
    void listTasks_unaffectedByLocks() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/work-tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateWorkTaskRequest(
                            UUID.randomUUID(), "Task " + i, null, "admin"))))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/work-tasks?status=ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should update task status")
    void updateTaskStatus_success() throws Exception {
        UUID recipientId = UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateWorkTaskRequest(recipientId, "Test", null, "admin"))))
            .andExpect(status().isCreated())
            .andReturn();

        WorkTaskResponse task = objectMapper.readValue(
            created.getResponse().getContentAsString(), WorkTaskResponse.class);

        mockMvc.perform(patch("/api/v1/work-tasks/" + task.id() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateWorkTaskRequest(TaskStatus.COMPLETED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should delete task")
    void deleteTask_success() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/work-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateWorkTaskRequest(UUID.randomUUID(), "Delete me", null, "admin"))))
            .andExpect(status().isCreated())
            .andReturn();

        WorkTaskResponse task = objectMapper.readValue(
            created.getResponse().getContentAsString(), WorkTaskResponse.class);

        mockMvc.perform(delete("/api/v1/work-tasks/" + task.id()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/work-tasks/" + task.id()))
            .andExpect(status().isNotFound());
    }
}
