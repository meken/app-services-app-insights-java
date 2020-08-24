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
package com.microsoft.mueken.api.service;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.microsoft.mueken.api.SampleDataJpaApplication;
import com.microsoft.mueken.api.model.Employee;

import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleDataJpaApplication.class)
@ActiveProfiles("scratch")
public class EmployeeRepositoryTest {
	private static final AtomicInteger COUNTER = new AtomicInteger(1);

	@Resource
	private EmployeeRepository repository;
	private Employee emp;

	@Before
	public void setUp() {
		String alias = "foobar" + COUNTER.getAndIncrement();
		emp = new Employee("firstname", "lastname", alias);
	}

	@Test
	public void findSavedUserById() {
		emp = repository.save(emp);
		assertThat(repository.findById(emp.getId())).hasValue(emp);
	}

	@Test
	public void findSavedUserByLastname() {
		emp = repository.save(emp);
		assertThat(repository.findByLastName(emp.getLastName())).contains(emp);
	}

	@Test
	public void findSavedUserByAlias() {
		emp = repository.save(emp);
		assertThat(repository.findByAlias(emp.getAlias())).hasValue(emp);
	}

}
