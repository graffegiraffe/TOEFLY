package com.toefly.billing.adapter.in.web;

import com.toefly.billing.application.port.in.CheckoutEmulatorUseCase;
import com.toefly.billing.domain.checkout.CheckoutSession;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The hosted page of the Stripe emulator (profiles local and demo): no card fields, only the two test cards as buttons.
 * Card data never reaches TOEFLY, as with the real hosted checkout.
 */
@RestController
@Profile({"local", "demo"})
public class DevCheckoutController {

    private final CheckoutEmulatorUseCase emulator;

    public DevCheckoutController(CheckoutEmulatorUseCase emulator) {
        this.emulator = emulator;
    }

    @GetMapping(value = "/dev/checkout/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable UUID sessionId) {
        return emulator.find(sessionId)
                .map(session -> ResponseEntity.ok(html(session)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("<p>No such checkout.</p>"));
    }

    @PostMapping("/dev/checkout/{sessionId}")
    public ResponseEntity<Void> pay(@PathVariable UUID sessionId, @RequestParam String card) {
        String back = emulator.complete(sessionId, card.replace(" ", "").equals("4242424242424242"));
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(back))
                .build();
    }

    private static String html(CheckoutSession session) {
        String plan = session.tier().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String price = switch (session.tier()) {
            case STUDENT_PRO -> "€12.00";
            case TUTOR -> "€29.00";
            case TUTOR_PLUS -> "€59.00";
            case STUDENT_FREE -> "€0.00";
        };
        String action = "/dev/checkout/" + session.id();
        String cancel = HtmlUtils.htmlEscape(session.returnUrl() + "?session=" + session.id() + "&canceled=true");
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Test checkout · Stripe emulator</title>
                <style>
                body{font-family:system-ui,sans-serif;background:#f6f8fa;color:#1a1f36;margin:0;display:grid;
                place-items:center;min-height:100vh}
                main{background:#fff;border-radius:12px;box-shadow:0 8px 30px #0001;padding:32px;max-width:420px;width:90%%}
                .badge{display:inline-block;background:#fff4e5;color:#8a4b00;border-radius:6px;padding:4px 8px;
                font-size:13px;font-weight:600}
                h1{font-size:20px;margin:16px 0 4px}.price{font-size:32px;font-weight:700;margin:8px 0 24px}
                button{width:100%%;padding:12px;border-radius:8px;border:1px solid #d0d7de;background:#fff;
                font-size:15px;margin-top:10px;cursor:pointer}button.pay{background:#635bff;color:#fff;border:0}
                p.note{font-size:13px;color:#57606a}a{color:#635bff}
                </style></head><body><main>
                <span class="badge">Test mode — no real charges</span>
                <h1>TOEFLY %s</h1><div class="price">%s <small>/ month</small></div>
                <form method="post" action="%s"><input type="hidden" name="card" value="4242424242424242">
                <button class="pay" type="submit">Pay with test card 4242 4242 4242 4242</button></form>
                <form method="post" action="%s"><input type="hidden" name="card" value="4000000000000002">
                <button type="submit">Use declined card 4000 0000 0000 0002</button></form>
                <p class="note">Local Stripe emulator of billing-service. The webhook is signed with the local secret
                and verified like a real Stripe event.</p>
                <p><a href="%s">Cancel and go back</a></p>
                </main></body></html>
                """.formatted(plan, price, action, action, cancel);
    }
}
