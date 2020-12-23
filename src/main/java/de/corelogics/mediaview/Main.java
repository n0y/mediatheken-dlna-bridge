/*
 * MIT License
 *
 * Copyright (c) 2020 corelogics.de
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

package de.corelogics.mediaview;

import com.google.inject.Guice;
import de.corelogics.mediaview.config.MainConfiguration;
import de.corelogics.mediaview.service.dlna.DlnaServiceModule;
import de.corelogics.mediaview.service.importer.ImporterService;
import de.corelogics.mediaview.service.proxy.ForwardingProxyModule;
import org.fourthline.cling.model.ValidationException;

public class Main {
    public static void main(String[] args) throws InterruptedException, ValidationException {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

        var mainConfiguration = new MainConfiguration();
        var injector =
                Guice.createInjector(
                        new DlnaServiceModule(),
                        new ForwardingProxyModule(mainConfiguration));
        injector.getInstance(ImporterService.class).scheduleImport();
        Thread.currentThread().join();
    }
}
