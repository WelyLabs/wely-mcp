package com.calendar.mcp.application.mcp;

import com.calendar.mcp.application.dtos.EventSummaryResponse;
import com.calendar.mcp.application.mappers.EventSummaryToolMapperImpl;
import com.calendar.mcp.domain.models.EventSummary;
import com.calendar.mcp.domain.ports.EventCatalog;
import com.calendar.mcp.domain.services.MyEventsService;
import com.calendar.mcp.exception.BusinessErrorCode;
import com.calendar.mcp.exception.BusinessException;
import com.calendar.mcp.exception.TechnicalErrorCode;
import com.calendar.mcp.exception.TechnicalException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarToolsTest {

    private static final String FROM = "2026-10-21T00:00:00Z";
    private static final String TO = "2026-10-22T00:00:00Z";

    private CalendarTools tools;

    private static CalendarTools toolsReturning(EventSummary... events) {
        EventCatalog catalog = () -> Flux.just(events);
        return new CalendarTools(new MyEventsService(catalog), new EventSummaryToolMapperImpl());
    }

    private static void authenticateCaller() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .claim("businessId", "11111111-1111-1111-1111-111111111111")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));
    }

    @BeforeEach
    void setUp() {
        tools = toolsReturning(new EventSummary("e1", "Dinner",
                Instant.parse("2026-10-21T19:00:00Z"), Instant.parse("2026-10-21T22:00:00Z"),
                "Niort", false));
        authenticateCaller();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMyEvents_shouldReturnTheCallersEventsInTheRange() {
        List<EventSummaryResponse> events = tools.listMyEvents(FROM, TO);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.id()).isEqualTo("e1");
            assertThat(event.title()).isEqualTo("Dinner");
            assertThat(event.location()).isEqualTo("Niort");
        });
    }

    @Test
    void listMyEvents_shouldRenderDatesAsIsoInstants() {
        List<EventSummaryResponse> events = tools.listMyEvents(FROM, TO);

        assertThat(events.getFirst().startDate()).isEqualTo("2026-10-21T19:00:00Z");
        assertThat(events.getFirst().endDate()).isEqualTo("2026-10-21T22:00:00Z");
    }

    @Test
    void listMyEvents_shouldReturnAnEmptyListWhenNothingMatches() {
        assertThat(tools.listMyEvents("2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z")).isEmpty();
    }

    @Test
    void listMyEvents_shouldRejectAnUnreadableDate() {
        assertThatThrownBy(() -> tools.listMyEvents("21/10/2026", TO))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(BusinessErrorCode.TIME_RANGE_UNREADABLE);
    }

    @Test
    void listMyEvents_shouldRejectAMissingDate() {
        assertThatThrownBy(() -> tools.listMyEvents(null, TO))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listMyEvents_shouldRejectAnInvertedRange() {
        assertThatThrownBy(() -> tools.listMyEvents(TO, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(BusinessErrorCode.TIME_RANGE_INVALID);
    }

    @Test
    void listMyEvents_shouldRefuseWhenNoCallerIsAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> tools.listMyEvents(FROM, TO))
                .isInstanceOf(TechnicalException.class)
                .extracting(error -> ((TechnicalException) error).getErrorCode())
                .isEqualTo(TechnicalErrorCode.CALLER_TOKEN_MISSING);
    }

    @Test
    void listMyEvents_shouldRefuseWhenTheCallerIsNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("anonymous", null));

        assertThatThrownBy(() -> tools.listMyEvents(FROM, TO))
                .isInstanceOf(TechnicalException.class);
    }

    @Test
    void listMyEvents_shouldPropagateACatalogFailure() {
        EventCatalog failing = () -> Flux.error(new TechnicalException(TechnicalErrorCode.GATEWAY_REFUSED));
        CalendarTools failingTools = new CalendarTools(new MyEventsService(failing), new EventSummaryToolMapperImpl());

        assertThatThrownBy(() -> failingTools.listMyEvents(FROM, TO))
                .isInstanceOf(TechnicalException.class)
                .extracting(error -> ((TechnicalException) error).getErrorCode())
                .isEqualTo(TechnicalErrorCode.GATEWAY_REFUSED);
    }
}
