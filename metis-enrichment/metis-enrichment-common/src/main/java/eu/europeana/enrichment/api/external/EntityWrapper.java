package eu.europeana.enrichment.api.external;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import eu.europeana.enrichment.utils.EntityClass;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Wrapper Entity for the output generated by Enrichment
 * 
 * @author Yorgos.Mamakis@ europeana.eu
 *
 */
@XmlRootElement
@JsonSerialize
public class EntityWrapper {

  @XmlElement
  private EntityClass entityClass;
  @XmlElement
  private String originalField;
  @XmlElement
  private String contextualEntity;
  @XmlElement
  private String url;
  @XmlElement
  private String originalValue;

  public EntityWrapper() {
    // Required for XML binding.
  }

  public String getOriginalField() {
    return originalField;
  }

  public void setOriginalField(String originalField) {
    this.originalField = originalField;
  }

  public String getContextualEntity() {
    return contextualEntity;
  }

  public void setContextualEntity(String contextualEntity) {
    this.contextualEntity = contextualEntity;
  }

  public EntityClass getEntityClass() {
    return entityClass;
  }
  
  public void setEntityClass(EntityClass entityClass) {
    this.entityClass = entityClass;
  }
  
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getOriginalValue() {
    return originalValue;
  }

  public void setOriginalValue(String originalValue) {
    this.originalValue = originalValue;
  }
}
