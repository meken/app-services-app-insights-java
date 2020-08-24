/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.mueken.api.web;

import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import com.microsoft.mueken.api.model.Employee;
import com.microsoft.mueken.api.service.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class SampleController {
	private static final Logger LOGGER = LoggerFactory.getLogger(SampleController.class);

	@Resource
	private EmployeeRepository repository;

	@GetMapping("/employee/{id}")
	@Transactional(readOnly = true)
	public Employee findById(@PathVariable Long id) {
	    LOGGER.info("# findById: {}", id);
		Optional<Employee> emp = repository.findById(id);
		if (emp.isPresent()) {
			return emp.get();
		} else {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/employee")
	@Transactional(readOnly = true)
	public Iterable<Employee> findAll() {
        LOGGER.info("# findAll");
		return repository.findAll();
	}


	@GetMapping("/employee/alias/{alias}")
	@Transactional(readOnly = true)
	public Employee findByAlias(@PathVariable String alias) {
        LOGGER.info("# findByAlias: {}", alias);
		Optional<Employee> emp = repository.findByAlias(alias);
		if (emp.isPresent()) {
			return emp.get();
		} else {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}


	@PostMapping("/employee")
	@Transactional
	public Employee newEmployee(@RequestBody Employee employee) {
        LOGGER.info("# newEmployee: {}", employee);
		return repository.save(employee);
	}
}
