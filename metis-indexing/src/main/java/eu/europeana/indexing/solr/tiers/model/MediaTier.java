package eu.europeana.indexing.solr.tiers.model;

/**
 * This enum contains the different values of the media tier.
 */
public enum MediaTier implements Tier {

  T0(0), T1(1), T2(2), T3(3), T4(4);

  private final int level;

  MediaTier(int level) {
    this.level = level;
  }

  @Override
  public String getLabel() {
    return Integer.toString(level);
  }

  @Override
  public int getLevel() {
    return level;
  }
}