package securemailbox.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End-Test des kompletten Auth-Flows gegen den echten
 * Application-Context (H2-Datenbank, echte Security-Filterkette).
 *
 * Deckt genau die Eigenschaften ab, die im Code-Kommentar als bewusste
 * Sicherheitsentscheidungen dokumentiert sind - dieser Test stellt
 * sicher, dass sie auch tatsächlich so funktionieren, wie beschrieben,
 * und nicht nur so gemeint waren.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void register_thenAccessProtectedEndpoint_succeedsWithIssuedToken() throws Exception {
        String username = uniqueUsername("inttest");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "sicheresPasswort123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        String accessToken = extractAccessToken(registerResult);

        mockMvc.perform(get("/api/messages/inbox")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void register_withDuplicateUsername_returnsConflict() throws Exception {
        String username = uniqueUsername("duptest");
        String body = registerBody(username, "sicheresPasswort123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_withTooShortPassword_returnsValidationErrorWithFieldDetails() throws Exception {
        String username = uniqueUsername("shortpw");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "zukurz")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void protectedEndpoint_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/messages/inbox"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/messages/inbox")
                        .header("Authorization", "Bearer offensichtlich.kein.gueltiges.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPasswordAndWithUnknownUser_returnIdenticalErrorMessage() throws Exception {
        // Verhindert User-Enumeration ueber die Fehlermeldung: ein Angreifer
        // darf aus der Antwort nicht ableiten koennen, ob der Benutzername
        // ueberhaupt existiert.
        String username = uniqueUsername("logintest");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, "sicheresPasswort123")));

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "falschesPasswort")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost_user_" + System.nanoTime(), "irgendwas")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownUser));
    }

    @Test
    void login_withCorrectCredentials_returnsAccessTokenAndRefreshCookie() throws Exception {
        String username = uniqueUsername("loginok");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, "sicheresPasswort123")));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "sicheresPasswort123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        assertThat(loginResult.getResponse().getCookie("refresh_token")).isNotNull();
    }

    @Test
    void refresh_rotatesTokenAndRejectsReuseOfOldToken() throws Exception {
        String username = uniqueUsername("refreshtest");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "sicheresPasswort123")))
                .andReturn();

        Cookie initialRefreshCookie = registerResult.getResponse().getCookie("refresh_token");
        assertThat(initialRefreshCookie).isNotNull();

        // Erster Refresh: muss funktionieren und einen NEUEN Cookie ausstellen
        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh").cookie(initialRefreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotatedCookie = firstRefresh.getResponse().getCookie("refresh_token");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(initialRefreshCookie.getValue());

        // Der ALTE (jetzt widerrufene) Cookie darf kein zweites Mal funktionieren
        mockMvc.perform(post("/api/auth/refresh").cookie(initialRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withoutCookie_isRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshTokenSoItCannotBeUsedAgain() throws Exception {
        String username = uniqueUsername("logouttest");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "sicheresPasswort123")))
                .andReturn();

        Cookie refreshCookie = registerResult.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private String uniqueUsername(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private String registerBody(String username, String password) {
        return """
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(username, username, password);
    }

    private String loginBody(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String messageOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("message").asText();
    }
}
