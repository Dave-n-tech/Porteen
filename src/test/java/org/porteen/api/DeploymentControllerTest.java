package org.porteen.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.porteen.persistence.DeploymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired DeploymentRepository repo;
    @Value("${spring.datasource.url}") String dbUrl;

    @BeforeEach
    void guardAndClean() {
        assertTrue(dbUrl.endsWith("porteen_test"), "Tests must never run against " + dbUrl);
        repo.deleteAll();
    }

    private static final String VALID = """
        {"tenant":"demo","appName":"my-app","image":"node-app:v2","port":8080,"healthPath":"/health"}""";

    @Test
    void postReturns202WithLocationAndPendingStatus() throws Exception {
        mvc.perform(post("/deployments").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void getReturnsWhatWasPosted() throws Exception {
        MvcResult created = mvc.perform(post("/deployments")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andReturn();
        String location = created.getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("my-app"))
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void invalidTenantIsRejectedWithFieldDetails() throws Exception {
        String bad = VALID.replace("\"demo\"", "\"Demo_2\"");
        mvc.perform(post("/deployments").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.tenant").exists());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(get("/deployments/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}