/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2014-2020 Andres Almiray.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.shade.resources;

import com.google.common.io.LineReader;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Andres Almiray
 */
public class ServicesResourceTransformer implements ResourceTransformer {
    private String path = "META-INF/services";
    private Map<String, ServiceStream> serviceEntries = new LinkedHashMap<String, ServiceStream>();
    private List<Relocator> relocators;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean canTransformResource(String resource) {
        String path = this.path.endsWith("/") ? this.path : this.path + "/";
        return resource.startsWith(path);
    }

    @Override
    public void processResource(String resource, InputStream is, List<Relocator> relocators, long time) throws IOException {
        ServiceStream out = serviceEntries.get(resource);
        if (out == null) {
            out = new ServiceStream();
            serviceEntries.put(resource, out);
        }

        final ServiceStream fout = out;

        final String content = IOUtils.toString(is);
        StringReader reader = new StringReader(content);
        LineReader lineReader = new LineReader(reader);
        String line;
        while ((line = lineReader.readLine()) != null) {
            String relContent = line;
            for (Relocator relocator : relocators) {
                if (relocator.canRelocateClass(relContent)) {
                    relContent = relocator.applyToSourceContent(relContent);
                }
            }
            fout.append(relContent + "\n");
        }

        if (this.relocators == null) {
            this.relocators = relocators;
        }
    }

    @Override
    public boolean hasTransformedResource() {
        return serviceEntries.size() > 0;
    }

    @Override
    public void modifyOutputStream(JarOutputStream jos)
        throws IOException {
        for (Map.Entry<String, ServiceStream> entry : serviceEntries.entrySet()) {
            String key = entry.getKey();
            ServiceStream data = entry.getValue();

            if (relocators != null) {
                key = key.substring(path.length() + 1);
                for (Relocator relocator : relocators) {
                    if (relocator.canRelocateClass(key)) {
                        key = relocator.relocateClass(key);
                        break;
                    }
                }

                key = path + '/' + key;
            }

            jos.putNextEntry(new JarEntry(key));

            //read the content of service file for candidate classes for relocation
            PrintWriter writer = new PrintWriter(jos);
            InputStreamReader streamReader = new InputStreamReader(data.toInputStream());
            BufferedReader reader = new BufferedReader(streamReader);
            String className;

            while ((className = reader.readLine()) != null) {
                if (relocators != null) {
                    for (Relocator relocator : relocators) {
                        //if the class can be relocated then relocate it
                        if (relocator.canRelocateClass(className)) {
                            className = relocator.applyToSourceContent(className);
                            break;
                        }
                    }
                }

                writer.println(className);
                writer.flush();
            }

            reader.close();
            data.reset();
        }
    }

    private static class ServiceStream extends ByteArrayOutputStream {
        private ServiceStream() {
            super(1024);
        }

        private void append(String content)
            throws IOException {
            if (count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r') {
                write('\n');
            }

            byte[] contentBytes = content.getBytes("UTF-8");
            this.write(contentBytes);
        }

        private InputStream toInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }
}
