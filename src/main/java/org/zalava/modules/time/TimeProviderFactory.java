package org.zalava.modules.time;

import java.time.Clock;
import java.util.List;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

public final class TimeProviderFactory implements ProviderFactory {

  static final String FACTORY_ID = "jdk-time";
  static final String PROVIDER_TYPE = "time";

  private final Clock clock;

  public TimeProviderFactory() {
    this(Clock.systemUTC());
  }

  TimeProviderFactory(Clock clock) {
    this.clock = clock;
  }

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        FACTORY_ID,
        TimeSeaModule.MODULE_ID,
        PROVIDER_TYPE,
        "JDK Time",
        "Creates a read-only time provider backed by java.time");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    return List.of(new TimeProvider(clock));
  }
}
