package io.github.pedrolucas167.lastro.api;

import io.github.pedrolucas167.lastro.config.Roles;
import io.github.pedrolucas167.lastro.domain.Client;
import io.github.pedrolucas167.lastro.dto.ClientResponse;
import io.github.pedrolucas167.lastro.dto.RegisterClientRequest;
import io.github.pedrolucas167.lastro.dto.SuspendClientRequest;
import io.github.pedrolucas167.lastro.exception.ClientNotFoundException;
import io.github.pedrolucas167.lastro.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** Cadastra o cliente de um titular. Ato de operador. */
    @PostMapping
    public ResponseEntity<ClientResponse> register(Authentication authentication,
                                                   @Valid @RequestBody RegisterClientRequest request) {
        Client client = clientService.register(Roles.actor(authentication), request.subject(),
            request.name());
        return ResponseEntity.ok(ClientResponse.of(client));
    }

    /** O proprio cadastro: e por aqui que o titular descobre que esta suspenso, e por que. */
    @GetMapping("/me")
    public ResponseEntity<ClientResponse> mine(Authentication authentication) {
        String subject = Roles.actor(authentication).subject();
        Client client = clientService.bySubject(subject)
            .orElseThrow(() -> new ClientNotFoundException(subject));
        return ResponseEntity.ok(ClientResponse.of(client));
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> list() {
        return ResponseEntity.ok(clientService.all().stream().map(ClientResponse::of).toList());
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponse> get(@PathVariable String clientId) {
        return ResponseEntity.ok(ClientResponse.of(clientService.get(clientId)));
    }

    @PostMapping("/{clientId}/suspend")
    public ResponseEntity<ClientResponse> suspend(Authentication authentication,
                                                  @PathVariable String clientId,
                                                  @RequestBody(required = false) SuspendClientRequest request) {
        Client client = clientService.suspend(Roles.actor(authentication), clientId,
            request != null ? request.reason() : null);
        return ResponseEntity.ok(ClientResponse.of(client));
    }

    @PostMapping("/{clientId}/reactivate")
    public ResponseEntity<ClientResponse> reactivate(Authentication authentication,
                                                     @PathVariable String clientId) {
        return ResponseEntity.ok(ClientResponse.of(
            clientService.reactivate(Roles.actor(authentication), clientId)));
    }
}
