package com.niraj.MechanicMitra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.mitra.MechanicApplication.class)
class MechanicApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.taskrequests.TaskRequestRepository taskRequestRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.taskrequests.QuoteRepository quoteRepository;

	@org.springframework.beans.factory.annotation.Autowired
	private com.mitra.services.ServiceListingRepository serviceListingRepository;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		try {
			System.out.println("====== APPLYING SCHEMA PATCH ======");
			try {
				jdbcTemplate.execute("ALTER TABLE services ADD COLUMN image_url VARCHAR(255) NULL;");
			} catch (Exception ignored) {}
			jdbcTemplate.execute("UPDATE services SET is_active = TRUE WHERE is_active IS NULL;");
			System.out.println("====== JPQL findServices TEST ======");
			java.util.List<com.mitra.services.ServiceListing> res = serviceListingRepository.findAll();
			System.out.println("RESULT COUNT: " + res.size());
			for (com.mitra.services.ServiceListing s : res) {
				System.out.println(" - " + s.getId() + ": " + s.getName() + " (imageUrl=" + s.getImageUrl() + ")");
			}
			System.out.println("=========================================");
		} catch (Exception e) {
			System.err.println("=== ERROR IN SERVICE DB TEST ===");
			e.printStackTrace();
			System.err.println("=================================");
		}
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
