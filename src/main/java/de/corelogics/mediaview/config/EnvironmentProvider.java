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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.netflix.governator.configuration.AbstractObjectConfigurationProvider;
import com.netflix.governator.configuration.ConfigurationKey;
import com.netflix.governator.configuration.DateWithDefaultProperty;
import com.netflix.governator.configuration.Property;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

class EnvironmentProvider extends AbstractObjectConfigurationProvider {
    private final Map<String, String> variables;

    public EnvironmentProvider() {
        this(Maps.<String, String>newHashMap());
    }

    public EnvironmentProvider(Map<String, String> variables) {
        this(variables, null);
    }

    public EnvironmentProvider(Map<String, String> variables, ObjectMapper objectMapper) {
        super(objectMapper);
        this.variables = Maps.newHashMap(variables);
    }

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    @Override
    public boolean has(ConfigurationKey key) {
        return System.getenv().containsKey(key.getKey(variables));
    }

    @Override
    public Property<Boolean> getBooleanProperty(final ConfigurationKey key, final Boolean defaultValue) {
        return getEnv(key, Boolean::parseBoolean, defaultValue);
    }

    @Override
    public Property<Integer> getIntegerProperty(final ConfigurationKey key, final Integer defaultValue) {
        return getEnv(key, Integer::parseInt, defaultValue);
    }

    @Override
    public Property<Long> getLongProperty(final ConfigurationKey key, final Long defaultValue) {
        return getEnv(key, Long::parseLong, defaultValue);
    }

    @Override
    public Property<Double> getDoubleProperty(final ConfigurationKey key, final Double defaultValue) {
        return getEnv(key, Double::parseDouble, defaultValue);
    }

    @Override
    public Property<String> getStringProperty(final ConfigurationKey key, final String defaultValue) {
        return getEnv(key, Function.identity(), defaultValue);
    }

    @Override
    public Property<Date> getDateProperty(ConfigurationKey key, Date defaultValue) {
        return new DateWithDefaultProperty(getStringProperty(key, null), defaultValue);
    }

    private <T> Property<T> getEnv(ConfigurationKey key, Function<String, T> convert, T defaultValue) {
        return new Property<T>() {
            @Override
            public T get() {
                return ofNullable(System.getenv().get(key.getKey(variables))).map(convert).orElse(defaultValue);
            }
        };
    }
}
