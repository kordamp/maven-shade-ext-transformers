/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.shade.resources;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Resources transformer that merges Properties files.
 *
 * <p>The default merge strategy discards duplicate values coming from additional
 * resources. This behavior can be changed by setting a value for the <tt>mergeStrategy</tt>
 * property, such as 'first' (default), 'latest' or 'append'. If the merge strategy is
 * 'latest' then the last value of a matching property entry will be used. If the
 * merge strategy is 'append' then the property values will be combined, using a
 * merge separator (default value is ','). The merge separator can be changed by
 * setting a value for the <tt>mergeSeparator</tt> property.</p>
 *
 * Say there are two properties files A and B with the
 * following entries:
 *
 * <strong>A</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2</li>
 * </ul>
 *
 * <strong>B</strong>
 * <ul>
 *   <li>key2 = balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = first</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = latest</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = append</tt> and <tt>mergeSparator = ;</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2;balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * <p>There are two additional properties that can be set: <tt>paths</tt> and <tt>mappings</tt>.
 * The first contains a list of strings or regexes that will be used to determine if
 * a path should be transformed or not. The merge strategy and merge separator are
 * taken from the global settings.</p>
 *
 * <p>The <tt>mappings</tt> property allows you to define merge strategy and separator per
 * path</p>. If either <tt>paths</tt> or <tt>mappings</tt> is defined then no other path
 * entries will be merged. <tt>mappings</tt> has precedence over <tt>paths</tt> if both
 * are defined.</p>
 *
 * @author Andres Almiray
 */
public class PropertiesFileTransformer implements ResourceTransformer {
    private static final String PROPERTIES_SUFFIX = ".properties";
    private static final String KEY_MERGE_STRATEGY = "mergeStrategy";
    private static final String KEY_MERGE_SEPARATOR = "mergeSeparator";

    private Map<String, Properties> propertiesEntries = new LinkedHashMap<>();

    // Transformer properties
    private List<String> paths = new ArrayList<>();
    private Map<String, Map<String, String>> mappings = new LinkedHashMap<>();
    private String mergeStrategy = "first"; // latest, append
    private String mergeSeparator = ",";

    @Override
    public boolean canTransformResource(String resource) {
        if (mappings.containsKey(resource)) {
            return true;
        }
        for (String key : mappings.keySet()) {
            if (resource.matches(key)) {
                return true;
            }
        }

        if (paths.contains(resource)) {
            return true;
        }
        for (String path : paths) {
            if (resource.matches(path)) {
                return true;
            }
        }

        return mappings.isEmpty() && paths.isEmpty() && resource.endsWith(PROPERTIES_SUFFIX);
    }

    @Override
    public void processResource(String resource, InputStream is, List<Relocator> relocators) throws IOException {
        Properties props = propertiesEntries.get(resource);
        if (props == null) {
            props = new Properties();
            props.load(is);
            propertiesEntries.put(resource, props);
        } else {
            Properties incoming = new Properties();
            incoming.load(is);
            for (String key : incoming.stringPropertyNames()) {
                String value = incoming.getProperty(key);
                if (props.containsKey(key)) {
                    String ms = mergeStrategyFor(resource).toLowerCase();
                    switch (mergeStrategyFor(resource).toLowerCase()) {
                        case "latest":
                            props.put(key, value);
                            break;
                        case "append":
                            props.put(key, props.getProperty(key) + mergeSeparatorFor(resource) + value);
                            break;
                        case "first":
                        default:
                            // continue
                            break;
                    }
                } else {
                    props.put(key, value);
                }
            }
        }
    }

    @Override
    public boolean hasTransformedResource() {
        return propertiesEntries.size() > 0;
    }

    @Override
    public void modifyOutputStream(JarOutputStream os) throws IOException {
        for (Map.Entry<String, Properties> e : propertiesEntries.entrySet()) {
            os.putNextEntry(new JarEntry(e.getKey()));
            IOUtil.copy(toInputStream(e.getValue()), os);
            os.closeEntry();
        }
    }

    // == Private

    private static InputStream toInputStream(Properties props) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.store(baos, "");
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private String mergeStrategyFor(String path) {
        if (mappings.containsKey(path)) {
            return getMappingValue(path, KEY_MERGE_STRATEGY, mergeStrategy);
        }
        for (String key : mappings.keySet()) {
            if (path.matches(key)) {
                return getMappingValue(key, KEY_MERGE_STRATEGY, mergeStrategy);
            }
        }

        return mergeStrategy;
    }

    private String mergeSeparatorFor(String path) {
        if (mappings.containsKey(path)) {
            return getMappingValue(path, KEY_MERGE_SEPARATOR, mergeSeparator);
        }
        for (String key : mappings.keySet()) {
            if (path.matches(key)) {
                return getMappingValue(key, KEY_MERGE_SEPARATOR, mergeSeparator);
            }
        }

        return mergeSeparator;
    }

    private String getMappingValue(String key, String setting, String defaultValue) {
        Map<String, String> config = mappings.get(key);
        String value = config.get(setting);
        return value != null && value.trim().length() > 0 ? value : defaultValue;
    }

    // == Properties

    // made public for testing
    public Map<String, Properties> getPropertiesEntries() {
        return propertiesEntries;
    }

    public void setPropertiesEntries(Map<String, Properties> propertiesEntries) {
        this.propertiesEntries = propertiesEntries;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public Map<String, Map<String, String>> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, Map<String, String>> mappings) {
        this.mappings = mappings;
    }

    public String getMergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    public String getMergeSeparator() {
        return mergeSeparator;
    }

    public void setMergeSeparator(String mergeSeparator) {
        this.mergeSeparator = mergeSeparator;
    }
}
