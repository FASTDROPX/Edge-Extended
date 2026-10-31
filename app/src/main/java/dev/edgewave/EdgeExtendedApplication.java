package dev.edgewave;

public final class EdgeExtendedApplication extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    AppAppearance.apply(this);
  }
}
