/*
 * MIT License
 *
 * Copyright (c) 2020 Mediatheken DLNA Bridge Authors.
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

package de.corelogics.mediaview.config;

import com.netflix.governator.configuration.CompositeConfigurationProvider;
import com.netflix.governator.configuration.ConfigurationProvider;
import com.netflix.governator.configuration.PropertiesConfigurationProvider;
import com.netflix.governator.configuration.SystemConfigurationProvider;
import de.corelogics.mediaview.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class ConfigProviderFactory {
    private final Logger logger = LogManager.getLogger();

    public ConfigurationProvider createConfigurationProvider() {
        logger.debug("Initializing configuration provider");

        var provider = new CompositeConfigurationProvider();
        loadFsProperties().map(PropertiesConfigurationProvider::new).ifPresent(provider::add);
        loadClasspathProperties().map(PropertiesConfigurationProvider::new).ifPresent(provider::add);
        provider.add(new EnvironmentProvider());
        provider.add(new SystemConfigurationProvider());

        return provider;
    }

    private Optional<Properties> loadFsProperties() {
        try {
            var file = new File(new File("config"), "application.properties");
            if (file.isFile()) {
                logger.debug("Loading file system application.properties from {}", file::getAbsolutePath);
                var v = Optional.of(toProperties(new FileInputStream(file)));
                logger.info("successfully loaded system application.properties from {}", file::getAbsolutePath);
                return v;
            }
            logger.info("No file system application.properties file present in {}", file::getAbsolutePath);
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return empty();
    }

    private Optional<Properties> loadClasspathProperties() {
        return loadClasspathResource("/application.properties").map(this::toProperties);
    }

    private Properties toProperties(InputStream inputStream) {
        try (var i = inputStream) {
            logger.debug("loading classpath application.properties");
            var p = new Properties();
            p.load(inputStream);
            logger.info("successfully loaded classpath application.properties");
            return p;
        } catch (final IOException e) {
            throw new RuntimeException();
        }
    }

    private Optional<InputStream> loadClasspathResource(String resource) {
        return ofNullable(Main.class.getResourceAsStream(resource));
    }
}
