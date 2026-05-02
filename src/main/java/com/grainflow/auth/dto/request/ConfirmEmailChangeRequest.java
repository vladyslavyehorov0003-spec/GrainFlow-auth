package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Two-factor confirmation of an email change:
 *   - token comes from the link the user clicked in their OLD email
 *     (proves they still control the current address)
 *   - code is the 6-digit value sent to the NEW email
 *     (proves they own the new address)
 * Both must match the same email_change_codes row.
 */
public record ConfirmEmailChangeRequest(

        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
        String code
) {}
