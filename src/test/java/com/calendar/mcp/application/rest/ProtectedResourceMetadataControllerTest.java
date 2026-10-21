package com.calendar.mcp.application.rest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedResourceMetadataControllerTest {

    private static final String PUBLIC_URL = "https://mcp.wely.dev";
    private static final String ISSUER = "https://auth.wely.dev/realms/wely-realm";

    private final ProtectedResourceMetadataController controller =
            new ProtectedResourceMetadataController(PUBLIC_URL, ISSUER);

    @Test
    void metadata_shouldNameThisServiceAsTheResource() {
        assertThat(controller.metadata()).containsEntry("resource", PUBLIC_URL);
    }

    @Test
    void metadata_shouldPointAtTheIssuerSoAClientKnowsWhereToSignIn() {
        Map<String, Object> metadata = controller.metadata();

        assertThat(metadata.get("authorization_servers")).isEqualTo(List.of(ISSUER));
    }

    @Test
    void metadata_shouldAnnounceTheHeaderAsTheWayToCarryAToken() {
        assertThat(controller.metadata().get("bearer_methods_supported")).isEqualTo(List.of("header"));
    }
}
