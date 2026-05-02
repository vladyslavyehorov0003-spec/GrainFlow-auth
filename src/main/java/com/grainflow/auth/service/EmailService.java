package com.grainflow.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.from-email}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String link = baseUrl + "/verify?token=" + token;

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("Verify your GrainFlow account");
            msg.setText("""
                    Welcome to GrainFlow!

                    Please verify your email address by clicking the link below:

                    %s

                    This link expires in 24 hours.

                    If you didn't create a GrainFlow account, you can ignore this email.
                    """.formatted(link));

            mailSender.send(msg);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // Sent to the OLD email when a user requests to change their address.
    // Contains a link with a token — clicking it opens the confirm page in the app
    // where the user pastes the 6-digit code that was simultaneously sent to the
    // NEW email. Two-factor confirmation: control of both addresses is required.
    @Async
    public void sendEmailChangeLink(String toOldEmail, String token, String newEmail) {
        try {
            String link = baseUrl + "/email-change-confirm?token=" + token;

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toOldEmail);
            msg.setSubject("GrainFlow — confirm email change");
            msg.setText("""
                    Someone (hopefully you) requested to change the email on your GrainFlow account to:

                    %s

                    To confirm, click the link below and enter the 6-digit code that was sent to the new address:

                    %s

                    This link expires in 1 hour.

                    If you did not request this, ignore this email — the change will not take effect without
                    both this link AND the code from the new address.
                    """.formatted(newEmail, link));

            mailSender.send(msg);
            log.info("Email change link sent to old address {}", toOldEmail);
        } catch (Exception e) {
            log.error("Failed to send email change link to {}: {}", toOldEmail, e.getMessage(), e);
        }
    }

    // Sent when a user runs the "forgot password" flow. The token in the link is
    // a UUID; we keep only its SHA-256 hash in DB, so the raw token only exists
    // in the user's inbox. Single-use, expires after 1 hour.
    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            String link = baseUrl + "/reset-password?token=" + token;

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("GrainFlow — reset your password");
            msg.setText("""
                    Someone (hopefully you) requested a password reset for your GrainFlow account.

                    Click the link below to choose a new password:

                    %s

                    This link expires in 1 hour and can only be used once.

                    If you did not request this, ignore this email — your password will not change.
                    """.formatted(link));

            mailSender.send(msg);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // Sent to the NEW email simultaneously with the link above. Just the bare code —
    // the user pastes it into the form opened by clicking the link in the old inbox.
    @Async
    public void sendEmailChangeCode(String toNewEmail, String code) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toNewEmail);
            msg.setSubject("GrainFlow — your email change code");
            msg.setText("""
                    Your GrainFlow email change confirmation code:

                    %s

                    This code expires in 1 hour. Enter it in the confirmation page that opens
                    when you click the link sent to your previous email address.

                    If you did not request this, ignore this email.
                    """.formatted(code));

            mailSender.send(msg);
            log.info("Email change code sent to new address {}", toNewEmail);
        } catch (Exception e) {
            log.error("Failed to send email change code to {}: {}", toNewEmail, e.getMessage(), e);
        }
    }
}
