package com.company.employee;

import com.company.employee.entity.Department;
import com.company.employee.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "HR")
class DepartmentApiTests extends JwtTestSupport {
    private static final String API = "/api/v1/departments";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DepartmentRepository departments;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("UPDATE employees SET manager_id = NULL");
        jdbc.update("DELETE FROM employees");
        jdbc.update("DELETE FROM departments");
        jdbc.update("DELETE FROM positions");
    }

    @Test
    void createsAndReadsDepartmentWithNormalizedFieldsAndDatabaseDefaults() throws Exception {
        JsonNode created = create(Map.of("code", " IT ", "name", " Information Technology ",
                "description", " Internal systems "));
        String id = created.get("id").asText();
        assertThat(created.get("code").asText()).isEqualTo("IT");
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("IT"))
                .andExpect(jsonPath("$.name").value("Information Technology"))
                .andExpect(jsonPath("$.description").value("Internal systems"));
        Department persisted = departments.findById(UUID.fromString(id)).orElseThrow();
        assertThat(persisted.getActive()).isTrue();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());
    }

    @Test
    void acceptsOptionalDescriptionAndMaximumFieldLengths() throws Exception {
        JsonNode created = create(Map.of("code", "C".repeat(50), "name", "N".repeat(150)));
        assertThat(created.get("description").isNull()).isTrue();
        mvc.perform(get(API + "/" + created.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").isEmpty());
    }

    @Test
    void listsDepartmentsWithStablePagination() throws Exception {
        create(Map.of("code", "IT", "name", "Technology"));
        create(Map.of("code", "HR", "name", "Human Resources"));
        mvc.perform(get(API).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("HR"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(API).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("IT"));
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
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void rejectsDuplicateCodeAfterNormalization() throws Exception {
        create(Map.of("code", "IT", "name", "Technology"));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" IT \",\"name\":\"Another department\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail", containsString("code")));
        assertThat(departments.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":null,\"name\":null}", "{\"code\":\"  \",\"name\":\"  \"}"})
    void validatesRequiredFields(String body) throws Exception {
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.name").exists());
        assertThat(departments.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "name"})
    void rejectsValuesExceedingColumnLengths(String field) throws Exception {
        Map<String, String> body = field.equals("code")
                ? Map.of("code", "C".repeat(51), "name", "Technology")
                : Map.of("code", "IT", "name", "N".repeat(151));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
        assertThat(departments.count()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownDepartment() throws Exception {
        mvc.perform(get(API + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("Department not found")));
    }

    @Test
    void rejectsMalformedRequestsAndPagination() throws Exception {
        mvc.perform(get(API + "/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("page", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesDepartmentAndPreservesIdentityCreationTimeAndActiveState() throws Exception {
        String id = create(Map.of("code", "IT", "name", "Technology", "description", "Old description"))
                .get("id").asText();
        jdbc.update("UPDATE departments SET active = FALSE WHERE id = ?", UUID.fromString(id));
        Department original = departments.findById(UUID.fromString(id)).orElseThrow();

        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", " TECH ",
                                "name", " Engineering ", "description", " New description "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("TECH"))
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.description").value("New description"));
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TECH"))
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.description").value("New description"));
        Department updated = departments.findById(UUID.fromString(id)).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfter(original.getUpdatedAt());
        assertThat(updated.getActive()).isFalse();
        assertThat(departments.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\" IT \",\"name\":\"Updated\"}",
            "{\"code\":\" IT \",\"name\":\"Updated\",\"description\":null}"})
    void allowsSameCodeAndClearsOmittedOrNullDescription(String body) throws Exception {
        String id = create(Map.of("code", "IT", "name", "Technology", "description", "Old description"))
                .get("id").asText();
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("IT"))
                .andExpect(jsonPath("$.description").isEmpty());
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.description").isEmpty());
    }

    @Test
    void rejectsDuplicateCodeOnUpdateWithoutChangingDepartment() throws Exception {
        create(Map.of("code", "HR", "name", "Human Resources"));
        String id = create(Map.of("code", "IT", "name", "Technology", "description", "Original"))
                .get("id").asText();
        Department original = departments.findById(UUID.fromString(id)).orElseThrow();
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" HR \",\"name\":\"Changed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("code")));
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("IT"))
                .andExpect(jsonPath("$.name").value("Technology"))
                .andExpect(jsonPath("$.description").value("Original"));
        assertThat(departments.findById(UUID.fromString(id)).orElseThrow().getUpdatedAt())
                .isEqualTo(original.getUpdatedAt());
    }

    @Test
    void rejectsUpdateForUnknownDepartmentOrMalformedId() throws Exception {
        String body = "{\"code\":\"IT\",\"name\":\"Technology\"}";
        mvc.perform(put(API + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(API + "/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(departments.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":\" \",\"name\":\" \"}", "{", ""})
    void rejectsInvalidUpdateBodies(String body) throws Exception {
        String id = create(Map.of("code", "IT", "name", "Technology")).get("id").asText();
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("IT"))
                .andExpect(jsonPath("$.name").value("Technology"));
    }

    private JsonNode create(Map<String, String> request) throws Exception {
        var result = mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        header().string("Location", API + "/" + created.get("id").asText()).match(result);
        return created;
    }
}
