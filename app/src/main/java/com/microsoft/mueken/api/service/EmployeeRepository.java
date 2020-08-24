package com.microsoft.mueken.api.service;

import java.util.List;
import java.util.Optional;

import com.microsoft.mueken.api.model.Employee;
import org.springframework.data.repository.CrudRepository;

public interface EmployeeRepository extends CrudRepository<Employee, Long> {
    List<Employee> findByLastName(String lastName);

    Optional<Employee> findByAlias(String alias);
}