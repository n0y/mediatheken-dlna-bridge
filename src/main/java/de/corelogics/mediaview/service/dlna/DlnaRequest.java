/*
 * MIT License
 *
 * Copyright (c) 2020-2021 Mediatheken DLNA Bridge Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.corelogics.mediaview.service.dlna;

import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.SortCriterion;

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
