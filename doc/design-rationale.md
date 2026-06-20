# Design Rationale — cap-captcha-keycloak

This document captures the **why** behind `cap-captcha-keycloak`: the scope decision, the cap API contract the SPI implements against, the architecture and its derivation, and the resolved design choices. For operator-facing build/deploy/configure instructions, see [`../README.md`](../README.md).

## 1. Goal & scope

A Keycloak extension (Java 17, Maven jar) that integrates [trycap.dev cap captcha](https://trycap.dev/) into the **registration flow only**. The scope is deliberately the same as Keycloak's native reCAPTCHA (`RegistrationRecaptcha`) — a single `FormAction` registered against the registration flow. Keycloak ships no login-flow captcha, and neither does this extension.

Cap is a self-hostable proof-of-work + instrumentation CAPTCHA with a reCAPTCHA-compatible `siteverify` endpoint. The extension makes the cap instance URL configurable per execution so the operator points at their own cap Standalone instance rather than a third-party SaaS.

## 2. Reference contract (cap API)

The source of truth the SPI implements against. Extracted from the cap widget and `siteverify` docs.

- **Widget JS (CDN):** `https://cdn.jsdelivr.net/npm/cap-widget` (pin a version in production).
- **Widget JS (self-hosted, cap asset server with `ENABLE_ASSETS_SERVER=true`):** `https://<instance>/assets/widget.js`, plus `window.CAP_CUSTOM_WASM_URL = "https://<instance>/assets/cap_wasm_bg.wasm";` set via an inline script **before** the widget script loads.
- **Widget markup:** `<cap-widget data-cap-api-endpoint="https://<instance>/<site-key>/" required></cap-widget>` inside the `<form>`. Auto-injects `<input type="hidden" name="cap-token">` on solve.
- **Form field:** `cap-token` (hyphenated, top-level POST field).
- **Verify endpoint:** `POST https://<instance>/<site-key>/siteverify`.
- **Verify request:** header `Content-Type: application/json`; JSON body `{"secret":"<SECRET>","response":"<TOKEN>"}` (no `remoteip`).
- **Verify response:** `{"success": true}` ⇒ verified; anything else ⇒ rejected. HTTP/network/parse error ⇒ unreachable (treated as rejected — see §6).
- **Config:** `instanceUrl` (public base, no trailing slash), `siteKey` (public), `secretKey` (private; **not** the dashboard `ADMIN_KEY`).

## 3. Architecture

Three classes in package `com.schwebke.keycloak.cap.authenticator`:

| File | Role |
|---|---|
| `CapCaptchaConstants.java` | Shared constants (config keys, form field, message keys, FreeMarker attribute names, provider ID, timeout). No logic. |
| `CapVerifier.java` | Static `verify(...)` → `boolean`. Server-side `siteverify` POST. |
| `RegistrationCapCaptcha.java` | Registration `FormAction` + `FormActionFactory`. Reads config, sets FreeMarker attributes on `buildPage`, gates on `validate`. |

Registered via a single SPI descriptor: `META-INF/services/org.keycloak.authentication.FormActionFactory` listing `RegistrationCapCaptcha`. No `AuthenticatorFactory` descriptor — the extension registers no browser login authenticator.

`META-INF/jboss-deployment-structure.xml` is included for parity with the CaptchaFox template and forward-compatibility with any Keycloak-on-WildFly deployment; on the modern Quarkus distribution it is ignored.

### Derivation

The skeleton is adapted from [CaptchaFox's Keycloak extension](https://github.com/CaptchaFox/keycloak-captchafox) — specifically its registration `FormAction` — which itself derives from Keycloak's `AbstractRegistrationRecaptcha` / `RegistrationRecaptcha` (Red Hat). The verify call shape, the `buildPage`→`setAttribute`→`validate` flow, the `REQUIRED`/`DISABLED` requirement choices, and the fail-closed error path all follow that lineage. The cap-specific parts are the URL construction (`<instance>/<siteKey>/siteverify`), the JSON body shape (`{"secret","response"}` with no `remoteip`), and the theme-attribute names.

## 4. Resolved decisions

| # | Decision | Resolution |
|---|---|---|
| 1 | Flow scope | Registration only (one `FormAction`); no login authenticator, matching kc's native reCAPTCHA scope. |
| 2 | Theme strategy | Document a theme override; set FreeMarker attributes only. The extension jar stays theme-agnostic. A sample theme (asset-server variant) lives in the `src/cap-captcha-theme` Maven module and is built as a separate optional `cap-captcha-theme.jar` — not bundled into the extension jar. |
| 3 | Keycloak version | Pinned via `keycloak-parent` BOM in `pom.xml`; repo tags record the build tested against. |
| 4 | Config source | Per-execution admin config (`ProviderConfigProperty`). |
| 5 | Optional config fields | None. No `bypass-on-error`. Widget script URL lives in the theme, not config. |
| 6 | Widget script source | Script tag in the documented theme override (no `form.addScript`). |
| 7 | Error messages | Custom keys `capCaptchaFailed` / `capCaptchaNotConfigured`; operator-defined in the theme message bundle. |
| 8 | `remoteip` | Not sent — match cap docs (`{"secret","response"}` only). |
| 9 | Fail-closed semantics | Registration is always fail-closed, matching kc's `RegistrationRecaptcha`. No bypass option. |
| 10 | CSP | Documented as a deployment/theme concern; no SPI code. |
| 11 | Maven coords | `com.schwebke.keycloak` : parent `cap-captcha-keycloak-parent` : `1.0.0`, with modules `cap-captcha-keycloak` (SPI) and `cap-captcha-theme`; package `com.schwebke.keycloak.cap.authenticator`. |
| 12 | License | Apache 2.0 (preserve Red Hat / CaptchaFox attribution + our copyright). |
| 13 | CI/build | No CI file; containerized Docker build + `build.sh`. |
| 14 | Tests | None (matches CaptchaFox; integration tested manually against a Keycloak instance). |
| 15 | Provider ID | `registration-cap-action` (display name "Cap Captcha"). |

Low-stakes defaults: include `jboss-deployment-structure.xml` (harmless on Quarkus); verify timeout 10s (per-request `RequestConfig`); config keys `instance.url` / `site.key` / `secret`; FreeMarker attributes `capCaptchaRequired` / `capCaptchaInstanceUrl` / `capCaptchaSiteKey`; form field `cap-token`; `<finalName>` = artifactId; `CapVerifier.verify` returns `boolean` (parity with kc's `validate()` → `boolean`); no locale handling in the SPI (the cap widget auto-detects locale).

## 5. Why registration-only

Keycloak's native reCAPTCHA is **registration-only**. `RegistrationRecaptcha` (v2/v3) and `RegistrationRecaptchaEnterprise` are `FormAction` implementations registered in `services/...FormActionFactory` and rendered by the stock `register.ftl` themes. Keycloak ships **no** login-flow captcha authenticator — the stock `UsernamePasswordForm` has no captcha gate, and no `g-recaptcha` markup exists in `login.ftl` or `login-username.ftl`.

### Functional parity with kc `RegistrationRecaptcha`

| Behavior | kc `RegistrationRecaptcha` | `RegistrationCapCaptcha` |
|---|---|---|
| SPI type | `FormAction` + `FormActionFactory` | same |
| Requirement choices | `REQUIRED`, `DISABLED` | same |
| `buildPage` validates config | site key + secret | instance url + site key + secret |
| `buildPage` sets FreeMarker attrs | `recaptchaRequired`, `recaptchaSiteKey`, `recaptchaAction`, `recaptchaVisible` | `capCaptchaRequired`, `capCaptchaInstanceUrl`, `capCaptchaSiteKey` |
| `buildPage` injects script | `form.addScript(scriptUrl)` | no — theme loads the widget (see §7) |
| `validate` reads form field | `g-recaptcha-response` | `cap-token` |
| `validate` verifies server-side | POST to `siteverify` | POST to `<instance>/<siteKey>/siteverify` |
| `validate` return type | `boolean` | `boolean` |
| `validate` fail-closed on failure | yes (exception ⇒ `false` ⇒ `RECAPTCHA_FAILED`) | yes (rejected/unreachable/blank ⇒ `capCaptchaFailed`) |
| Error message | `Messages.RECAPTCHA_FAILED` (bundled in `Messages.java`) | `capCaptchaFailed` (operator-defined) |
| `remoteip` sent | yes (v2/v3) | no (cap docs) |
| Score/threshold | v3/Enterprise: `score.threshold` + action match | n/a (cap is boolean verified/rejected) |

## 6. Verify semantics

`CapVerifier.verify(session, instanceUrl, siteKey, secret, token)` constructs the verify URL by stripping any trailing `/` from `instanceUrl` and appending `/<siteKey>/siteverify`, then POSTs the JSON body `{"secret": secret, "response": token}` via Keycloak's `HttpClientProvider`.

- **Fail-closed on any error.** A rejected token (`{"success": false}`) and an unreachable instance (network/timeout/non-2xx/JSON parse error) both return `false`. The caller (`RegistrationCapCaptcha.validate`) treats all non-`true` results the same — no bypass path. A missing/blank token short-circuits before `verify` is called and also fails closed. This matches kc's `RegistrationRecaptcha`, where exceptions ⇒ `false` ⇒ `RECAPTCHA_FAILED`.
- **No `remoteip`** in the body (decision #8). Only `secret` + `response`, per cap docs.
- **Per-request timeout** via `RequestConfig` (10s connect + 10s socket), set on the `HttpPost` so the shared `HttpClientProvider` client config is not altered.
- **Exception logging** reuses Keycloak's built-in `ServicesLogger.LOGGER.recaptchaFailed(e)` (as CaptchaFox does) — no custom logger, no new log category.
- **No token/secret logging** at any level. The token and secret never appear in log output.

## 7. Theme strategy

The SPI sets three FreeMarker attributes on `LoginFormsProvider` in `buildPage`:

- `capCaptchaRequired` — set to `true` only when config is present and complete; the theme guards widget rendering on `<#if capCaptchaRequired??>`.
- `capCaptchaInstanceUrl` — the configured cap instance base URL.
- `capCaptchaSiteKey` — the configured site key.

It does **not** call `form.addScript(...)`. kc's `RegistrationRecaptcha` injects `api.js` that way, but cap's widget is a `<cap-widget>` web component — the markup and the script tag both belong in the theme's `register.ftl`, where the operator controls CDN vs. self-hosted asset-server loading and CSP nonces. This is a deployment-cost difference, not a functional one: the operator's `register.ftl` adds the `<cap-widget>` inside the registration `<form>` and loads the widget script in `<head>` or before `</body>`. See the README "Theme override" and "Content-Security-Policy" sections for the snippets.

No theme is bundled inside the extension jar. kc's stock `register.ftl` renders `g-recaptcha` out of the box; bundling a cap theme into the SPI jar would conflict with operator theme customization and CSP policy, so the extension stays theme-agnostic and the README documents the required override. A **sample** theme (asset-server variant) lives in its own Maven module, `src/cap-captcha-theme`, and is built into a separate optional artifact, `cap-captcha-theme.jar`. The module's `src/main/resources` holds the theme (`theme/cap-captcha/login/...`) and the `META-INF/keycloak-themes.json` manifest, packaged by the standard `maven-jar-plugin` — no assembly plugin. It is `keycloak.v2` plus exactly the cap-relevant `register.ftl` block and the two message-bundle keys — nothing else. Deploying it is optional; operators can instead fold the cap block into their own theme.

## 8. Config & error messages

### Config

Three per-execution admin config properties (`ProviderConfigProperty`), no optional fields:

| name | label | type |
|---|---|---|
| `instance.url` | Cap Instance URL | `STRING_TYPE` |
| `site.key` | Cap Site Key | `STRING_TYPE` |
| `secret` | Cap Secret | `PASSWORD` |

No `mode` (cap has no inline/popup distinction). No `bypass-on-error` (registration is always fail-closed). The widget script URL is not a config field — it lives in the theme (decision #6), because the CDN-vs-self-hosted choice and CSP nonce wiring are deployment concerns.

### Error messages

kc bundles `recaptchaFailed` / `recaptchaNotConfigured` in `Messages.java` + `messages_*.properties`. This extension uses custom keys `capCaptchaFailed` / `capCaptchaNotConfigured` that the **operator** must add to their theme's `messages.properties` (and locale variants). Rationale: bundling messages in the SPI would require a theme resource bundle shipped in the jar, which would not be overridable by the operator's theme and would not respect the operator's locale coverage. Operator-defined keys keep the extension theme-agnostic and let each deployment localize as needed. The trade-off — Keycloak renders the raw key string on error if the operator forgets the bundle entry — is documented in the README as a required setup step.

## 9. License & attribution

Apache License 2.0. The extension adapts CaptchaFox's Apache-2.0 code, which itself derives from Keycloak's reCAPTCHA authenticator (Red Hat). Per Apache 2.0, the Red Hat copyright line is preserved verbatim in each Java source header, alongside `Copyright 2026 Kai Schwebke`. The `LICENSE` file is the standard Apache 2.0 boilerplate. The README Credits section acknowledges Keycloak and CaptchaFox by name.

## 10. Build

No CI workflow files (decision #13). The project is a multi-module Maven build: the root `pom.xml` (packaging `pom`, artifact `cap-captcha-keycloak-parent`) aggregates `src/cap-captcha-spi` and `src/cap-captcha-theme`. `mvn clean package` at the root builds both modules. The SPI module produces `cap-captcha-keycloak.jar`; the theme module produces `cap-captcha-theme.jar`.

The build is containerized: a `Dockerfile` based on `maven:3.9-eclipse-temurin-17` copies all three poms for a deps-caching layer (`dependency:go-offline`), then copies `src/` and runs `mvn clean package`. Maven emits the jars into the per-module `target/` dirs inside the container (`/build/src/cap-captcha-spi/target/`, `/build/src/cap-captcha-theme/target/`); the Dockerfile then `cp`s them to fixed image-root paths (`/cap-captcha-keycloak.jar`, `/cap-captcha-theme.jar`) as a stable extraction contract, separate from the build output location. `build.sh` runs the Docker build and `docker cp`s both jars from those image-root paths out to repo-root `target/`. A native `mvn clean compile package` with Java 17 + Maven produces the same two jars and leaves them in the module `target/` dirs (`src/cap-captcha-spi/target/`, `src/cap-captcha-theme/target/`) — the image-root `cp` step only exists in the containerized path.

All Keycloak and Apache HttpComponents dependencies are `provided`-scope — supplied by the Keycloak server runtime at deploy time. The SPI jar (`cap-captcha-keycloak.jar`) contains only the three extension classes and the `META-INF/` SPI resources. The theme jar (`cap-captcha-theme.jar`) contains only `theme/cap-captcha/...` and `META-INF/keycloak-themes.json` — no SPI classes, no Java. No shade/assembly/source plugin, no tests, no CheckStyle/SpotBugs — the SPI build footprint matches CaptchaFox's.

The SPI classes used (`FormAction`, `LoginFormsProvider`, `HttpClientProvider`, `JsonSerialization`, `Errors`, `ServicesLogger`) have been stable across recent Keycloak releases, so pointing at a different Keycloak build only requires updating `<version.keycloak>` in the root `pom.xml`.
