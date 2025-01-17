/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.feature.extension.unpack.impl.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.util.TimeValue;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.extension.unpack.Unpack;
import org.apache.sling.feature.io.json.FeatureJSONWriter;

public class Converter {
    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            ArtifactId id = ArtifactId.fromMvnId(args[0]);

            String name = args[1];
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalStateException("Invalid extension name: " + name);
            }

            File featureFile = new File(args[2]);
            File featureDir = featureFile.getParentFile();
            if (!featureDir.isDirectory() && !featureDir.mkdirs()) {
                throw new IOException("Unable to create target dir: " + featureDir);
            }
            File base = new File(args[3]);
            if (!base.isDirectory() && !base.mkdirs()) {
                throw new IOException("Unable to create base dir: " + base);
            }

            String key = null;
            String value = null;

            List<String> urls = new ArrayList<>();
            for (int i = 4; i < args.length;i++) {
                if (args[i].startsWith("key=")) {
                    key = args[i].substring("key=".length());
                } else if (args[i].startsWith("value=")) {
                    value = args[i].substring("value=".length());
                }
                else{
                    urls.addAll(Arrays.asList(args[i].split(" ")).stream().map(String::trim).filter(((Predicate<String>)String::isEmpty).negate()).collect(Collectors.toList()));
                }
            }

            Predicate<InputStream> check;
            if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                final String keyF = key;
                final String valueF = value;
                check = (stream) -> Unpack.handles(keyF, valueF, stream);
            } else {
                check = inputStream -> true;
            }

            List<String> unhandled = convert(id, name, featureFile, base, check, urls);

            System.out.println(String.join(" ", unhandled));
        }
    }

    public static List<String> convert(ArtifactId featureId, String extensionName, File featureFile, File repository, Predicate<InputStream> filter, List<String> urls) throws Exception {
        Feature feature = new Feature(featureId);

        List<String> unhandled = new ArrayList<>();

        Extension extension = new Extension(ExtensionType.ARTIFACTS, extensionName, ExtensionState.OPTIONAL);

        for (String urlString : urls) {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            HttpRequestRetryStrategy rs = new DefaultHttpRequestRetryStrategy(10, TimeValue.ofSeconds(1)) {
                @Override
                protected boolean handleAsIdempotent(HttpRequest request) {
                    md.reset(); // Reset message digest on retry
                    return super.handleAsIdempotent(request);
                }
            };

            File tmp = File.createTempFile("unpack", ".zip");

            try (CloseableHttpClient httpClient = HttpClients.custom().setRetryStrategy(rs).build()) {
                HttpGet httpGet = new HttpGet(urlString);
                try (CloseableHttpResponse res = httpClient.execute(httpGet)) {
                    HttpEntity entity = res.getEntity();
                    try (DigestInputStream inputStream = new DigestInputStream(entity.getContent(), md)) {
                        Files.copy(inputStream, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        String digest = bytesToHex(inputStream.getMessageDigest().digest());

                        if (filter.test(new FileInputStream(tmp))){
                            Artifact artifact = new Artifact(new ArtifactId(featureId.getGroupId(), featureId.getArtifactId(), featureId.getVersion(), (featureId.getClassifier() != null ? featureId.getClassifier() + "-" : "" ) + digest, "zip"));
                            extension.getArtifacts().add(artifact);
                            File target = new File(repository, artifact.getId().toMvnPath());
                            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
                                throw new IOException("Unable to create parent dir: " + target.getParentFile());
                            }
                            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            unhandled.add(urlString);
                        }
                    }
                }
            } finally {
                tmp.delete();
            }
        }

        feature.getExtensions().add(extension);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(featureFile), StandardCharsets.UTF_8)) {
            FeatureJSONWriter.write(writer, feature);
        }

        return unhandled;
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes();

    static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
