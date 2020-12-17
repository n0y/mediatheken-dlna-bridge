package de.corelogics.mediaview.service.dlna;

import com.google.common.collect.ImmutableList;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.SortCriterion;

public class DlnaRequest {
	private final String objectId;

	private final BrowseFlag browseFlag;

	private final String filer;

	private final long firstResult;

	private final long maxResults;

	private final ImmutableList<SortCriterion> orderBy;

	public DlnaRequest(String objectId, BrowseFlag browseFlag, String filer, long firstResult, long maxResults,
			ImmutableList<SortCriterion> orderBy) {
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

	public ImmutableList<SortCriterion> getOrderBy() {
		return orderBy;
	}
}
