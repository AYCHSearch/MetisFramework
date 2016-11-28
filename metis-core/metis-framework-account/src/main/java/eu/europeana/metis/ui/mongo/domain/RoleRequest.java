package eu.europeana.metis.ui.mongo.domain;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Indexed;

import java.util.Date;

/**
 * A request for an assignment to an organization
 * Created by ymamakis on 11/24/16.
 */
@Entity
public class RoleRequest {
    @Id
    private ObjectId id;

    /**
     * The id of the organization
     */
    @Indexed
    private String organizationId;

    /**
     * The id of a user
     */
    @Indexed
    private String userId;

    /**
     * The role
     */
    private String role;

    /**
     * The request date
     */
    private Date requestDate;

    /**
     * The request status
     */
    @Indexed
    private String requestStatus;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }
}