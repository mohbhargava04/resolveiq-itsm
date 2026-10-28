package com.resolveiq.user.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** An employee (customer of IT) who reports incidents and tracks them. */
@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    protected Employee() {
    }

    public Employee(String fullName, String email, String passwordHash, String phone, String department) {
        super(fullName, email, passwordHash, phone, department);
    }

    @Override
    public Role getRole() {
        return Role.EMPLOYEE;
    }
}
