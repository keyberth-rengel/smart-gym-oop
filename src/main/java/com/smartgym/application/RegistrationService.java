package com.smartgym.application;

import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.service.SmartGymService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altas administrativas: crea el cliente o entrenador y, si se indica, vincula su DNI en UNA transacción.
 * Si el DNI ya pertenece a otro correo (409) no queda nada creado.
 */
@Service
public class RegistrationService {

    private final SmartGymService core;
    private final GymExtensions ext;

    public RegistrationService(SmartGymService core, GymExtensions ext) {
        this.core = core;
        this.ext = ext;
    }

    @Transactional
    public void registerTrainer(Trainer trainer, String dni) {
        core.addTrainer(trainer);
        if (dni != null && !dni.isBlank()) {
            ext.registerTrainerIdentity(dni, trainer.getEmail());
        }
    }

    @Transactional
    public void registerCustomer(Customer customer, String dni) {
        core.addCustomer(customer);
        if (dni != null && !dni.isBlank()) {
            ext.registerCustomerIdentity(dni, customer.getEmail());
        }
    }
}
