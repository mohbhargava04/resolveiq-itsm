package com.resolveiq.user.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Configures the system: users, services, categories, SLA policies and strategies. */
@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    protected Administrator() {
    }

    public Administrator(String fullName, String email, String passwordHash, String phone, String department) {
        super(fullName, email, passwordHash, phone, department);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }
}
