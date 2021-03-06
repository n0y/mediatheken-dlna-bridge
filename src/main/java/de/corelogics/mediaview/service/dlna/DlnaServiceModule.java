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

import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.repository.clip.ClipRepository;
import de.corelogics.mediaview.service.ClipContentUrlGenerator;
import de.corelogics.mediaview.service.dlna.content.*;
import org.fourthline.cling.model.ValidationException;

import java.util.Set;

public class DlnaServiceModule {
    private final MainConfiguration mainConfiguration;
    private final ClipContentUrlGenerator clipContentUrlGenerator;
    private final ClipRepository clipRepository;
    private final DlnaServer dlnaServer;

    public DlnaServiceModule(MainConfiguration mainConfiguration, ClipContentUrlGenerator clipContentUrlGenerator, ClipRepository clipRepository) {
        try {
            this.mainConfiguration = mainConfiguration;
            this.clipContentUrlGenerator = clipContentUrlGenerator;
            this.clipRepository = clipRepository;
            this.dlnaServer = new DlnaServer(mainConfiguration, buildRequestHandlers());
        } catch (ValidationException e) {
            throw new RuntimeException("Initialization failed", e);
        }
    }

    private Set<DlnaRequestHandler> buildRequestHandlers() {
        var clipContent = new ClipContent(this.clipContentUrlGenerator);
        var showContent = new ShowContent(clipContent, this.clipRepository);
        var sendungAzContent = new SendungAzContent(this.clipRepository, showContent);
        var missedShowsContent = new MissedShowsContent(clipContent, this.clipRepository);
        var rootContent = new RootContent(mainConfiguration, sendungAzContent, showContent, missedShowsContent);
        return Set.of(clipContent, missedShowsContent, sendungAzContent, rootContent, showContent);
    }

    public DlnaServer getDlnaServer() {
        return dlnaServer;
    }
}
