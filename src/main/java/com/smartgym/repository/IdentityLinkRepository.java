package com.smartgym.repository;

import com.smartgym.domain.IdentityLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityLinkRepository extends JpaRepository<IdentityLink, String> {
    Optional<IdentityLink> findByDni(String dni);
}
