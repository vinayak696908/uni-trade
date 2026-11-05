package com.unitrade.config;

import com.unitrade.entity.Campuses;
import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import com.unitrade.entity.Role;
import com.unitrade.entity.User;
import com.unitrade.repository.ProductRepository;
import com.unitrade.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * DEVELOPMENT-ONLY sample data. Runs only when {@code app.seed.enabled=true} (env SEED_DEMO_DATA=true).
 *
 * - Demo listings and three fictional sellers (example.com addresses) are inserted only when the
 *   products table is empty.
 * - Demo sellers get the password from SEED_DEMO_PASSWORD if set, otherwise a random password nobody knows.
 * - An admin account is created only if SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD are both provided.
 *   No default administrator credentials exist anywhere in the code.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String demoPassword;

    public DevDataSeeder(UserRepository userRepository, ProductRepository productRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.seed.admin-email:}") String adminEmail,
                         @Value("${app.seed.admin-password:}") String adminPassword,
                         @Value("${app.seed.demo-password:}") String demoPassword) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.demoPassword = demoPassword == null ? "" : demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("Development data seeding is ENABLED (app.seed.enabled=true). Disable it in production.");
        seedAdmin();
        seedListings();
    }

    private void seedAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("No SEED_ADMIN_EMAIL/SEED_ADMIN_PASSWORD provided; skipping admin account creation.");
            return;
        }
        if (adminPassword.length() < 12) {
            log.warn("SEED_ADMIN_PASSWORD must be at least 12 characters; admin account NOT created.");
            return;
        }
        userRepository.findByEmail(adminEmail).ifPresentOrElse(existing -> {
            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                userRepository.save(existing);
                log.info("Promoted existing user {} to ADMIN.", adminEmail);
            }
        }, () -> {
            User admin = newUser("UniTrade Moderator", adminEmail, adminPassword, Campuses.ALL.get(0));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("Created admin account {}.", adminEmail);
        });
    }

    private void seedListings() {
        if (productRepository.count() > 0) {
            log.info("Listings already exist; skipping demo listings.");
            return;
        }
        String sellerPassword = demoPassword.length() >= 8 ? demoPassword : randomSecret();

        User ananya = findOrCreate("Ananya Rao", "ananya.demo@example.com", sellerPassword, Campuses.ALL.get(0));
        User rohan = findOrCreate("Rohan Mehta", "rohan.demo@example.com", sellerPassword, Campuses.ALL.get(1));
        User kavya = findOrCreate("Kavya Iyer", "kavya.demo@example.com", sellerPassword, Campuses.ALL.get(2));

        List<Product> products = List.of(
                product(ananya, "Head First Java (3rd Edition)",
                        "Well-kept copy used for one semester of OOP. A few pencil notes in chapters 4-6, no torn pages. "
                                + "Great for first-year Java labs.",
                        Category.BOOKS, ItemCondition.GOOD, "450.00"),
                product(rohan, "Data Structures and Algorithms Made Easy",
                        "DSA textbook with solved problems on arrays, trees, graphs and dynamic programming. "
                                + "Ideal for placement preparation. Cover slightly bent.",
                        Category.BOOKS, ItemCondition.LIKE_NEW, "380.00"),
                product(kavya, "Introduction to Algorithms (CLRS), 3rd Edition",
                        "The classic algorithms reference. Some highlighting in the sorting and graph chapters. Heavy but complete.",
                        Category.BOOKS, ItemCondition.FAIR, "650.00"),
                product(ananya, "Casio fx-991EX ClassWiz Scientific Calculator",
                        "Exam-approved scientific calculator with spreadsheet and matrix modes. Works perfectly, includes cover. "
                                + "Battery replaced last month.",
                        Category.CALCULATORS, ItemCondition.GOOD, "900.00"),
                product(rohan, "Arduino Uno R3 Starter Kit",
                        "Complete starter kit: Uno R3 board, USB cable, LEDs, resistors, servo, ultrasonic sensor, LCD and "
                                + "project booklet. Used for one mini project, all parts tested.",
                        Category.PROJECT_COMPONENTS, ItemCondition.LIKE_NEW, "1400.00"),
                product(kavya, "830-point Breadboard with 120 Jumper Wires",
                        "Full-size solderless breadboard plus male-male, male-female and female-female jumper wires (40 each).",
                        Category.PROJECT_COMPONENTS, ItemCondition.GOOD, "180.00"),
                product(ananya, "Engineering Drawing Instrument Box with Mini Drafter",
                        "Mini drafter, compass set, protractor, set squares and French curves in a hard case. "
                                + "Everything needed for first-year engineering graphics.",
                        Category.STATIONERY, ItemCondition.GOOD, "650.00"),
                product(rohan, "White Cotton Lab Coat (Size M)",
                        "Full-sleeve lab coat for chemistry and biology labs. Washed and ironed, no stains.",
                        Category.LAB_EQUIPMENT, ItemCondition.GOOD, "250.00"),
                product(kavya, "Digital Multimeter DT-830D",
                        "Measures DC/AC voltage, current and resistance. Comes with test probes. Useful for electronics labs.",
                        Category.LAB_EQUIPMENT, ItemCondition.GOOD, "300.00"),
                product(ananya, "Wireless Keyboard and Mouse Combo",
                        "Compact wireless keyboard with matching mouse, single USB receiver. Batteries included.",
                        Category.ELECTRONICS, ItemCondition.LIKE_NEW, "750.00"),
                product(rohan, "7-in-1 USB-C Hub (HDMI, USB 3.0, SD card)",
                        "Connect a projector and pen drives to your laptop. 4K HDMI output, 3 x USB 3.0, SD and microSD slots.",
                        Category.ELECTRONICS, ItemCondition.GOOD, "950.00"),
                product(kavya, "Lab Record Notebooks (set of 3, unused)",
                        "Three ruled-and-plain lab record notebooks, 200 pages each. Bought extra, never used.",
                        Category.STATIONERY, ItemCondition.NEW, "120.00")
        );
        productRepository.saveAll(products);

        // One sold example so the SOLD badge can be seen on the seller's dashboard.
        Product sold = product(kavya, "Physics Practical Manual (2nd Year)",
                "Practical manual with completed readings for reference.",
                Category.BOOKS, ItemCondition.FAIR, "90.00");
        sold.setStatus(ListingStatus.SOLD);
        productRepository.save(sold);

        log.info("Inserted {} demo listings for 3 demo sellers.", products.size() + 1);
        if (demoPassword.length() >= 8) {
            log.info("Demo sellers can log in with the password from SEED_DEMO_PASSWORD (e.g. ananya.demo@example.com).");
        }
    }

    private User findOrCreate(String name, String email, String password, String campus) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(newUser(name, email, password, campus)));
    }

    private User newUser(String name, String email, String password, String campus) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCampus(campus);
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
        return user;
    }

    private static Product product(User seller, String title, String description, Category category,
                                   ItemCondition condition, String price) {
        Product p = new Product();
        p.setSeller(seller);
        p.setTitle(title);
        p.setDescription(description);
        p.setCategory(category);
        p.setCondition(condition);
        p.setPrice(new BigDecimal(price));
        p.setCampus(seller.getCampus());
        p.setStatus(ListingStatus.AVAILABLE);
        return p;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
