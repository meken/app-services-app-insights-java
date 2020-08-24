package com.microsoft.mueken.api.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Entity
public class Employee {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;

    private String firstName;

    private String lastName;

    @Column(unique = true)
    private String alias;

    protected Employee() {}

    public Employee(String firstName, String lastName, String alias) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.alias = alias;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) { return false; }
        if (obj == this) { return true; }
        if (obj.getClass() != getClass()) {
            return false;
        }
        Employee rhs = (Employee) obj;
        return new EqualsBuilder().
                append(alias, rhs.alias).
                append(firstName, rhs.firstName).
                append(lastName, rhs.lastName).
                isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).
                append(alias).
                append(firstName).
                append(lastName).
                toHashCode();
    }

    @Override
    public String toString() {
        return String.format(
                "Employee[id=%d, firstName='%s', lastName='%s', alias='%s']",
                id, firstName, lastName, alias);
    }

}