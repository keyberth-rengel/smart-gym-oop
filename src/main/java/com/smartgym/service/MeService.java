package com.smartgym.service;

import com.smartgym.api.dto.MeResponse;
import com.smartgym.api.dto.ProfileDto;
import com.smartgym.domain.IdentityLink;
import com.smartgym.model.Customer;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.IdentityLinkRepository;
import com.smartgym.repository.TrainerRepository;
import com.smartgym.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Perfil del usuario autenticado y onboarding de clientes (identificado por el correo del token). */
@Service
public class MeService {

    /** Resultado del onboarding: {@code changed} indica si se creó algo (cliente o vínculo de DNI). */
    public record OnboardingResult(boolean changed) {}

    private final CustomerRepository customerRepository;
    private final TrainerRepository trainerRepository;
    private final IdentityLinkRepository identityLinkRepository;

    public MeService(CustomerRepository customerRepository, TrainerRepository trainerRepository,
                     IdentityLinkRepository identityLinkRepository) {
        this.customerRepository = customerRepository;
        this.trainerRepository = trainerRepository;
        this.identityLinkRepository = identityLinkRepository;
    }

    /** @param email correo ya normalizado (minúsculas) */
    @Transactional(readOnly = true)
    public MeResponse me(Role role, String email) {
        String dni = identityLinkRepository.findFirstByEmailOrderByDniAsc(email).map(IdentityLink::getDni).orElse(null);
        return switch (role) {
            case CLIENTE -> {
                Optional<Customer> customer = customerRepository.findById(email);
                ProfileDto profile = customer.map(c -> new ProfileDto(c.getEmail(), c.getName(), c.getAge(), null)).orElse(null);
                yield new MeResponse(role.label(), email, customer.map(Customer::getName).orElse(null), dni,
                        customer.isPresent() && dni != null, profile);
            }
            case ENTRENADOR -> {
                var trainer = trainerRepository.findById(email);
                ProfileDto profile = trainer.map(t -> new ProfileDto(t.getEmail(), t.getName(), t.getAge(), t.getSpecialty())).orElse(null);
                yield new MeResponse(role.label(), email, trainer.map(t -> t.getName()).orElse(null), dni,
                        trainer.isPresent(), profile);
            }
            case ADMIN -> new MeResponse(role.label(), email, null, dni, true, null);
        };
    }

    /**
     * Crea el cliente y vincula su DNI en una sola transacción.
     * 409 si el DNI pertenece a otro correo o si el correo ya tiene otro DNI; idempotente si ya está todo igual.
     */
    @Transactional
    public OnboardingResult onboard(String email, String name, int age, String dni) {
        String key = dni.trim();
        Optional<IdentityLink> byDni = identityLinkRepository.findById(key);
        if (byDni.isPresent() && !byDni.get().getEmail().equals(email)) {
            throw new IllegalStateException("DNI already linked to another account");
        }
        Optional<String> currentDni = identityLinkRepository.findFirstByEmailOrderByDniAsc(email).map(IdentityLink::getDni);
        if (currentDni.isPresent() && !currentDni.get().equals(key)) {
            throw new IllegalStateException("This account is already linked to a different DNI");
        }
        boolean changed = false;
        if (!customerRepository.existsById(email)) {
            customerRepository.save(new Customer(email, name.trim(), age));
            changed = true;
        }
        if (currentDni.isEmpty()) {
            identityLinkRepository.save(new IdentityLink(key, email));
            changed = true;
        }
        return new OnboardingResult(changed);
    }
}
