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
	private com.mitra.users.ProviderRepository providerRepository;

	@Test
	void dbTest() {
		try {
			System.out.println("====== JPQL findProviders TEST ======");
			org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
			org.springframework.data.domain.Page<com.mitra.users.Provider> res = providerRepository.findProviders("", "", pageable);
			System.out.println("RESULT COUNT: " + res.getTotalElements());
			for (com.mitra.users.Provider p : res.getContent()) {
				System.out.println(" - " + p.getId() + ": " + p.getBusinessName() + " (" + p.getStatus() + ")");
			}
			System.out.println("=========================================");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
