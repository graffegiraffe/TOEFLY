package com.toefly.billing.adapter.in.web;

import com.toefly.billing.application.port.in.WebhookUseCase;
import com.toefly.billing.domain.subscription.PaymentProviderCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhooks of the providers (docs/05 §6.12). The body is read as bytes: the signature is checked on the exact raw
 * body before any parsing; the body is never logged (docs/10 §17.3).
 */
@RestController
public class WebhookController {

    static final Map<String, PaymentProviderCode> PROVIDERS = Map.of(
            "stripe", PaymentProviderCode.STRIPE,
            "bepaid", PaymentProviderCode.BEPAID,
            "app-store", PaymentProviderCode.APP_STORE);

    private final WebhookUseCase webhooks;

    public WebhookController(WebhookUseCase webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/api/v1/billing/webhooks/{provider}")
    public ResponseEntity<Void> receive(
            @PathVariable String provider, @RequestHeader Map<String, String> headers, @RequestBody byte[] body) {
        PaymentProviderCode code = PROVIDERS.get(provider);
        if (code == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> lowerCase = new LinkedHashMap<>();
        headers.forEach((name, value) -> lowerCase.put(name.toLowerCase(java.util.Locale.ROOT), value));
        webhooks.receive(code, Collections.unmodifiableMap(lowerCase), body);
        return ResponseEntity.ok().build();
    }
}
