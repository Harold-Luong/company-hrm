package com.company.employee;

import com.company.employee.entity.Department;
import com.company.employee.entity.Position;
import com.company.employee.enums.AccountStatus;
import com.company.employee.repository.DepartmentRepository;
import com.company.employee.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "HR")
class EmployeeApiTests extends JwtTestSupport {
    private static final String API = "/api/v1/employees";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private PositionRepository positions;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("UPDATE employees SET manager_id = NULL");
        jdbc.update("DELETE FROM employees");
        jdbc.update("DELETE FROM departments");
        jdbc.update("DELETE FROM positions");
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void accountStatusIsReadOnlyAndPreservedWhenEditingEmployee(AccountStatus accountStatus) throws Exception {
        Map<String, Object> request = request("EMP001");
        request.put("accountStatus", "ACTIVE");
        JsonNode created = create(request);
        assertThat(created.get("accountStatus").asText()).isEqualTo("NOT_CREATED");
        assertThat(created.has("hasAccount")).isFalse();
        String id = created.get("id").asText();

        jdbc.update("UPDATE employees SET account_status = ? WHERE id = ?", accountStatus.name(), UUID.fromString(id));
        request.put("accountStatus", "NOT_CREATED");
        mvc.perform(put(API + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value(accountStatus.name()))
                .andExpect(jsonPath("$.hasAccount").doesNotExist());
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value(accountStatus.name()));
        mvc.perform(get(API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountStatus").value(accountStatus.name()));
    }

    @Test
    void createsAndReadsEmployeeWithReferencesAndTimestamps() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        Department department = departments.saveAndFlush(Department.builder()
                .code("IT").name("Information Technology").active(true)
                .createdAt(now).updatedAt(now).build());
        Position position = positions.saveAndFlush(Position.builder()
                .code("ENGINEER").name("Software Engineer").active(true)
                .createdAt(now).updatedAt(now).build());
        String managerId = create(request("MANAGER")).get("id").asText();
        Map<String, Object> request = request("EMP001");
        request.put("departmentId", department.getId());
        request.put("positionId", position.getId());
        request.put("managerId", managerId);
        JsonNode employee = create(request);

        assertThat(employee.get("createdAt").asText()).isNotBlank();
        assertThat(employee.get("updatedAt").asText()).isEqualTo(employee.get("createdAt").asText());
        mvc.perform(get(API + "/" + employee.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP001"))
                .andExpect(jsonPath("$.department.id").value(department.getId().toString()))
                .andExpect(jsonPath("$.position.code").value("ENGINEER"))
                .andExpect(jsonPath("$.manager.id").value(managerId))
                .andExpect(jsonPath("$.manager.manager").doesNotExist());
    }

    @Test
    void listsEmployeesWithStablePagination() throws Exception {
        create(request("EMP002"));
        create(request("EMP001"));
        mvc.perform(get(API).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("EMP001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(API).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeCode").value("EMP002"));
        mvc.perform(get(API).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listsEmptyDatabaseWithDefaults() throws Exception {
        mvc.perform(get(API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void replacesEmployeeAndClearsOptionalFields() throws Exception {
        String managerId = create(request("MANAGER")).get("id").asText();
        Map<String, Object> original = request("EMP001");
        original.put("managerId", managerId);
        original.put("phone", "0901000001");
        JsonNode created = create(original);
        String id = created.get("id").asText();
        Map<String, Object> updated = request("EMP001");
        updated.put("firstName", "Updated");
        updated.put("status", "ACTIVE");
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.manager").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
        JsonNode persisted = objectMapper.readTree(mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(persisted.get("firstName").asText()).isEqualTo("Updated");
        assertThat(OffsetDateTime.parse(persisted.get("updatedAt").asText()))
                .isAfter(OffsetDateTime.parse(created.get("updatedAt").asText()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "PROBATION", "RESIGNED", "TERMINATED"})
    void changesOnlyStatus(String newStatus) throws Exception {
        JsonNode created = create(request("EMP001"));
        String id = created.get("id").asText();
        mvc.perform(patch(API + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", newStatus))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(newStatus))
                .andExpect(jsonPath("$.email").value("EMP001@company.com"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(newStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"employeeCode", "email"})
    void rejectsDuplicateFieldsOnCreateAndUpdate(String field) throws Exception {
        Map<String, Object> first = request("EMP001");
        create(first);
        Map<String, Object> conflicting = request("EMP002");
        conflicting.put(field, first.get(field));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflicting)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString(field)));
        String secondId = create(request("EMP002")).get("id").asText();
        mvc.perform(put(API + "/" + secondId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflicting)))
                .andExpect(status().isConflict());
        mvc.perform(get(API + "/" + secondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP002"))
                .andExpect(jsonPath("$.email").value("EMP002@company.com"));
    }

    @Test
    void validatesRequiredFieldsAndEmail() throws Exception {
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.employeeCode").exists())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.hireDate").exists())
                .andExpect(jsonPath("$.errors.status").exists());
        Map<String, Object> invalid = request("EMP001");
        invalid.put("email", "invalid-email");
        invalid.put("firstName", "   ");
        invalid.put("dateOfBirth", "2999-01-01");
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"employeeCode", "firstName", "lastName", "email", "phone"})
    void rejectsValuesLongerThanDatabaseColumns(String field) throws Exception {
        Map<String, Object> invalid = request("EMP001");
        invalid.put(field, "x".repeat(256));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void stripsWhitespaceBeforeValidationAndPersistence() throws Exception {
        Map<String, Object> request = request(" EMP001 ");
        request.put("email", " employee@company.com ");
        JsonNode created = create(request);
        assertThat(created.get("employeeCode").asText()).isEqualTo("EMP001");
        assertThat(created.get("email").asText()).isEqualTo("employee@company.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId", "positionId", "managerId"})
    void rejectsMissingReferences(String field) throws Exception {
        Map<String, Object> invalid = request("EMP001");
        invalid.put(field, UUID.randomUUID());
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isZero();
    }

    @Test
    void returnsNotFoundForUnknownEmployee() throws Exception {
        String path = API + "/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("EMP001"))))
                .andExpect(status().isNotFound());
        mvc.perform(patch(path + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsSelfManagerAndIndirectManagerCycle() throws Exception {
        String firstId = create(request("EMP001")).get("id").asText();
        Map<String, Object> second = request("EMP002");
        second.put("managerId", firstId);
        String secondId = create(second).get("id").asText();
        Map<String, Object> third = request("EMP003");
        third.put("managerId", secondId);
        String thirdId = create(third).get("id").asText();
        for (String managerId : new String[]{firstId, thirdId}) {
            Map<String, Object> invalid = request("EMP001");
            invalid.put("managerId", managerId);
            mvc.perform(put(API + "/" + firstId).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("cycle")));
        }
        mvc.perform(get(API + "/" + firstId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manager").isEmpty());
    }

    @Test
    void rejectsBirthDateOnOrAfterHireDate() throws Exception {
        Map<String, Object> invalid = request("EMP001");
        invalid.put("dateOfBirth", "2000-01-01");
        invalid.put("hireDate", "1999-01-01");
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("dateOfBirth")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}", "{", "{\"status\":0}"})
    void rejectsInvalidStatusBodies(String body) throws Exception {
        String id = create(request("EMP001")).get("id").asText();
        mvc.perform(patch(API + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedIdentifiersDatesAndPagination() throws Exception {
        mvc.perform(get(API + "/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("page", "abc")).andExpect(status().isBadRequest());
        Map<String, Object> invalid = request("EMP001");
        invalid.put("hireDate", "not-a-date");
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> request(String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeCode", code);
        request.put("firstName", "An");
        request.put("lastName", "Nguyen");
        request.put("email", code + "@company.com");
        request.put("dateOfBirth", "1990-04-15");
        request.put("hireDate", "2024-01-10");
        request.put("status", "PROBATION");
        return request;
    }

    private JsonNode create(Map<String, Object> request) throws Exception {
        String body = mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(API + "/")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
