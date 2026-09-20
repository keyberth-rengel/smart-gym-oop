package com.smartgym.service;

import com.smartgym.domain.IdentityLink;
import com.smartgym.model.Customer;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.IdentityLinkRepository;
import com.smartgym.repository.TrainerRepository;
import com.smartgym.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MeServiceTest {

    @Autowired MeService service;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired IdentityLinkRepository links;

    @BeforeEach
    void clean() {
        links.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    @Test
    void onboardCreatesCustomerAndLinkAtomically() {
        var r = service.onboard("a@example.com", "Ana", 25, "12345678");
        assertTrue(r.changed());
        assertTrue(customers.existsById("a@example.com"));
        assertEquals("a@example.com", links.findById("12345678").orElseThrow().getEmail());
    }

    @Test
    void conflictDoesNotLeaveAPartialCustomer() {
        links.save(new IdentityLink("12345678", "other@example.com"));
        assertThrows(IllegalStateException.class, () -> service.onboard("a@example.com", "Ana", 25, "12345678"));
        assertFalse(customers.existsById("a@example.com"));
    }

    @Test
    void secondCallWithSameDataChangesNothing() {
        service.onboard("a@example.com", "Ana", 25, "12345678");
        assertFalse(service.onboard("a@example.com", "Ana", 25, "12345678").changed());
    }

    @Test
    void meForEachRoleNeverThrowsOnMissingData() {
        assertFalse(service.me(Role.CLIENTE, "nobody@example.com").profileComplete());
        assertFalse(service.me(Role.ENTRENADOR, "nobody@example.com").profileComplete());
        assertTrue(service.me(Role.ADMIN, "nobody@example.com").profileComplete());
        customers.save(new Customer("c@example.com", "Cris", 20));
        assertNull(service.me(Role.CLIENTE, "c@example.com").dni());
    }
}
