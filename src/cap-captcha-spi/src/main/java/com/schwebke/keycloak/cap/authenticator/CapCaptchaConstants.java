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

public final class CapCaptchaConstants {
    private CapCaptchaConstants() {}

    /** Form field the cap widget auto-injects (default data-cap-hidden-field-name). */
    public static final String CAPTCHA_RESPONSE_KEY = "cap-token";

    /** Config property keys (AuthenticatorConfigModel.getConfig() map keys). */
    public static final String INSTANCE_URL = "instance.url";
    public static final String SITE_KEY    = "site.key";
    public static final String SITE_SECRET = "secret";

    /** Theme message-bundle keys the operator must define. */
    public static final String MSG_CAPTCHA_FAILED     = "capCaptchaFailed";
    public static final String MSG_NOT_CONFIGURED     = "capCaptchaNotConfigured";

    /** FreeMarker template attributes set on LoginFormsProvider. */
    public static final String ATTR_REQUIRED      = "capCaptchaRequired";
    public static final String ATTR_INSTANCE_URL  = "capCaptchaInstanceUrl";
    public static final String ATTR_SITE_KEY      = "capCaptchaSiteKey";

    /** Provider ID. */
    public static final String REGISTRATION_PROVIDER_ID = "registration-cap-action";

    /** Verify HTTP timeout (ms). Per-request RequestConfig; does not alter the shared client. */
    public static final int VERIFY_TIMEOUT_MS = 10000;
}
