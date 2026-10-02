package org.zalava.modules.time;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;

public final class TimeSeaModule implements ZalavaModule {

  static final String MODULE_ID = "zalava-module-time";

  private final List<ProviderFactory> providerFactories;

  public TimeSeaModule() {
    this.providerFactories = List.of(new TimeProviderFactory());
  }

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        MODULE_ID,
        version(),
        "Time",
        "Timezone-aware current-time and conversion tools backed by the JDK time API");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return providerFactories;
  }

  static String version() {
    Properties properties = new Properties();
    try (InputStream input = TimeSeaModule.class.getResourceAsStream("/module.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing module version metadata");
      }
      properties.load(input);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read module version metadata", exception);
    }
    return properties.getProperty("module.version");
  }
}
