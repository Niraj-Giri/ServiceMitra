package com.niraj.MechanicMitra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.mitra.MechanicApplication.class)
class MechanicApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.taskrequests.TaskRequestRepository taskRequestRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.taskrequests.QuoteRepository quoteRepository;

	@Test
	void contextLoads() {
	}

	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.taskrequests.TaskRequestService taskRequestService;

	@Test
	void dbTest() {
		try {
			System.out.println("====== ATTEMPTING START TASK SERVICE DIRECTLY ======");
			// Task 1 status is ACCEPTED, acceptedQuoteId is 2 (provider 9)
			taskRequestService.startTask(9L, 1L);
			System.out.println("SUCCESS: Task started successfully!");
		} catch (Exception e) {
			System.out.println("EXCEPTION THROWN: " + e.getClass().getName() + " - " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("=========================================");
	}

}
