package com.company.employee;

import com.company.employee.entity.Department;
import com.company.employee.entity.Position;
import com.company.employee.enums.EmployeeAccountStatus;
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
        jdbc.execute("ALTER SEQUENCE employee_code_seq RESTART WITH 1");
    }

    @ParameterizedTest
    @EnumSource(EmployeeAccountStatus.class)
    void accountStatusIsReadOnlyAndPreservedWhenEditingEmployee(EmployeeAccountStatus accountStatus) throws Exception {
        Map<String, Object> request = request("EMP001");
        request.put("accountStatus", "ACTIVE");
        JsonNode created = create(request);
        assertThat(created.get("accountStatus").asText()).isEqualTo("NOT_CREATED");
        assertThat(created.has("hasAccount")).isFalse();
        String id = created.get("id").asText();

        jdbc.update("UPDATE employees SET employee_account_status = ? WHERE id = ?", accountStatus.name(), UUID.fromString(id));
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
        java.time.Instant now = java.time.Instant.now();
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
                .andExpect(jsonPath("$.employeeCode").value(employee.get("employeeCode").asText()))
                .andExpect(jsonPath("$.department.id").value(department.getId().toString()))
                .andExpect(jsonPath("$.position.code").value("ENGINEER"))
                .andExpect(jsonPath("$.manager.id").value(managerId))
                .andExpect(jsonPath("$.manager.manager").doesNotExist());
    }

    @Test
    void persistsNewProfileFieldsAndAllowsSharedPersonalEmail() throws Exception {
        Map<String, Object> request = request("EMP001");
        request.put("gender", "FEMALE");
        request.put("address", "  12 Nguyen Trai  ");
        request.put("contactRelative", "  Nguyen Van An  ");
        request.put("contactRelativePhone", "  0901234567  ");
        JsonNode created = create(request);
        String id = created.get("id").asText();
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalEmail").value("EMP001@company.com"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.address").value("12 Nguyen Trai"))
                .andExpect(jsonPath("$.contactRelative").value("Nguyen Van An"))
                .andExpect(jsonPath("$.contactRelativePhone").value("0901234567"));
        Map<String, Object> second = request("EMP002");
        second.put("personalEmail", request.get("personalEmail"));
        create(second);

        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("EMP001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("OTHER"))
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.contactRelative").isEmpty())
                .andExpect(jsonPath("$.contactRelativePhone").isEmpty());
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactRelativePhone").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "", "male"})
    void rejectsInvalidGender(String gender) throws Exception {
        Map<String, Object> request = request("EMP001");
        request.put("gender", gender);
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditsEmployeeUuidFromJwtAndKeepsCreatorOnUpdate() throws Exception {
        UUID creator = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        var hr = new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HR");
        String body = mvc.perform(post(API)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("42").claim("employee_id", creator.toString())).authorities(hr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("EMP001"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT created_by FROM employees WHERE id = ?", UUID.class, id)).isEqualTo(creator);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM employees WHERE id = ?", UUID.class, id)).isEqualTo(creator);
        mvc.perform(patch(API + "/" + id + "/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("43").claim("employee_id", editor.toString())).authorities(hr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT created_by FROM employees WHERE id = ?", UUID.class, id)).isEqualTo(creator);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM employees WHERE id = ?", UUID.class, id)).isEqualTo(editor);
    }

    @Test
    void listsEmployeesWithStablePagination() throws Exception {
        String firstCode = create(request("EMP002")).get("employeeCode").asText();
        String secondCode = create(request("EMP001")).get("employeeCode").asText();
        mvc.perform(get(API).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value(firstCode))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(API).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeCode").value(secondCode));
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
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "PROBATION", "SUSPENDED", "TERMINATED"})
    void changesOnlyStatus(String newStatus) throws Exception {
        JsonNode created = create(request("EMP001"));
        String id = created.get("id").asText();
        mvc.perform(patch(API + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", newStatus))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(newStatus))
                .andExpect(jsonPath("$.personalEmail").value("EMP001@company.com"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(newStatus));
    }

    @Test
    void generatesEmployeeCodeAndIgnoresClientCodesOnCreateAndUpdate() throws Exception {
        Map<String, Object> request = request("EMP001");
        request.put("employeeCode", "CLIENT-CODE");
        JsonNode created = create(request);
        String code = created.get("employeeCode").asText();
        assertThat(code).isEqualTo("EMP000001");
        String id = created.get("id").asText();
        request.put("employeeCode", "CHANGED-CODE");
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value(code));
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value(code));
        JsonNode second = create(request("EMP002"));
        assertThat(second.get("employeeCode").asText()).isEqualTo("EMP000002");
    }

    @Test
    void preservesLegacyCodesAndSkipsCodesAlreadyInUse() throws Exception {
        JsonNode legacy = create(request("LEGACY"));
        UUID id = UUID.fromString(legacy.get("id").asText());
        jdbc.update("UPDATE employees SET employee_code = 'EMP000002' WHERE id = ?", id);
        assertThat(create(request("NEW")).get("employeeCode").asText()).isEqualTo("EMP000003");
        jdbc.update("UPDATE employees SET employee_code = 'NV001' WHERE id = ?", id);
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("LEGACY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("NV001"));
        // Removing records must not recycle previously allocated numbers.
        jdbc.update("DELETE FROM employees");
        assertThat(create(request("NEXT")).get("employeeCode").asText()).isEqualTo("EMP000004");
    }

    @Test
    void concurrentCreatesReceiveDistinctPersistedCodes() throws Exception {
        int count = 8;
        var ready = new java.util.concurrent.CountDownLatch(count);
        var start = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(count)) {
            for (int i = 0; i < count; i++) {
                String payload = objectMapper.writeValueAsString(request("CONCURRENT" + i));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent creation did not start");
                    }
                    String body = mvc.perform(post(API)
                                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                            .user("hr").roles("HR"))
                                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
                    return objectMapper.readTree(body).get("employeeCode").asText();
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var codes = new java.util.HashSet<String>();
            for (var future : futures) {
                String code = future.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(code).matches("EMP[0-9]{6,}");
                assertThat(codes.add(code)).isTrue();
            }
            assertThat(jdbc.queryForList("SELECT employee_code FROM employees", String.class))
                    .containsExactlyInAnyOrderElementsOf(codes);
        } finally {
            start.countDown();
        }
    }

    @Test
    void validatesRequiredFieldsAndEmail() throws Exception {
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.employeeCode").doesNotExist())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.personalEmail").exists())
                .andExpect(jsonPath("$.errors.hireDate").exists())
                .andExpect(jsonPath("$.errors.status").exists())
                .andExpect(jsonPath("$.errors.gender").exists());
        Map<String, Object> invalid = request("EMP001");
        invalid.put("personalEmail", "invalid-email");
        invalid.put("firstName", "   ");
        invalid.put("dateOfBirth", "2999-01-01");
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.personalEmail").exists())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "personalEmail", "phone", "address", "contactRelative", "contactRelativePhone"})
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
        request.put("personalEmail", " employee@company.com ");
        JsonNode created = create(request);
        assertThat(created.get("employeeCode").asText()).isEqualTo("EMP000001");
        assertThat(created.get("personalEmail").asText()).isEqualTo("employee@company.com");
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
        request.put("firstName", "An");
        request.put("lastName", "Nguyen");
        request.put("personalEmail", code + "@company.com");
        request.put("dateOfBirth", "1990-04-15");
        request.put("hireDate", "2024-01-10");
        request.put("status", "PROBATION");
        request.put("gender", "OTHER");
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
