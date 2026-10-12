package org.porteen.api;

import jakarta.validation.Valid;
import org.porteen.persistence.DeploymentRecord;
import org.porteen.persistence.DeploymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/deployments")
public class DeploymentController {

    private final DeploymentRepository repo;

    public DeploymentController(DeploymentRepository repo){
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> create(
            @Valid @RequestBody CreateDeploymentRequest req
    ) {
        String healthPath = req.healthPath() == null ? "/" : req.healthPath();

        DeploymentRecord saved = repo.save(new DeploymentRecord(
                req.tenant(), req.appName(), req.image(), req.port(), healthPath
        ));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(saved.getId()).toUri();

        return ResponseEntity.accepted().location(location).body(DeploymentResponse.from(saved));
    }

    @GetMapping("/{id}")
    public DeploymentResponse get(@PathVariable UUID id) {
        return repo.findById(id)
                .map(DeploymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No deployment " + id));
    }

}
