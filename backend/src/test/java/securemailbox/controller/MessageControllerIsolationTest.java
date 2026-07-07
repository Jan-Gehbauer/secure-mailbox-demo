package securemailbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End-Nachweis für den IDOR-Fix: zwei unabhängige, echte Nutzer,
 * jeder sieht ausschliesslich seinen eigenen Posteingang - unabhängig
 * davon, was der jeweils andere sendet/empfängt. Es gibt (bewusst) gar
 * keinen Pfad-Parameter mehr, über den ein Nutzer die Inbox eines
 * anderen adressieren könnte.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void users_canOnlySeeTheirOwnInboxAndSentMessages() throws Exception {
        String alice = "alice_" + System.nanoTime();
        String bob = "bob_" + System.nanoTime();

        String aliceToken = registerAndGetToken(alice);
        String bobToken = registerAndGetToken(bob);

        // Alice schickt Bob eine Nachricht
        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient": "%s", "subject": "Hallo", "body": "Geheimer Inhalt"}
                                """.formatted(bob)))
                .andExpect(status().isCreated());

        // Bob sieht die Nachricht in seiner Inbox
        mockMvc.perform(get("/api/messages/inbox")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sender").value(alice))
                .andExpect(jsonPath("$[0].body").value("Geheimer Inhalt"));

        // Alice sieht NICHTS in ihrer eigenen Inbox (sie hat nichts empfangen) -
        // insbesondere nicht die Nachricht, die sie selbst gesendet hat
        mockMvc.perform(get("/api/messages/inbox")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasEntry("body", "Geheimer Inhalt")))));

        // Alice sieht die Nachricht aber in ihrem "Gesendet"-Ordner
        mockMvc.perform(get("/api/messages/sent")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipient").value(bob));

        // Es gibt keinen Weg für Bob, an "fremde" Daten von Alice zu kommen -
        // es existiert schlicht kein Endpunkt, der einen Benutzernamen als
        // Parameter entgegennimmt (die einzige Quelle ist Authentication).
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s@example.com", "password": "sicheresPasswort123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
