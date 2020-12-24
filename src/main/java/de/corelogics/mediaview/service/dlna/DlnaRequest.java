package de.corelogics.mediaview.service.dlna;

import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.SortCriterion;

import java.util.List;

public class DlnaRequest {
    private final String objectId;

    private final BrowseFlag browseFlag;

    private final String filer;

    private final long firstResult;

    private final long maxResults;

    private final List<SortCriterion> orderBy;

    public DlnaRequest(String objectId, BrowseFlag browseFlag, String filer, long firstResult, long maxResults,
                       List<SortCriterion> orderBy) {
        this.objectId = objectId;
        this.browseFlag = browseFlag;
        this.filer = filer;
        this.firstResult = firstResult;
        this.maxResults = maxResults;
        this.orderBy = orderBy;
    }

    public String getObjectId() {
        return objectId;
    }

    public BrowseFlag getBrowseFlag() {
        return browseFlag;
    }

    public String getFiler() {
        return filer;
    }

    public long getFirstResult() {
        return firstResult;
    }

    public long getMaxResults() {
        return maxResults;
    }

    public List<SortCriterion> getOrderBy() {
        return orderBy;
    }
}
