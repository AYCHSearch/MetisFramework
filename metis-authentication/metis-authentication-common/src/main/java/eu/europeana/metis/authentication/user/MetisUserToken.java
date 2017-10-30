package eu.europeana.metis.authentication.user;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-10-30
 */
@Entity
@Table(name = "metis_user_tokens")
public class MetisUserToken {
  @Id
  @Column(name = "email")
  private String email;
  @Column(name = "access_token")
  private String accessToken;
  @Column(name = "timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date timestamp;

  public MetisUserToken() {
  }

  public MetisUserToken(String email, String accessToken, Date timestamp) {
    this.email = email;
    this.accessToken = accessToken;
    this.timestamp = timestamp;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }
}
