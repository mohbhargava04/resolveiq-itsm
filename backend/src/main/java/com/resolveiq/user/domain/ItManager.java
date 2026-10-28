package com.resolveiq.user.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * The IT manager <em>is an</em> administrator with organisational accountability; they are the top
 * of the escalation chain and see the management analytics.
 */
@Entity
@DiscriminatorValue("MANAGER")
public class ItManager extends Administrator {

    protected ItManager() {
    }

    public ItManager(String fullName, String email, String passwordHash, String phone, String department) {
        super(fullName, email, passwordHash, phone, department);
    }

    @Override
    public Role getRole() {
        return Role.MANAGER;
    }
}
