/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */

package eu.europeana.metis.core.dataset;

import eu.europeana.metis.core.common.HarvestType;

/**
 * OAIPMH specific technical metadata
 * Created by ymamakis on 2/17/16.
 */
public class OaipmhHarvestingMetadata implements HarvestingMetadata {

  private HarvestType harvestType = HarvestType.OAIPMH_HARVEST;
  private String url;
  private String metadataFormat;
  private String setSpec;

  @Override
  public HarvestType getHarvestType() {
    return harvestType;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMetadataFormat() {
    return metadataFormat;
  }

  public void setMetadataFormat(String metadataFormat) {
    this.metadataFormat = metadataFormat;
  }

  public String getSetSpec() {
    return setSpec;
  }

  public void setSetSpec(String setSpec) {
    this.setSpec = setSpec;
  }
}
