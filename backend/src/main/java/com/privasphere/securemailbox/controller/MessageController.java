package com.privasphere.securemailbox.controller;

import com.privasphere.securemailbox.dto.MessageResponse;
import com.privasphere.securemailbox.dto.SendMessageRequest;
import com.privasphere.securemailbox.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CORS ist hier bewusst offen für localhost:4200 (Angular Dev-Server).
 * In Produktion würde man das über eine explizite Config-Klasse
 * und eine Whitelist von Origins lösen statt per Annotation.
 */
@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "http://localhost:4200")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@Valid @RequestBody SendMessageRequest request) {
        return messageService.send(request);
    }

    @GetMapping("/inbox/{recipient}")
    public List<MessageResponse> inbox(@PathVariable String recipient) {
        return messageService.getInbox(recipient);
    }

    @GetMapping("/sent/{sender}")
    public List<MessageResponse> sent(@PathVariable String sender) {
        return messageService.getSent(sender);
    }
}
