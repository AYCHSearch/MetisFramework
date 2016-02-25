package eu.europeana.metis.framework.service;

import eu.europeana.metis.framework.dao.DatasetDao;
import eu.europeana.metis.framework.dao.OrganizationDao;
import eu.europeana.metis.framework.dataset.Dataset;
import eu.europeana.metis.framework.exceptions.NoDatasetFoundException;
import eu.europeana.metis.framework.organization.Organization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for storing datasets
 * Created by ymamakis on 2/17/16.
 */
@Component
public class DatasetService {

    @Autowired
    private DatasetDao dsDao;

    @Autowired
    private OrganizationDao orgDao;

    /**
     * Create a dataset for an organization
     * @param org The organization to assign the dataset to
     * @param ds The dataset to persist
     */
    public void createDataset(Organization org, Dataset ds){
        dsDao.createDatasetForOrganization(org,ds);
        orgDao.update(org);
    }

    /**
     * Update a dataset
     * @param ds The dataset to update
     */
    public void updateDataset(Dataset ds){
        dsDao.update(ds);
    }

    /**
     * Delete a dataset
     * @param org The organization the dataset is assigned to
     * @param ds The dataset to delete
     */
    public void deleteDataset(Organization org, Dataset ds){
        dsDao.delete(ds);
        org.getDatasets().remove(ds);
        orgDao.update(org);
    }

    /**
     * Get Dataset by name
     * @param name The name of the dataset
     * @return The Dataset
     */
    public Dataset getByName(String name) throws NoDatasetFoundException{
        Dataset dataset = dsDao.getByName(name);
        if(dataset==null){
            throw new NoDatasetFoundException(name);
        }
        return dataset;
    }

}
