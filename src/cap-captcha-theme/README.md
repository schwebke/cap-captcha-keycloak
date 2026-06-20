# Sample theme — `cap-captcha`

A minimal Keycloak login theme that renders the cap widget on the registration page using the **self-hosted asset server** variant (cap with `ENABLE_ASSETS_SERVER=true`). It is `keycloak.v2` plus exactly the cap-relevant FreeMarker changes — no other customizations.

This is a reference sample. It is built into a deployable theme jar (`cap-captcha-theme.jar`) as its own Maven module, but it is **not** bundled inside the extension jar — the extension itself stays theme-agnostic. Use it as-is, or fold its `register.ftl` / `messages_*.properties` into your own theme.

The module is a plain jar module — `src/main/resources` holds the theme and the Keycloak manifest, packaged as-is by the standard `maven-jar-plugin` (no assembly descriptor):

```
src/main/resources/
  theme/cap-captcha/login/
    register.ftl                  # stock keycloak.v2 register.ftl + the cap widget block (asset-server variant)
    theme.properties              # parent=keycloak.v2 + import=common/keycloak (nothing else)
    messages/
      messages_en.properties      # capCaptchaFailed / capCaptchaNotConfigured
      messages_de.properties      # German translations of the above
  META-INF/
    keycloak-themes.json          # Keycloak theme manifest (registers the cap-captcha login theme)
```

The only deviation from stock `keycloak.v2/login/register.ftl` is the inserted block inside the registration `<form>`:

```html
<#if capCaptchaRequired??>
    <div class="form-group">
        <div class="${properties.kcInputWrapperClass!}">
            <cap-widget
                data-cap-api-endpoint="${capCaptchaInstanceUrl}/${capCaptchaSiteKey}/"
                required></cap-widget>
        </div>
    </div>
    <script>window.CAP_CUSTOM_WASM_URL = "${capCaptchaInstanceUrl}/assets/cap_wasm_bg.wasm";</script>
    <script src="${capCaptchaInstanceUrl}/assets/widget.js"></script>
</#if>
```

The stock reCAPTCHA block is left in place — it is harmless and only renders when reCAPTCHA is also enabled.

## Deploy as a theme jar (recommended)

1. Build both jars (see the project root [`README.md`](../../README.md)):

   ```bash
   ./build.sh        # Docker — copies both jars to repo-root target/
   # or
   mvn clean compile package   # native — jars in src/cap-captcha-spi/target/ and src/cap-captcha-theme/target/
   ```

2. Copy both jars into Keycloak's `providers/` directory:

   ```bash
   cp target/cap-captcha-keycloak.jar target/cap-captcha-theme.jar /path/to/keycloak/providers/
   ```

3. Restart Keycloak (or run `kc.sh build` then restart) so both the SPI and the classpath theme are picked up.

4. In the realm settings, set **Theme** → **Login Theme** to `cap-captcha`.

   The theme jar carries a `META-INF/keycloak-themes.json` manifest (source: [`src/main/resources/META-INF/keycloak-themes.json`](src/main/resources/META-INF/keycloak-themes.json)) that registers the `cap-captcha` login theme with Keycloak.

## Deploy by copying the directory

If you do not want to deploy the theme jar, copy the `cap-captcha/` directory (from `src/cap-captcha-theme/src/main/resources/theme/`) directly into Keycloak's filesystem `themes/` directory:

```bash
cp -r src/cap-captcha-theme/src/main/resources/theme/cap-captcha /path/to/keycloak/themes/
```

Then set the realm's **Login Theme** to `cap-captcha` and restart. No `META-INF/keycloak-themes.json` is needed for filesystem themes — Keycloak discovers them by directory.

## Fold into an existing theme

If you already have a custom login theme, do not deploy this sample alongside it. Instead, merge the two cap-relevant pieces into your theme:

1. Copy the `<#if capCaptchaRequired??> ... </#if>` block from [`register.ftl`](src/main/resources/theme/cap-captcha/login/register.ftl) into your theme's `register.ftl`, inside the registration `<form>` (e.g. right after `<@registerCommons.termsAcceptance/>`).
2. Add the two `capCaptchaFailed` / `capCaptchaNotConfigured` keys to your theme's `messages_*.properties` (and any further locales you support).

## Prerequisites

- Your cap instance must run with `ENABLE_ASSETS_SERVER=true` so that `<instance>/assets/widget.js` and `<instance>/assets/cap_wasm_bg.wasm` are served. See the [cap asset-server docs](https://trycap.dev/guide/standalone/options.html#asset-server).
- For the CDN variant instead, see the project root [`README.md`](../../README.md) "Theme override" section and swap the two `<script>` lines for the jsdelivr tag.
- If your realm enforces a strict CSP, allowlist the cap instance origin in `script-src`, `style-src`, `connect-src`, and `worker-src`. See the root README "Content-Security-Policy" section.
