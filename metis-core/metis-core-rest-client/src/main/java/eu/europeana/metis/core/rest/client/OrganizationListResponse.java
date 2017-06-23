package eu.europeana.metis.core.rest.client;

import eu.europeana.metis.core.organization.Organization;

import java.util.List;

/**
 * Created by gmamakis on 10-2-17.
 */
public class OrganizationListResponse {

    private List<Organization> results;
    private int listSize;
    private String nextPage;

    public String getNextPage() {
        return nextPage;
    }

    public void setNextPage(String nextPage) {
        this.nextPage = nextPage;
    }

    public List<Organization> getResults() {
        return results;
    }

    public void setResults(List<Organization> results) {
        this.results = results;
    }

    public int getListSize() { return listSize; }

    public void setListSize(int listSize) {
        this.listSize = listSize;
    }
}
