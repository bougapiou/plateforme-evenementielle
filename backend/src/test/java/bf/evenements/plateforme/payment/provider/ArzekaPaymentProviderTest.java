package bf.evenements.plateforme.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bf.evenements.plateforme.common.config.ArzekaProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.money.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * No Spring context, no network — pure logic: URL building, phone
 * normalisation and, most importantly, that a webhook result is NEVER taken
 * from the raw (unsigned) callback and always re-derived from the
 * authenticated "check status" call.
 */
@ExtendWith(MockitoExtension.class)
class ArzekaPaymentProviderTest {

    @Mock
    private ArzekaTokenService tokenService;
    @Mock
    private RestTemplate restTemplate;

    private ArzekaPaymentProvider provider;

    @BeforeEach
    void setUp() {
        ArzekaProperties props = new ArzekaProperties(
                "https://pgw-test.fasoarzeka.bf",
                "/AvepayPaymentGatewayUI/avepay-payment/app/validorder",
                "/AvepayPaymentGatewayUI/avepay-payment/app/getThirdPartyMapInfo",
                "/AvepayPaymentGatewayUI/avepay-payment/auth/getToken",
                "222", "22600000133", "secret", "access_token",
                "http://localhost:8080", null);
        provider = new ArzekaPaymentProvider(props, tokenService, restTemplate, new ObjectMapper());
    }

    @Test
    void initiate_builds_the_validorder_url_with_all_required_params() {
        when(tokenService.getValidToken()).thenReturn("tok123");

        var initiation = provider.initiate(new PaymentProvider.Context(
                "PAY-ABC123", new Money(new BigDecimal("1500"), "XOF"),
                "buyer@example.bf", "+226 70 00 00 00", "Billets SIAO", null));

        assertThat(initiation.providerRef()).isEqualTo("PAY-ABC123");
        URI uri = URI.create(initiation.redirectUrl());
        assertThat(uri.toString()).startsWith("https://pgw-test.fasoarzeka.bf"
                + "/AvepayPaymentGatewayUI/avepay-payment/app/validorder");
        assertThat(uri.getQuery())
                .contains("amount=1500")
                .contains("msisdn=22670000000")
                .contains("merchantid=222")
                .contains("securedAccessToken=tok123")
                .contains("mappedOrderId=PAY-ABC123");

        String callbackParam = parseQuery(uri).get("linkBackToCallingWebsite");
        String decoded = new String(Base64.getDecoder().decode(callbackParam), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("http://localhost:8080/api/payments/arzeka/callback");
    }

    @Test
    void initiate_without_a_phone_number_is_rejected() {
        assertThatThrownBy(() -> provider.initiate(new PaymentProvider.Context(
                "PAY-ABC123", new Money(new BigDecimal("1500"), "XOF"),
                "buyer@example.bf", null, "Billets SIAO", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("téléphone");
    }

    @Test
    void check_status_maps_completed_to_success() {
        when(tokenService.getValidToken()).thenReturn("tok123");
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "COMPLETED",
                        "third_party_trans_id", "OMRCH240918.2350.J00021")));

        var result = provider.checkStatus("PAY-ABC123");

        assertThat(result.outcome()).isEqualTo(PaymentProvider.Outcome.SUCCESS);
        assertThat(result.transactionRef()).isEqualTo("OMRCH240918.2350.J00021");
    }

    @Test
    void check_status_maps_not_found_to_pending_instead_of_failing() {
        when(tokenService.getValidToken()).thenReturn("tok123");
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], null));

        var result = provider.checkStatus("PAY-ABC123");

        assertThat(result.outcome()).isEqualTo(PaymentProvider.Outcome.PENDING);
    }

    @Test
    void verify_webhook_never_trusts_the_raw_callback_status() {
        // The unsigned callback claims SUCCESS...
        String rawBody = "{\"status\":\"SUCCESS\",\"paymentRequestId\":\"PAY-ABC123\"}";
        // ...but the authenticated check-status call says otherwise: that must win.
        when(tokenService.getValidToken()).thenReturn("tok123");
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "REJECTED")));

        var result = provider.verifyWebhook(rawBody, null);

        assertThat(result.reference()).isEqualTo("PAY-ABC123");
        assertThat(result.outcome()).isEqualTo(PaymentProvider.Outcome.FAILED);
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String part : uri.getQuery().split("&")) {
            String[] kv = part.split("=", 2);
            map.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        return map;
    }
}
