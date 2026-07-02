package com.mitra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import com.mitra.entity.User;
import com.mitra.entity.UserRepository;

@SpringBootApplication
public class MechanicApplication {

    public static void main(String[] args) {
        SpringApplication.run(MechanicApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(ProviderRepository providerRepo, UserRepository userRepo) {
        return args -> {
            // Seed Admin
            if (userRepo.findByEmail("admin@servicemitra.com").isEmpty()) {
                User admin = new User();
                admin.setFullName("System Admin");
                admin.setEmail("admin@servicemitra.com");
                admin.setPhone("0000000000");
                admin.setPassword("admin123");
                admin.setRole("ADMIN");
                userRepo.save(admin);
            }

            // Seed Providers
            if (providerRepo.count() == 0) {
                Provider p1 = new Provider();
                p1.setFullName("Raju Sharma");
                p1.setEmail("raju@example.com");
                p1.setPhone("9876543210");
                p1.setPassword("password123");
                p1.setBusinessName("Raju Auto Works");
                p1.setServiceCategory("AUTO_MECHANIC");
                p1.setAddress("Lodhi Colony, New Delhi");
                p1.setStatus("APPROVED");
                p1.setLatitude(28.5916);
                p1.setLongitude(77.2272);
                providerRepo.save(p1);

                Provider p2 = new Provider();
                p2.setFullName("Suresh Verma");
                p2.setEmail("suresh@example.com");
                p2.setPhone("9876543211");
                p2.setPassword("password123");
                p2.setBusinessName("Suresh AC Repairs");
                p2.setServiceCategory("AC_TECHNICIAN");
                p2.setAddress("Lajpat Nagar, New Delhi");
                p2.setStatus("APPROVED");
                p2.setLatitude(28.5677);
                p2.setLongitude(77.2433);
                providerRepo.save(p2);
                
                Provider p3 = new Provider();
                p3.setFullName("Amit Plumbing");
                p3.setEmail("amit@example.com");
                p3.setPhone("9876543212");
                p3.setPassword("password123");
                p3.setBusinessName("Amit Plumbers");
                p3.setServiceCategory("PLUMBER");
                p3.setAddress("South Extension, New Delhi");
                p3.setStatus("APPROVED");
                p3.setLatitude(28.5684);
                p3.setLongitude(77.2205);
                providerRepo.save(p3);
            }
        };
    }
}
