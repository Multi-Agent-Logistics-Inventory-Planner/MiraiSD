package com.mirai.inventoryservice.services;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final String fromEmail;
    private final String fromName;
    private final boolean devMode;

    public EmailService(
            @Value("${resend.api.key}") String apiKey,
            @Value("${resend.from.email}") String fromEmail,
            @Value("${resend.from.name:Mirai Inventory}") String fromName) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;

        // Enable dev mode if no real API key is provided
        this.devMode = apiKey == null || apiKey.isBlank() || apiKey.equals("re_test_key");

        if (devMode) {
            this.resend = null;
            log.warn("EmailService running in DEV MODE - emails will be logged, not sent");
        } else {
            this.resend = new Resend(apiKey);
            log.info("EmailService initialized with Resend API");
        }
    }

    public void sendInvitationEmail(String toEmail, String inviteLink, String role, String inviterName) {
        String subject = "You've been invited to join Mirai Inventory";

        if (devMode) {
            log.info("=== DEV MODE: Invitation Email ===");
            log.info("To: {}", toEmail);
            log.info("Role: {}", role);
            log.info("Invited by: {}", inviterName);
            log.info("Invite link: {}", inviteLink);
            log.info("==================================");
            return;
        }

        String html = buildInvitationEmailHtml(inviteLink, role, inviterName);
        sendEmail(toEmail, subject, html);
    }

    public void sendEmail(String to, String subject, String html) {
        if (devMode) {
            log.info("=== DEV MODE: Email ===");
            log.info("To: {}", to);
            log.info("Subject: {}", subject);
            log.info("=======================");
            return;
        }

        try {
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(to)
                    .subject(subject)
                    .html(html)
                    .build();

            CreateEmailResponse response = resend.emails().send(options);
            log.info("Email sent successfully to {}, id: {}", to, response.getId());
        } catch (ResendException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildInvitationEmailHtml(String inviteLink, String role, String inviterName) {
        String roleDisplay = role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: #2563eb; padding: 30px; border-radius: 10px 10px 0 0; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 24px;">Mirai Inventory</h1>
                </div>

                <div style="background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 10px 10px;">
                    <h2 style="color: #333; margin-top: 0;">You're Invited!</h2>

                    <p>Hi there,</p>

                    <p><strong>%s</strong> has invited you to join Mirai Inventory as an <strong>%s</strong>.</p>

                    <p>Click the button below to accept your invitation and set up your account:</p>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2563eb; color: white; padding: 14px 30px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">
                            Accept Invitation
                        </a>
                    </div>

                    <p style="color: #666; font-size: 14px;">If the button doesn't work, copy and paste this link into your browser:</p>
                    <p style="color: #2563eb; font-size: 14px; word-break: break-all;">%s</p>

                    <hr style="border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;">

                    <p style="color: #999; font-size: 12px; margin-bottom: 0;">
                        This invitation was sent by Mirai Inventory. If you didn't expect this email, you can safely ignore it.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(inviterName, roleDisplay, inviteLink, inviteLink);
    }
}
