package eu.europeana.itemization;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by ymamakis on 2/25/16.
 */
@XmlRootElement
public class IdentifierError {
    @XmlElement
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
