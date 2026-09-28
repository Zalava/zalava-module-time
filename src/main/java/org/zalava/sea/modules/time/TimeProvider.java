package org.zalava.modules.time;

import tools.jackson.databind.JsonNode;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class TimeProvider implements SeaProvider {

    static final String PROVIDER_ID = "jdk-time";
    static final String CURRENT_TIME = "current_time";
    static final String CONVERT_TIME = "convert_time";

    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final List<SeaToolDescriptor> tools;

    TimeProvider(Clock clock) {
        this.clock = clock;
        this.descriptor = new ProviderDescriptor(
                PROVIDER_ID,
                TimeSeaModule.MODULE_ID,
                TimeProviderFactory.PROVIDER_TYPE,
                "JDK Time",
                "Read-only provider for current time and timezone conversion",
                TimeSeaModule.version(),
                ProviderCapabilities.toolsOnly(),
                List.of("read-only", "time"),
                Map.of("clock", "system"));
        this.tools = List.of(currentTimeTool(), convertTimeTool());
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return descriptor.capabilities();
    }

    @Override
    public List<SeaToolDescriptor> listTools() {
        return tools;
    }

    @Override
    public SeaOperationResult callTool(String toolName, JsonNode arguments, InvocationContext context) {
        return switch (toolName) {
            case CURRENT_TIME -> currentTime(arguments);
            case CONVERT_TIME -> convertTime(arguments);
            default -> SeaOperationResult.failure(Map.of("error", "Unsupported tool: " + toolName));
        };
    }

    private SeaOperationResult currentTime(JsonNode arguments) {
        ZoneId zone;
        try {
            zone = zone(arguments, "zoneId", ZoneId.of("UTC"));
        } catch (IllegalArgumentException ex) {
            return SeaOperationResult.failure(Map.of("error", ex.getMessage()));
        }
        Instant instant = clock.instant();
        return SeaOperationResult.success(timePayload(instant, zone));
    }

    private SeaOperationResult convertTime(JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return SeaOperationResult.failure(Map.of("error", "Arguments are required"));
        }
        ZoneId targetZone;
        try {
            targetZone = zone(arguments, "targetZoneId", null);
        } catch (IllegalArgumentException ex) {
            return SeaOperationResult.failure(Map.of("error", ex.getMessage()));
        }
        if (targetZone == null) {
            return SeaOperationResult.failure(Map.of("error", "targetZoneId is required"));
        }
        boolean hasInstant = hasText(arguments, "instant");
        boolean hasZonedDateTime = hasText(arguments, "zonedDateTime");
        if (hasInstant == hasZonedDateTime) {
            return SeaOperationResult.failure(Map.of(
                    "error", "Provide exactly one of instant or zonedDateTime"));
        }
        try {
            Instant instant = hasInstant
                    ? Instant.parse(arguments.path("instant").asText())
                    : parseZonedInstant(arguments.path("zonedDateTime").asText());
            return SeaOperationResult.success(timePayload(instant, targetZone));
        } catch (DateTimeException ex) {
            return SeaOperationResult.failure(Map.of(
                    "error", "Invalid date-time value",
                    "detail", ex.getMessage()));
        }
    }

    private Instant parseZonedInstant(String value) {
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (DateTimeException ignored) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private ZoneId zone(JsonNode arguments, String field, ZoneId defaultZone) {
        if (arguments == null || arguments.isNull() || !hasText(arguments, field)) {
            return defaultZone;
        }
        try {
            return ZoneId.of(arguments.path(field).asText());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Unknown IANA time zone: " + arguments.path(field).asText(), ex);
        }
    }

    private boolean hasText(JsonNode arguments, String field) {
        return arguments.hasNonNull(field) && !arguments.path(field).asText().isBlank();
    }

    private Map<String, Object> timePayload(Instant instant, ZoneId zone) {
        ZonedDateTime zoned = instant.atZone(zone);
        return Map.of(
                "instant", instant.toString(),
                "zoneId", zone.getId(),
                "offset", zoned.getOffset().toString(),
                "zonedDateTime", zoned.toString());
    }

    private SeaToolDescriptor currentTimeTool() {
        return new SeaToolDescriptor(
                CURRENT_TIME,
                "Return the current time for an optional IANA time zone",
                false,
                List.of("read-only", "time"),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "zoneId", Map.of(
                                        "type", "string",
                                        "description", "IANA time zone, defaulting to UTC")),
                        "additionalProperties", false));
    }

    private SeaToolDescriptor convertTimeTool() {
        return new SeaToolDescriptor(
                CONVERT_TIME,
                "Convert an ISO instant or zoned date-time to a target IANA time zone",
                false,
                List.of("read-only", "time"),
                Map.of(
                        "type", "object",
                        "required", List.of("targetZoneId"),
                        "properties", Map.of(
                                "instant", Map.of(
                                        "type", "string",
                                        "description", "ISO-8601 instant, for example 2026-06-17T12:00:00Z"),
                                "zonedDateTime", Map.of(
                                        "type", "string",
                                        "description", "ISO-8601 zoned or offset date-time"),
                                "targetZoneId", Map.of(
                                        "type", "string",
                                        "description", "Target IANA time zone")),
                        "additionalProperties", false));
    }
}
