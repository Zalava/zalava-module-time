package org.zalava.modules.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.testing.ConfigFixture;
import org.zalava.testing.ModuleContractKit;
import org.zalava.testing.ProviderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real built module JAR at the stable {@code module-api} boundary through the released
 * contract kit. Host-owned resolution, validation, permissions and persistence stay covered by SEA.
 */
class TimeSeaModuleTest {

    private static final String MODULE_ID = "zalava-module-time";
    private static final String FACTORY_ID = "jdk-time";
    private static final String PROVIDER_ID = "jdk-time";
    private static final String CURRENT_TIME = "current_time";
    private static final String CONVERT_TIME = "convert_time";

    private ModuleContractKit kit;

    @BeforeEach
    void loadTheBuiltArtifact() {
        Path artifact = Path.of(System.getProperty("module.artifact"));
        String version = System.getProperty("module.version");
        kit = ModuleContractKit.load(artifact, List.of(), MODULE_ID, version);
    }

    @AfterEach
    void closeTheArtifact() throws Exception {
        if (kit != null) {
            kit.close();
        }
    }

    @Test
    void loadsTheModuleFromTheBuiltArtifact() {
        assertThat(kit.module().getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
        assertThat(kit.module().getClass().getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith(".jar");
    }

    @Test
    void exposesTheModuleOwnedDescriptorAndEmptyConfigurationContract() {
        assertThat(kit.moduleId()).isEqualTo(MODULE_ID);
        assertThat(kit.version()).isEqualTo(System.getProperty("module.version"));

        Map<String, Object> schema = kit.module().configuration().jsonSchema();
        assertThat(schema).containsEntry("type", "object");
        assertThat(schema.get("properties")).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) schema.get("properties")).isEmpty();
    }

    @Test
    void createsTheReadOnlyProviderAndDeclaresItsTools() {
        try (ProviderFixture providers = kit.providers(ConfigFixture.empty())) {
            ZalavaProvider provider = providers.requireProvider(PROVIDER_ID);

            assertThat(providers.providers()).hasSize(1);
            assertThat(provider.descriptor().moduleId()).isEqualTo(MODULE_ID);
            assertThat(provider.descriptor().providerType()).isEqualTo("time");
            assertThat(provider.listTools().stream().map(ZalavaToolDescriptor::name))
                    .containsExactly(CURRENT_TIME, CONVERT_TIME);
            assertThat(provider.listTools()).allSatisfy(tool -> assertThat(tool.sideEffecting()).isFalse());

            ZalavaToolDescriptor convert = providers.requireTool(PROVIDER_ID, CONVERT_TIME);
            assertThat(convert.inputSchema()).containsEntry("required", List.of("targetZoneId"));
        }
    }

    @Test
    void createsItsProviderWhenTheHostSuppliesScopedConfigurationAndSecrets() {
        ConfigFixture configuration = ConfigFixture.empty()
                .factoryConfiguration(MODULE_ID, FACTORY_ID, Map.of("clock", "system"))
                .secrets(MODULE_ID, reference -> Optional.of(reference.toCharArray()));

        try (ProviderFixture providers = kit.providers(configuration)) {
            assertThat(providers.requireProvider(PROVIDER_ID).listTools()).hasSize(2);
        }
    }

    @Test
    void defaultsCurrentTimeToUtcWithConsistentInstantAndZone() {
        try (ProviderFixture providers = kit.providers()) {
            ZalavaOperationResult result = providers.invoke(PROVIDER_ID, CURRENT_TIME, arguments());
            assertThat(result.success()).isTrue();
            Map<String, Object> content = content(result);

            assertThat(content).containsEntry("zoneId", "UTC").containsEntry("offset", "Z");
            assertThat(ZonedDateTime.parse(text(content, "zonedDateTime")).toInstant())
                    .isEqualTo(Instant.parse(text(content, "instant")));
        }
    }

    @Test
    void currentTimeUsesTheRequestedIanaZone() {
        try (ProviderFixture providers = kit.providers()) {
            ZalavaOperationResult result = providers.invoke(PROVIDER_ID, CURRENT_TIME,
                    arguments().put("zoneId", "Europe/Madrid"));
            assertThat(result.success()).isTrue();
            Map<String, Object> content = content(result);

            assertThat(content).containsEntry("zoneId", "Europe/Madrid");
            assertThat(ZonedDateTime.parse(text(content, "zonedDateTime")).toInstant())
                    .isEqualTo(Instant.parse(text(content, "instant")));
        }
    }

    @Test
    void convertsFixedInstantToFixedTargetZone() {
        try (ProviderFixture providers = kit.providers()) {
            ZalavaOperationResult result = providers.invoke(PROVIDER_ID, CONVERT_TIME,
                    arguments().put("instant", "2026-01-01T00:00:00Z").put("targetZoneId", "America/New_York"));
            assertThat(result.success()).isTrue();
            Map<String, Object> content = content(result);

            assertThat(content)
                    .containsEntry("instant", "2026-01-01T00:00:00Z")
                    .containsEntry("zoneId", "America/New_York")
                    .containsEntry("offset", "-05:00")
                    .containsEntry("zonedDateTime", "2025-12-31T19:00-05:00[America/New_York]");
        }
    }

    @Test
    void convertsFixedZonedDateTimeToFixedTargetZone() {
        try (ProviderFixture providers = kit.providers()) {
            ZalavaOperationResult result = providers.invoke(PROVIDER_ID, CONVERT_TIME,
                    arguments().put("zonedDateTime", "2026-06-17T14:34:56+02:00[Europe/Madrid]")
                            .put("targetZoneId", "UTC"));
            assertThat(result.success()).isTrue();
            Map<String, Object> content = content(result);

            assertThat(content)
                    .containsEntry("instant", "2026-06-17T12:34:56Z")
                    .containsEntry("zoneId", "UTC")
                    .containsEntry("offset", "Z")
                    .containsEntry("zonedDateTime", "2026-06-17T12:34:56Z[UTC]");
        }
    }

    @Test
    void rejectsMissingAndAmbiguousConversionInputs() {
        try (ProviderFixture providers = kit.providers()) {
            assertThat(content(providers.invoke(PROVIDER_ID, CONVERT_TIME,
                    arguments().put("targetZoneId", "UTC"))))
                    .isEqualTo(Map.of("error", "Provide exactly one of instant or zonedDateTime"));

            assertThat(content(providers.invoke(PROVIDER_ID, CONVERT_TIME,
                    arguments().put("instant", "2026-01-01T00:00:00Z")
                            .put("zonedDateTime", "2026-01-01T01:00:00+01:00[Europe/Madrid]")
                            .put("targetZoneId", "UTC"))))
                    .isEqualTo(Map.of("error", "Provide exactly one of instant or zonedDateTime"));
        }
    }

    @Test
    void rejectsMalformedDateTimeAndUnknownZones() {
        try (ProviderFixture providers = kit.providers()) {
            ZalavaOperationResult malformed = providers.invoke(PROVIDER_ID, CONVERT_TIME,
                    arguments().put("instant", "not-a-date").put("targetZoneId", "UTC"));
            assertThat(malformed.success()).isFalse();
            assertThat(content(malformed)).containsEntry("error", "Invalid date-time value");

            ZalavaOperationResult unknown = providers.invoke(PROVIDER_ID, CURRENT_TIME,
                    arguments().put("zoneId", "Mars/Base"));
            assertThat(unknown.success()).isFalse();
            assertThat(content(unknown)).isEqualTo(Map.of("error", "Unknown IANA time zone: Mars/Base"));
        }
    }

    private static ObjectNode arguments() {
        return JsonNodeFactory.instance.objectNode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(ZalavaOperationResult result) {
        return (Map<String, Object>) result.content();
    }

    private static String text(Map<String, Object> content, String field) {
        return (String) content.get(field);
    }
}
