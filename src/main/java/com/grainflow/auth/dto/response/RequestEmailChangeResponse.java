package com.grainflow.auth.dto.response;

/**
 * Tells the frontend which flow it just triggered:
 *
 *  - status = "CHANGED"   → email was changed immediately (company was UNVERIFIED).
 *                           tokens are non-null (frontend should overwrite stored ones).
 *  - status = "CODE_SENT" → 6-digit code was sent to the OLD email.
 *                           Frontend should now show the "enter code" screen.
 *                           tokens is null at this stage.
 */
public record RequestEmailChangeResponse(
        String status,
        AuthResponse tokens
) {
    public static RequestEmailChangeResponse changed(AuthResponse tokens) {
        return new RequestEmailChangeResponse("CHANGED", tokens);
    }

    public static RequestEmailChangeResponse codeSent() {
        return new RequestEmailChangeResponse("CODE_SENT", null);
    }
}
