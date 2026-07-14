package com.mitra.config;

import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Startup seeder and schema synchronizer.
 * Runs SQL ALTER queries on boot to ensure the database matches our entities,
 * then seeds default services if empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final ServiceListingRepository serviceListingRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting database schema synchronization check...");

        // 0. Synchronize User columns
        executeAlterSafe("ALTER TABLE users ADD COLUMN profile_photo VARCHAR(255) NULL");
        executeAlterSafe("ALTER TABLE providers MODIFY COLUMN user_id BIGINT NULL");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN name VARCHAR(255) NULL");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN age INT NULL");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN description TEXT NULL");

        // 1. Synchronize OtpVerification columns
        executeAlterSafe("ALTER TABLE otp_verifications MODIFY COLUMN otp VARCHAR(6) NOT NULL");
        executeAlterSafe("ALTER TABLE otp_verifications MODIFY COLUMN phone VARCHAR(20) NOT NULL");
        executeAlterSafe("ALTER TABLE otp_verifications ADD COLUMN attempt_count TINYINT DEFAULT 0");
        executeAlterSafe("ALTER TABLE otp_verifications ADD COLUMN is_used BOOLEAN DEFAULT FALSE");
        executeAlterSafe("ALTER TABLE otp_verifications ADD COLUMN used_at DATETIME NULL");

        // 2. Synchronize Provider columns
        executeAlterSafe("ALTER TABLE providers ADD COLUMN total_jobs INT DEFAULT 0");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN admin_notes TEXT");
        executeAlterSafe("ALTER TABLE providers MODIFY COLUMN status VARCHAR(30) DEFAULT 'PENDING_REVIEW'");
        executeAlterSafe("ALTER TABLE providers MODIFY COLUMN rating_cache DECIMAL(3,2) DEFAULT 0.00");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN bank_details VARCHAR(255) NULL");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN certificates_urls TEXT NULL");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN acceptance_rate DECIMAL(5, 2) NOT NULL DEFAULT 100.00");
        executeAlterSafe("ALTER TABLE providers ADD COLUMN commission_percentage DECIMAL(5, 2) NULL");

        // 2.5 Create Admin Operations Tables
        executeAlterSafe("CREATE TABLE IF NOT EXISTS platform_settings (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "platform_name VARCHAR(100) NOT NULL DEFAULT 'ServiceMitra'," +
                "commission_percentage DECIMAL(5, 2) NOT NULL DEFAULT 10.00," +
                "support_number VARCHAR(20) NOT NULL DEFAULT '9800000000'," +
                "cancellation_policy TEXT NULL," +
                "auto_assignment_rules VARCHAR(100) DEFAULT 'CLOSEST_DISTANCE'," +
                "booking_radius DOUBLE DEFAULT 15.0," +
                "working_hours VARCHAR(100) DEFAULT '08:00-20:00'," +
                "tax_settings VARCHAR(100) DEFAULT 'NONE'," +
                "payment_gateway VARCHAR(50) DEFAULT 'COD')");

        executeAlterSafe("INSERT INTO platform_settings (platform_name, commission_percentage, support_number) " +
                "SELECT 'ServiceMitra', 10.00, '9800000000' FROM DUAL " +
                "WHERE NOT EXISTS (SELECT * FROM platform_settings)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS audit_logs (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "admin VARCHAR(100) NOT NULL," +
                "action TEXT NOT NULL," +
                "entity VARCHAR(100) NULL," +
                "old_value TEXT NULL," +
                "new_value TEXT NULL," +
                "timestamp DATETIME NOT NULL," +
                "ip_address VARCHAR(45) NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS complaints (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "booking_id BIGINT NOT NULL," +
                "customer_id BIGINT NOT NULL," +
                "provider_id BIGINT NOT NULL," +
                "subject VARCHAR(255) NOT NULL," +
                "description TEXT NOT NULL," +
                "status VARCHAR(30) NOT NULL DEFAULT 'PENDING'," +
                "priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'," +
                "evidence_url VARCHAR(255) NULL," +
                "created_at DATETIME NOT NULL," +
                "resolved_at DATETIME NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS complaint_messages (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "complaint_id BIGINT NOT NULL," +
                "sender_id BIGINT NOT NULL," +
                "sender_role VARCHAR(20) NOT NULL," +
                "content TEXT NOT NULL," +
                "created_at DATETIME NOT NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS transactions (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "booking_id BIGINT NOT NULL," +
                "customer_id BIGINT NOT NULL," +
                "provider_id BIGINT NOT NULL," +
                "amount DECIMAL(10, 2) NOT NULL," +
                "commission DECIMAL(10, 2) NOT NULL," +
                "provider_earnings DECIMAL(10, 2) NOT NULL," +
                "status VARCHAR(30) NOT NULL DEFAULT 'PENDING'," +
                "transaction_id VARCHAR(100) NOT NULL," +
                "created_at DATETIME NOT NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS payout_requests (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "provider_id BIGINT NOT NULL," +
                "amount DECIMAL(10, 2) NOT NULL," +
                "status VARCHAR(30) NOT NULL DEFAULT 'PENDING'," +
                "created_at DATETIME NOT NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS booking_status_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "booking_id BIGINT NOT NULL," +
                "status VARCHAR(30) NOT NULL," +
                "updated_at DATETIME NOT NULL," +
                "updated_by VARCHAR(50) NOT NULL," +
                "notes TEXT NULL)");

        // 3. Synchronize Booking columns
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN otp_generated_at DATETIME NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN started_at DATETIME NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN completed_at DATETIME NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN cancelled_at DATETIME NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN cancelled_by VARCHAR(20) NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT NULL");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN rejected_provider_ids VARCHAR(500) NULL");
        executeAlterSafe("ALTER TABLE bookings MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_DISPATCH'");
        executeAlterSafe("UPDATE bookings SET status = 'PENDING_DISPATCH' WHERE status = 'REQUESTED'");
        executeAlterSafe("DELETE FROM booking_status_history WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users))");
        executeAlterSafe("DELETE FROM chat_messages WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users))");
        executeAlterSafe("DELETE FROM reviews WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users))");
        executeAlterSafe("DELETE FROM complaints WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users))");
        executeAlterSafe("DELETE FROM transactions WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users))");
        executeAlterSafe("DELETE FROM bookings WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM users)");
        executeAlterSafe("UPDATE bookings SET provider_id = NULL WHERE provider_id = 0 OR (provider_id IS NOT NULL AND provider_id NOT IN (SELECT id FROM providers))");

        // 4. Synchronize Service Listing columns
        executeAlterSafe("ALTER TABLE services ADD COLUMN duration_min INT NULL");
        executeAlterSafe("ALTER TABLE services ADD COLUMN what_included TEXT NULL");
        executeAlterSafe("ALTER TABLE services ADD COLUMN what_excluded TEXT NULL");

        // 5. Synchronize Review (Rating) columns
        executeAlterSafe("ALTER TABLE reviews ADD COLUMN punctuality_score TINYINT NOT NULL DEFAULT 5");
        executeAlterSafe("ALTER TABLE reviews ADD COLUMN quality_score TINYINT NOT NULL DEFAULT 5");
        executeAlterSafe("ALTER TABLE reviews ADD COLUMN behavior_score TINYINT NOT NULL DEFAULT 5");
        executeAlterSafe("ALTER TABLE reviews ADD COLUMN overall_score DECIMAL(3,2) NOT NULL DEFAULT 5.00");
        executeAlterSafe("ALTER TABLE reviews ADD COLUMN is_visible BOOLEAN DEFAULT TRUE");
        executeAlterSafe("ALTER TABLE reviews MODIFY COLUMN rating INT NULL");
        executeAlterSafe("ALTER TABLE reviews ADD UNIQUE INDEX uk_reviews_booking_id (booking_id)");

        // 6. Add performance indexes
        executeIndexSafe("idx_bookings_provider_id", "ALTER TABLE bookings ADD INDEX idx_bookings_provider_id (provider_id)");
        executeIndexSafe("idx_bookings_user_id", "ALTER TABLE bookings ADD INDEX idx_bookings_user_id (user_id)");
        executeIndexSafe("idx_bookings_status", "ALTER TABLE bookings ADD INDEX idx_bookings_status (status)");
        executeIndexSafe("idx_bookings_created_at", "ALTER TABLE bookings ADD INDEX idx_bookings_created_at (created_at)");
        executeIndexSafe("idx_bookings_scheduled_at", "ALTER TABLE bookings ADD INDEX idx_bookings_scheduled_at (scheduled_at)");
        executeIndexSafe("idx_providers_status", "ALTER TABLE providers ADD INDEX idx_providers_status (status)");
        executeIndexSafe("idx_providers_category", "ALTER TABLE providers ADD INDEX idx_providers_category (service_category)");
        executeIndexSafe("idx_otp_phone", "ALTER TABLE otp_verifications ADD INDEX idx_otp_phone (phone)");
        executeIndexSafe("idx_reviews_provider_id", "ALTER TABLE reviews ADD INDEX idx_reviews_provider_id (provider_id)");

        // 6.5 Reward Points & Provider Incentives Schema Synchronization
        executeAlterSafe("ALTER TABLE users ADD COLUMN reward_points INT NOT NULL DEFAULT 0");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN points_redeemed INT NULL DEFAULT 0");
        executeAlterSafe("ALTER TABLE bookings ADD COLUMN points_discount_npr DECIMAL(12,2) NULL DEFAULT 0.00");
        executeAlterSafe("ALTER TABLE platform_settings ADD COLUMN points_per_npr_spent DECIMAL(5,2) NOT NULL DEFAULT 0.10");
        executeAlterSafe("ALTER TABLE platform_settings ADD COLUMN points_redemption_rate DECIMAL(5,2) NOT NULL DEFAULT 1.00");
        executeAlterSafe("ALTER TABLE platform_settings ADD COLUMN first_booking_points_bonus INT NOT NULL DEFAULT 50");
        executeAlterSafe("ALTER TABLE platform_settings ADD COLUMN referral_points_bonus INT NOT NULL DEFAULT 30");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS reward_points_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "points INT NOT NULL," +
                "action_type VARCHAR(50) NOT NULL," +
                "description VARCHAR(255) NULL," +
                "booking_id BIGINT NULL," +
                "created_at DATETIME NOT NULL)");

        executeAlterSafe("CREATE TABLE IF NOT EXISTS provider_incentives (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "provider_id BIGINT NOT NULL," +
                "amount DECIMAL(12,2) NOT NULL," +
                "booking_id BIGINT NULL," +
                "reason VARCHAR(50) NOT NULL," +
                "description VARCHAR(255) NULL," +
                "status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYOUT'," +
                "created_at DATETIME NOT NULL," +
                "payout_id BIGINT NULL)");

        // 7. Normalize legacy status naming
        try {
            jdbcTemplate.execute("UPDATE bookings SET status = 'COMPLETED' WHERE status IN ('completed', 'COMPLETE')");
            jdbcTemplate.execute("UPDATE bookings SET status = 'CANCELLED_BY_CUSTOMER' WHERE status IN ('cancelled', 'CANCELLED')");
            jdbcTemplate.execute("UPDATE bookings SET status = 'PENDING_DISPATCH' WHERE status IN ('pending', 'PENDING', 'PENDING_DISPATCH')");
        } catch (Exception e) {
            log.warn("Could not update legacy booking status values: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("UPDATE services SET category = 'ELECTRICAL' WHERE name = 'Electrician' OR id = 1");
            jdbcTemplate.execute("UPDATE services SET category = 'PLUMBING' WHERE name = 'Plumber' OR id = 2");
            jdbcTemplate.execute("UPDATE services SET category = 'ELECTRICAL' WHERE name = 'AC Repair' OR id = 3");
            log.info("Synchronized existing database service categories to match provider category tags.");
        } catch (Exception e) {
            log.warn("Could not update service categories: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("DROP TABLE IF EXISTS mechanics");
            jdbcTemplate.execute("DROP TABLE IF EXISTS provider_profiles");
            jdbcTemplate.execute("DROP TABLE IF EXISTS service_categories");
            jdbcTemplate.execute("DROP TABLE IF EXISTS service_subcategories");

            // Drop old reviews -> bookings foreign key constraint if it exists
            try {
                jdbcTemplate.execute("ALTER TABLE reviews DROP FOREIGN KEY FK28an517hrxtt2bsg93uefugrm");
                log.info("Dropped legacy reviews -> bookings foreign key constraint.");
            } catch (Exception ex) {
                // Ignore if it doesn't exist or was already dropped
            }

            // Add new reviews -> task_requests foreign key constraint
            try {
                jdbcTemplate.execute("ALTER TABLE reviews ADD CONSTRAINT FK_reviews_task_requests FOREIGN KEY (booking_id) REFERENCES task_requests (id)");
                log.info("Successfully linked reviews table to task_requests table.");
            } catch (Exception ex) {
                // Ignore if already added
            }

            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            log.info("Successfully dropped unused legacy tables and updated foreign key constraints.");
        } catch (Exception e) {
            log.warn("Could not drop unused tables or migrate constraints: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE booking_status_history");
            jdbcTemplate.execute("TRUNCATE TABLE chat_messages");
            jdbcTemplate.execute("TRUNCATE TABLE reviews");
            jdbcTemplate.execute("TRUNCATE TABLE complaints");
            jdbcTemplate.execute("TRUNCATE TABLE bookings");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            log.info("Successfully removed all legacy bookings and associated records from the database.");
        } catch (Exception e) {
            log.warn("Could not clean up legacy bookings: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("DELETE FROM quotes WHERE task_request_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM chat_messages WHERE task_request_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM transactions WHERE booking_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM provider_incentives WHERE booking_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM coupon_usages WHERE task_request_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM complaints WHERE booking_id IN (SELECT id FROM task_requests WHERE created_at < '2026-07-13 00:00:00')");
            jdbcTemplate.execute("DELETE FROM task_requests WHERE created_at < '2026-07-13 00:00:00'");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            log.info("Successfully deleted all marketplace task requests (bookings) created before 13 July 2026.");
        } catch (Exception e) {
            log.warn("Could not clean up legacy task requests: {}", e.getMessage());
        }

        log.info("Database schema check complete. Validating default service seeds...");

        // 8. Seed default services
        if (serviceListingRepository.count() < 10) {
            serviceListingRepository.deleteAll();

            ServiceListing s1 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Ceiling Fan Repair, Noise Diagnosis & Condenser Replacement")
                    .description("Fix squeaking noises, low speed issues, or dead ceiling fans. Our expert will inspect the regulator, wiring, and replace the condenser if faulty.")
                    .basePrice(new BigDecimal("600.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("Labor charge, Condenser testing, Wire insulation check, 30-day service warranty")
                    .whatExcluded("New condenser cost, Regulator replacement, Motor winding repair")
                    .isActive(true)
                    .build();

            ServiceListing s2 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Complete LED Tube Light & Fancy Chandelier Installation")
                    .description("Get your new LED tube lights, decorative panel lights, or heavy fancy glass chandeliers securely mounted and wired on your ceiling or walls.")
                    .basePrice(new BigDecimal("450.00"))
                    .priceType("STARTING_AT")
                    .durationMin(45)
                    .whatIncluded("Chandelier bracket mounting, Basic ceiling wiring connection, Functional testing")
                    .whatExcluded("Cost of Chandelier/Light fixtures, Scaffold rental for double-height ceilings, Heavy-duty anchor bolts")
                    .isActive(true)
                    .build();

            ServiceListing s3 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("AC Filter Cleaning, Gas Leakage Testing & Coolant Top-Up")
                    .description("Full servicing of split or window air conditioners. Includes high-pressure jet cleaning of filters and testing for refrigerant gas leaks.")
                    .basePrice(new BigDecimal("1800.00"))
                    .priceType("FIXED")
                    .durationMin(90)
                    .whatIncluded("Split indoor and outdoor unit dust cleaning, Filter wash, Gas pressure gauge reading check")
                    .whatExcluded("Refrigerant gas cylinder refill charges, Copper piping repair, Bracket installation")
                    .isActive(true)
                    .build();

            ServiceListing s4 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Inverter Installation & Battery Water Level Check")
                    .description("Setup of home backup power inverter system. Includes mounting, wiring to mains distribution board, and checking battery acid/water levels.")
                    .basePrice(new BigDecimal("1200.00"))
                    .priceType("FIXED")
                    .durationMin(90)
                    .whatIncluded("Inverter unit testing, Connection to battery bank, Main bypass switch installation, Water level top-up")
                    .whatExcluded("Distilled water cost, Inverter battery replacement, Main wiring cables")
                    .isActive(true)
                    .build();

            ServiceListing s5 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Distribution Board Fuse & MCB Circuit Breaker Troubleshooting")
                    .description("Diagnose frequent power trips, smelling burnt wires, or dead circuit outlets. We test and replace faulty MCB breakers and fuses.")
                    .basePrice(new BigDecimal("800.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("Fault search with multimeter, DB box inspection, Terminal screw tightening")
                    .whatExcluded("Replacement MCB breakers, RCD safety switch parts, Main meter replacement")
                    .isActive(true)
                    .build();

            ServiceListing s6 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Smart Home Wi-Fi Switch Board Installation & Wiring Setup")
                    .description("Upgrade your standard switches to smart Wi-Fi enabled touch plates. Allows mobile and voice control of lights and appliances.")
                    .basePrice(new BigDecimal("1500.00"))
                    .priceType("STARTING_AT")
                    .durationMin(120)
                    .whatIncluded("Smart switch fitting, Neutral wire connection setup, Wi-Fi pairing assistance")
                    .whatExcluded("Smart switch hardware, Smart home hub device, Internet connection setup")
                    .isActive(true)
                    .build();

            ServiceListing s7 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Water Geyser Heating Element & Thermostat Replacement")
                    .description("Fix cold water issues in your geyser. We replace failed heating coils, thermostats, and check safety release valves.")
                    .basePrice(new BigDecimal("1000.00"))
                    .priceType("FIXED")
                    .durationMin(75)
                    .whatIncluded("Heating element replacement labor, Thermostat calibration, Water leak check")
                    .whatExcluded("Spare heating element cost, New thermostat cost, Inlet/outlet pipe hose fittings")
                    .isActive(true)
                    .build();

            ServiceListing s8 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Kitchen Chimney / Exhaust Fan Deep Cleaning & Motor Repair")
                    .description("Remove thick grease and carbon build-up from your kitchen exhaust chimney. Includes motor diagnostic check and filter cleaning.")
                    .basePrice(new BigDecimal("1500.00"))
                    .priceType("FIXED")
                    .durationMin(120)
                    .whatIncluded("Chimney filter chemical wash, Blower motor cleaning, External steel polish")
                    .whatExcluded("Blower motor spare replacement, Flexible duct pipe, Activated charcoal filters")
                    .isActive(true)
                    .build();

            ServiceListing s9 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Doorbell & Video Intercom System Wiring & Installation")
                    .description("Install electric calling bell, wireless bell, or smart video door phones with security monitoring screen setup.")
                    .basePrice(new BigDecimal("700.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("Outdoor unit mounting, Indoor chime wiring, Screen installation, Configuration")
                    .whatExcluded("Intercom device kit, Extra security camera wiring, Concealed wall channelling")
                    .isActive(true)
                    .build();

            ServiceListing s10 = ServiceListing.builder()
                    .category("ELECTRICAL")
                    .name("Underground / Wall Concealed Copper Wiring Fault Diagnostics")
                    .description("Find hidden electrical short circuits, wire breaks, or dampness issues causing leakage currents in your wall pipes.")
                    .basePrice(new BigDecimal("2000.00"))
                    .priceType("HOURLY")
                    .durationMin(120)
                    .whatIncluded("Megger testing for insulation, Wall wire routing tracing, Phase balance check")
                    .whatExcluded("Concealed wall concrete cutting, New copper wiring cables, Re-plastering of walls")
                    .isActive(true)
                    .build();

            ServiceListing s11 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Leaky Tap Repair, Thread Sealant & Spindle Replacement")
                    .description("Stop water wastage from leaking bathroom or kitchen taps. We clean internal scale and replace worn-out spindles or washer seals.")
                    .basePrice(new BigDecimal("350.00"))
                    .priceType("FIXED")
                    .durationMin(30)
                    .whatIncluded("Spindle inspection, Washer replacement, Teflon thread tape packing, Gasket replacement")
                    .whatExcluded("New tap mixer body, Inlet angle valves, Decorative cover caps")
                    .isActive(true)
                    .build();

            ServiceListing s12 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Kitchen Washbasin Pipe Unclogging & Trap Cleaning Service")
                    .description("Restore smooth drainage in your kitchen sinks. We remove accumulated food waste, grease blockages, and clean the bottle trap.")
                    .basePrice(new BigDecimal("400.00"))
                    .priceType("FIXED")
                    .durationMin(45)
                    .whatIncluded("Bottle trap disassembly, Drain spring pipe cleaning, Blockage clearance, Leak test")
                    .whatExcluded("New sink waste coupling, Corrugated flexible waste pipe, Wall pipe replacement")
                    .isActive(true)
                    .build();

            ServiceListing s13 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Overhead Water Tank Cleaning, Sanitization & Valve Fitting")
                    .description("Deep scrub cleaning of your underground or rooftop plastic/metal water tank. Includes scrubbing silt, vacuuming sludge, and sanitizing.")
                    .basePrice(new BigDecimal("2000.00"))
                    .priceType("FIXED")
                    .durationMin(150)
                    .whatIncluded("Tank empty pumpout, Mud extraction, Side wall wire brush scrubbing, Chlorine sanitization")
                    .whatExcluded("Repairing tank cracks, Float auto-off switch, Outlet valve replacement")
                    .isActive(true)
                    .build();

            ServiceListing s14 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Solar Water Heater Air Release Valve & Pipe Join Fixes")
                    .description("Fix hot water pressure drops or pipe leaks on your rooftop solar water heater system. Includes testing air vent valves.")
                    .basePrice(new BigDecimal("900.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("High-temperature CPVC pipe jointing, Air release valve replacement labor, Solar panel seal check")
                    .whatExcluded("Rooftop solar inner tank welding, Glass tube replacement, Replacement valves")
                    .isActive(true)
                    .build();

            ServiceListing s15 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Commode Flush Tank Leak Repair & Syphon Replacement")
                    .description("Fix continuous water running into the commode bowl. We repair flush buttons, syphon valves, and ball cocks.")
                    .basePrice(new BigDecimal("500.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("Flush mechanism testing, Internal seal cleaning, Syphon alignment adjustments")
                    .whatExcluded("Flush kit/Syphon assembly spares, Ceramic commode cracks, Jet spray replacement")
                    .isActive(true)
                    .build();

            ServiceListing s16 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Bathroom Shower Mixer Crane & Multi-Flow Shower Head Installation")
                    .description("Install your premium hot and cold water shower mixer units, rain shower heads, and hand showers with perfect wall sealing.")
                    .basePrice(new BigDecimal("1200.00"))
                    .priceType("FIXED")
                    .durationMin(90)
                    .whatIncluded("Wall flange alignment, Mixer body mounting, Hand shower stand drilling, Sealant application")
                    .whatExcluded("Mixer unit hardware, Connection hoses, Internal concealed piping leaks")
                    .isActive(true)
                    .build();

            ServiceListing s17 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Automatic Booster Pressure Pump Repair & Installation")
                    .description("Resolve low shower pressure. We install or repair automatic booster pressure pumps with electrical bypass systems.")
                    .basePrice(new BigDecimal("1800.00"))
                    .priceType("FIXED")
                    .durationMin(120)
                    .whatIncluded("Pump mounting, Inlet/outlet union joints, Flow sensor wiring, Run testing")
                    .whatExcluded("Booster pump machine, Main electrical power point setup, Pressure tank bladder")
                    .isActive(true)
                    .build();

            ServiceListing s18 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Washing Machine Inlet & Outlet Drain Pipe Installation")
                    .description("Connect your new front-load or top-load washing machine to clean water supply lines and drain outlets without floor flooding.")
                    .basePrice(new BigDecimal("450.00"))
                    .priceType("FIXED")
                    .durationMin(45)
                    .whatIncluded("Tap adaptor fitting, Inlet hose link, Drain pipe routing, Drainage clamp fitting")
                    .whatExcluded("Washing machine tap, Longer inlet hose extension, Floor drain trap")
                    .isActive(true)
                    .build();

            ServiceListing s19 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Main Water Supply Pipeline Leak Detection & Joining Repair")
                    .description("Locate and patch pipe burst cracks in your GI, PPR, or PVC main water supply pipelines under lawns or flooring.")
                    .basePrice(new BigDecimal("1500.00"))
                    .priceType("STARTING_AT")
                    .durationMin(120)
                    .whatIncluded("Pressure decay testing, Pipe exposing digging, Patch jointing, Sand refilling")
                    .whatExcluded("Tile cutting & reinstatement, Large pipe bypass loops, Excavator machinery")
                    .isActive(true)
                    .build();

            ServiceListing s20 = ServiceListing.builder()
                    .category("PLUMBING")
                    .name("Bathroom Floor Drain Grating & Anti-Odor Trap Fitting")
                    .description("Stop foul bathroom smells and cockroaches. We fit anti-odor silicone traps and replacement stainless steel gratings.")
                    .basePrice(new BigDecimal("500.00"))
                    .priceType("FIXED")
                    .durationMin(45)
                    .whatIncluded("Old grating removal, Cement floor cleaning, Odor trap insertion, Waterproof grouting")
                    .whatExcluded("Stainless steel grating cost, Ceramic tile matching, Main line sewer jetting")
                    .isActive(true)
                    .build();

            ServiceListing s21 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Full House Deep Cleaning & Kitchen Degreasing for 2BHK Apartment")
                    .description("Complete deep cleaning of your 2BHK home. Includes ceiling cobweb removal, kitchen cabinets grease cleaning, and bathroom scrubbing.")
                    .basePrice(new BigDecimal("3500.00"))
                    .priceType("FIXED")
                    .durationMin(300)
                    .whatIncluded("2 Professional cleaners, Eco-friendly chemical sprays, Floor machine buffing, Bathroom stain scrub")
                    .whatExcluded("Sofa/carpet wet shampooing, Balcony outer wall wash, Storage box internal organizing")
                    .isActive(true)
                    .build();

            ServiceListing s22 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Fabric Sofa Shampooing, Vacuuming & Wet Dirt Extraction")
                    .description("Remove accumulated dust, body oils, and food stains from fabric sofas. Restores brightness and freshness.")
                    .basePrice(new BigDecimal("250.00"))
                    .priceType("STARTING_AT")
                    .durationMin(60)
                    .whatIncluded("Dry vacuuming, Chemical foam spray application, Hand scrub brushing, Wet extraction vacuuming")
                    .whatExcluded("Leather conditioning, Permanent ink stain removal, Cushion stuffing repair")
                    .isActive(true)
                    .build();

            ServiceListing s23 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Bathroom Wall Tile Stain Scrubbing & Deep Sanitization")
                    .description("Get rid of hard water scales, soap scum, and yellow tiles grout stains. Includes sanitizing the commode, tub, and washbasins.")
                    .basePrice(new BigDecimal("800.00"))
                    .priceType("FIXED")
                    .durationMin(90)
                    .whatIncluded("Acid-free tile scrubbing, Chrome tap descaling, Grout bleaching, Steam disinfection")
                    .whatExcluded("Tile replacement, Grout re-sealing, Ceiling paint mold treatment")
                    .isActive(true)
                    .build();

            ServiceListing s24 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Kitchen Cabinets Internal Cleaning & Chimney Exterior Scrubbing")
                    .description("De-clutter and clean your kitchen storage areas. We wipe cabinet drawers internally and wipe down oil build-ups from outside tiles.")
                    .basePrice(new BigDecimal("1200.00"))
                    .priceType("FIXED")
                    .durationMin(150)
                    .whatIncluded("Drawer liner cleaning, Cabinet doors internal/external scrub, Outer chimney hood wipe, Countertop polish")
                    .whatExcluded("Organizing spices (decluttering), Internal chimney oil cup cleaning, Utensil washing")
                    .isActive(true)
                    .build();

            ServiceListing s25 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Premium Mattress Dust Mite Vacuuming & Steam Disinfection")
                    .description("Deep clean your sleeping mattresses to kill bed bugs, dust mites, and bacteria. Recommended for allergy sufferers.")
                    .basePrice(new BigDecimal("1000.00"))
                    .priceType("FIXED")
                    .durationMin(60)
                    .whatIncluded("UV-C light treatment, High-power allergen vacuuming, Hot dry steam injection, Deodorizer spray")
                    .whatExcluded("Deep wet stain washing, Mattress drying wait time, Pillow dry cleaning")
                    .isActive(true)
                    .build();

            ServiceListing s26 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Balcony Floor Power Washing & Window Panel Glass Cleaning")
                    .description("Clear mud deposits, bird droppings, and dust from balconies. Includes wiping window glass surfaces from inside.")
                    .basePrice(new BigDecimal("700.00"))
                    .priceType("FIXED")
                    .durationMin(75)
                    .whatIncluded("Power pressure floor wash, Sliding window track brush out, Glass wiper cleaning")
                    .whatExcluded("Balcony outer wall paint wash, Window glass external high-rise cleaning, Grills rust removal")
                    .isActive(true)
                    .build();

            ServiceListing s27 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Carpet Wet Shampooing, Stain Removal & Deodorization")
                    .description("Wet cleaning of living room carpets. Removes soil and stains while conditioning fibers to bring back softness.")
                    .basePrice(new BigDecimal("800.00"))
                    .priceType("STARTING_AT")
                    .durationMin(90)
                    .whatIncluded("Dry vacuuming, Shampoo foam wash, Soft brush floor machine buff, Water extraction")
                    .whatExcluded("Dry clean only silk carpets, Extreme pet odor disinfection, Fringe repair")
                    .isActive(true)
                    .build();

            ServiceListing s28 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Rebuilt / Post-Construction Site Debris & Fine Dust Deep Clean")
                    .description("Specialist cleanup for newly built or renovated homes. Wipes fine plaster dust, paint spots, and removes drywall debris.")
                    .basePrice(new BigDecimal("5000.00"))
                    .priceType("STARTING_AT")
                    .durationMin(360)
                    .whatIncluded("Plaster dust vacuuming, Paint spot floor scraping, Tile acid wash, Window frame cleaning")
                    .whatExcluded("Debris haulage truck rental, Heavy concrete block removal, Painting touch-ups")
                    .isActive(true)
                    .build();

            ServiceListing s29 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Water Closet (WC) Commode Acid Wash & Limescale Removal")
                    .description("Restore sparkling white cleanliness to heavily stained ceramic toilet commode bowls. Removes thick brown limescale deposits.")
                    .basePrice(new BigDecimal("450.00"))
                    .priceType("FIXED")
                    .durationMin(45)
                    .whatIncluded("Concentrated descaler treatment, Inner rim scrubbing, Jet pressure rinse, Seat sanitization")
                    .whatExcluded("Repairing flush valves, Toilet seat cover replacement, Sewer line unclogging")
                    .isActive(true)
                    .build();

            ServiceListing s30 = ServiceListing.builder()
                    .category("CLEANING")
                    .name("Office Workstation Desk Vacuuming & Upholstery Chair Cleaning")
                    .description("Deep clean office workspaces, typing desks, files storage cabinets exterior, and shampoo fabric computer chairs.")
                    .basePrice(new BigDecimal("3000.00"))
                    .priceType("STARTING_AT")
                    .durationMin(180)
                    .whatIncluded("Keyboard air blowout, Monitor screen microfiber wipes, Desk vacuuming, 5 fabric chairs shampoo")
                    .whatExcluded("Cleaning laptop internals, Server cabinet rack wiring touch, Garbage disposal fee")
                    .isActive(true)
                    .build();

            serviceListingRepository.saveAll(List.of(
                    s1, s2, s3, s4, s5, s6, s7, s8, s9, s10,
                    s11, s12, s13, s14, s15, s16, s17, s18, s19, s20,
                    s21, s22, s23, s24, s25, s26, s27, s28, s29, s30
            ));
            log.info("Database seeded with 30 detailed fixed-price services.");
        } else {
            log.info("Database services table is already seeded (count: {}).", serviceListingRepository.count());
        }

        // 8.5 Update existing provider seed fields if they are missing
        try {
            jdbcTemplate.execute("UPDATE providers SET name = 'Ram Bahadur', age = 34, description = 'Certified senior electrician with over 8 years of experience in residential wiring, appliance fixing, and emergency electrical troubleshooting. Dedicated to fast and safe service.', latitude = 27.7007, longitude = 85.3001, skills = 'Ceiling Fan Repair, Wiring, Circuit Breaker Replacement, Switch Fixes', experience_years = 8, profile_photo_url = 'https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&auto=format&fit=crop&q=60' WHERE phone = '9807484886' AND (name IS NULL OR description IS NULL)");
            jdbcTemplate.execute("UPDATE providers SET name = 'Hari Prasad', age = 42, description = 'Specialized residential plumber with 12 years of hands-on experience in pipe fitting, leak repair, drainage clearing, and kitchen/bathroom sanitizing upgrades. Available for emergencies.', latitude = 27.6710, longitude = 85.3240, skills = 'Leak Repair, Pipe Install, Tap Replacement, Drain Unclogging', experience_years = 12, profile_photo_url = 'https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=150&auto=format&fit=crop&q=60' WHERE phone = '9807484887' AND (name IS NULL OR description IS NULL)");
            jdbcTemplate.execute("UPDATE providers SET name = 'Sita Kumari', age = 29, description = 'Detail-oriented professional home cleaner. Skilled in deep disinfection, kitchen de-greasing, post-renovation cleanup, and premium carpet shampooing.', latitude = 27.6715, longitude = 85.4298, skills = 'Deep Cleaning, Kitchen Cleaning, Sanitizing, Carpet Shampooing', experience_years = 4, profile_photo_url = 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=60' WHERE phone = '9807484888' AND (name IS NULL OR description IS NULL)");
        } catch (Exception e) {
            log.warn("Could not update existing provider seed details: {}", e.getMessage());
        }

        // 9. Seed default test accounts
        if (userRepository.findByPhone("9807484885").isEmpty()) {
            User testCustomer = User.builder()
                    .name("Test Customer")
                    .phone("9807484885")
                    .email("customer@mitra.com")
                    .password("")
                    .role("CUSTOMER")
                    .isActive(true)
                    .build();
            userRepository.save(testCustomer);
            log.info("Seeded default test customer user: 9807484885");
        }

        if (userRepository.findByPhone("9800000000").isEmpty()) {
            User testAdmin = User.builder()
                    .name("Admin User")
                    .phone("9800000000")
                    .email("admin@mitra.com")
                    .password("")
                    .role("ADMIN")
                    .isActive(true)
                    .build();
            userRepository.save(testAdmin);
            log.info("Seeded default admin user: 9800000000");
        }

        // 9. Seed 15 default approved providers (5 in each category)
        // Category: ELECTRICAL
        seedProvider("9807484886", "Ram Bahadur", "Quick Spark Electricals", 34, "ELECTRICAL",
                "Certified senior electrician with over 8 years of experience in residential wiring, appliance fixing, and emergency electrical troubleshooting. Dedicated to fast and safe service.",
                "Ceiling Fan Repair, Wiring, Circuit Breaker Replacement, Switch Fixes",
                27.7007, 85.3001, 8, "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&auto=format&fit=crop&q=60", 5.00, 0);

        seedProvider("9807484889", "Shyam Thapa", "Thapa Electric Solutions", 28, "ELECTRICAL",
                "Specialist in house lightings, socket replacements, and emergency appliance grounding services. Safe and quick turnaround.",
                "Light Installation, Socket Fixes, Switch replacement, Grounding",
                27.6710, 85.3240, 4, "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=60", 4.70, 12);

        seedProvider("9807484890", "Krishna Shrestha", "Shrestha Wiring Experts", 45, "ELECTRICAL",
                "Certified industrial and residential electrician with 15 years experience in complex distribution boards and smart home automation setups.",
                "Short Circuit Troubleshooting, Generator Maintenance, Home Wiring, Distribution Boards",
                27.6715, 85.4298, 15, "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&auto=format&fit=crop&q=60", 4.90, 45);

        seedProvider("9807484891", "Gopal Tamang", "Gopal Electrical Works", 31, "ELECTRICAL",
                "Dedicated home electrician for appliance fixes, inverters, ceiling fan wiring, and backyard lights setup.",
                "Ceiling Fan Repair, Inverter Service, Distribution Board Fixes, Yard Lights",
                27.6795, 85.2770, 6, "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=60", 4.55, 8);

        seedProvider("9807484892", "Niranjan Giri", "Bouddha Spark Service", 38, "ELECTRICAL",
                "Over 10 years fixing electric geysers, kitchen hoods, and residential circuit boards with certified safety gear.",
                "Geyser Repair, Kitchen Hood Wiring, Circuit breaker testing, Board Fixes",
                27.7215, 85.3620, 10, "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150&auto=format&fit=crop&q=60", 4.85, 30);

        // Category: PLUMBING
        seedProvider("9807484887", "Hari Prasad", "Hari Plumbing Solutions", 42, "PLUMBING",
                "Specialized residential plumber with 12 years of hands-on experience in pipe fitting, leak repair, drainage clearing, and kitchen/bathroom upgrades.",
                "Leak Repair, Pipe Install, Tap Replacement, Drain Unclogging",
                27.6710, 85.3240, 12, "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=150&auto=format&fit=crop&q=60", 4.80, 24);

        seedProvider("9807484893", "Rajesh Maharjan", "Maharjan Plumbers", 35, "PLUMBING",
                "Expert plumber specialized in fixing toilet flushing issues, basin fittings, unclogging pipes, and water pump servicing.",
                "Commode Repair, Water Pump Fixing, Pipe Clogging Clearing, Basin Installation",
                27.7050, 85.3150, 8, "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150&auto=format&fit=crop&q=60", 4.75, 22);

        seedProvider("9807484894", "Devendra Joshi", "Joshi Pipe Services", 41, "PLUMBING",
                "14 years handling hot and cold water installations, heavy drainage fittings, and premium bathroom upgrades.",
                "Hot & Cold Water Line Fit, Pipe Leak Repair, Drainage Installation",
                27.6650, 85.3200, 14, "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&auto=format&fit=crop&q=60", 4.92, 60);

        seedProvider("9807484895", "Manoj Chaudhary", "Manoj Plumbing & Fixes", 27, "PLUMBING",
                "Fast, reliable service for tap replacements, kitchen sink repairs, and pipe joins. Available for urgent calls.",
                "Bathroom Fitting Repair, Washbasin Installation, Tap Repair, Pipe joins",
                27.6938, 85.2818, 3, "https://images.unsplash.com/photo-1519345182560-3f2917c472ef?w=150&auto=format&fit=crop&q=60", 4.60, 15);

        seedProvider("9807484896", "Bikash Shrestha", "Bikash Solar Plumbers", 33, "PLUMBING",
                "Specialist in solar water heaters, overhead tanks, automatic sensors, and general pipe fittings.",
                "Solar Water Heater Pipe Repair, Water Tank Cleaning, Leak Detection, Pipe fitting",
                27.7340, 85.3320, 7, "https://images.unsplash.com/photo-1489980508314-941910ded1f4?w=150&auto=format&fit=crop&q=60", 4.80, 28);

        // Category: CLEANING
        seedProvider("9807484888", "Sita Kumari", "Sita Home Cleaners", 29, "CLEANING",
                "Detail-oriented professional home cleaner. Skilled in deep disinfection, kitchen de-greasing, post-renovation cleanup, and premium carpet shampooing.",
                "Deep Cleaning, Kitchen Cleaning, Sanitizing, Carpet Shampooing",
                27.6715, 85.4298, 4, "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=60", 4.95, 18);

        seedProvider("9807484901", "Gita Adhikari", "Gita Sparkle Cleaning", 30, "CLEANING",
                "Over 6 years deep cleaning apartments, scrubbing kitchens, and steam sanitizing mattresses with natural cleaning agents.",
                "Full Home Deep Cleaning, Sofa Wet Cleaning, Kitchen Cleaning, Steam Sanitizing",
                27.7120, 85.3180, 6, "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=60", 4.88, 40);

        seedProvider("9807484902", "Sunita Rai", "Sunita Clean Team", 26, "CLEANING",
                "Dedicated cleaner for bathroom deep scrubbing, window panels cleaning, and carpet shampooing services.",
                "Bathroom Sanitization, Window Exterior Clean, Carpet Cleaning, Floor Wash",
                27.6770, 85.3110, 3, "https://images.unsplash.com/photo-1567532939604-b6b5b0db2604?w=150&auto=format&fit=crop&q=60", 4.72, 18);

        seedProvider("9807484903", "Radha Karki", "Radha Professional Scrubbers", 34, "CLEANING",
                "Especialized in heavy grease removal from kitchens, post-renovation dust cleaning, and floor restoration polishing.",
                "Kitchen Degreasing, Post-Construction Cleaning, Floor Polishing, Dust Clean",
                27.7170, 85.3480, 5, "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&auto=format&fit=crop&q=60", 4.65, 25);

        seedProvider("9807484904", "Maya Tamang", "Baneshwor Cleaning Express", 39, "CLEANING",
                "Highly rated cleaning professional with 9 years of experience in deep sanitizing residential and commercial areas.",
                "Full House Disinfection, Office Deep Cleaning, Mattress Sanitizing, Deep Scrubbing",
                27.6915, 85.3420, 9, "https://images.unsplash.com/photo-1551836022-d5d88e9218df?w=150&auto=format&fit=crop&q=60", 4.95, 85);
    }

    private void seedProvider(String phone, String name, String businessName, int age, String category, String description, String skills, double lat, double lng, int expYears, String photoUrl, double rating, int jobs) {
        if (providerRepository.findByPhone(phone).isEmpty()) {
            Provider p = Provider.builder()
                    .phone(phone)
                    .name(name)
                    .businessName(businessName)
                    .age(age)
                    .serviceCategory(category)
                    .description(description)
                    .skills(skills)
                    .latitude(lat)
                    .longitude(lng)
                    .experienceYears(expYears)
                    .profilePhotoUrl(photoUrl)
                    .ratingCache(BigDecimal.valueOf(rating).setScale(2, java.math.RoundingMode.HALF_UP))
                    .totalJobs(jobs)
                    .status("APPROVED")
                    .isOnline(true)
                    .address(category.equalsIgnoreCase("ELECTRICAL") ? "Kathmandu, Nepal" : category.equalsIgnoreCase("PLUMBING") ? "Lalitpur, Nepal" : "Bhaktapur, Nepal")
                    .build();
            providerRepository.save(p);
            log.info("Seeded provider: {} ({})", name, category);
        }
    }

    /**
     * Executes schema changes safely, ignoring duplicate column errors.
     */
    private void executeAlterSafe(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            String msg = e.getMessage();
            // Error code 1060 = Duplicate column name (safe to ignore)
            // Error code 1061 = Duplicate key name (index already exists)
            // Error code 1022 = Can't write; duplicate key in table
            if (msg != null && (msg.contains("1060") || msg.contains("Duplicate column") || msg.contains("1061") || msg.contains("Duplicate key"))) {
                // Ignore
            } else {
                log.warn("Safe ALTER failed for: '{}'. Reason: {}", sql, msg);
            }
        }
    }

    /**
     * Creates indexes safely.
     */
    private void executeIndexSafe(String indexName, String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            // Ignore duplicate index errors
        }
    }
}
