package org.porteen.api;

import jakarta.validation.constraints.*;

public record CreateDeploymentRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([-a-z0-9]{0,30}[a-z0-9])?$",
                message = "lowercase letters, digits and '-', max 32 chars, must start and end with a letter or digit")
        String tenant,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]([-a-z0-9]{0,30}[a-z0-9])?$",
                message = "lowercase letters, digits and '-', max 32 chars, must start and end with a letter or digit")
        String appName,

        @NotBlank String image,

        @Min(1) @Max(65535) int port,

        @Pattern(regexp = "^/.*", message = "must start with '/'")
        String healthPath   // optional, defaults to "/"
) {}