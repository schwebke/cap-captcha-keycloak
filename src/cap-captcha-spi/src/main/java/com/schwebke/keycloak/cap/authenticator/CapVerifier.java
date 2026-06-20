/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 * Copyright 2026 Kai Schwebke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.schwebke.keycloak.cap.authenticator;

import java.io.InputStream;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.ServicesLogger;
import org.keycloak.util.JsonSerialization;

public final class CapVerifier {
    private CapVerifier() {}

    public static boolean verify(
            KeycloakSession session,
            String instanceUrl,
            String siteKey,
            String secret,
            String token) {
        String base = instanceUrl;
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String verifyUrl = base + "/" + siteKey + "/siteverify";

        try {
            String json = JsonSerialization.writeValueAsString(Map.of("secret", secret, "response", token));

            CloseableHttpClient httpClient = session.getProvider(HttpClientProvider.class).getHttpClient();
            HttpPost post = new HttpPost(verifyUrl);
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            post.setConfig(RequestConfig.custom()
                    .setConnectTimeout(CapCaptchaConstants.VERIFY_TIMEOUT_MS)
                    .setSocketTimeout(CapCaptchaConstants.VERIFY_TIMEOUT_MS)
                    .build());

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                InputStream content = response.getEntity().getContent();
                try {
                    @SuppressWarnings("rawtypes")
                    Map responseJson = JsonSerialization.readValue(content, Map.class);
                    Object val = responseJson.get("success");
                    return Boolean.TRUE.equals(val);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            }
        } catch (Exception e) {
            ServicesLogger.LOGGER.recaptchaFailed(e);
            return false;
        }
    }
}
