package com.tradej.broker.icici.auth;

import com.tradej.broker.icici.config.BreezeConnectionSettings;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Automates ICICI Breeze browser login (username + password + TOTP) and captures
 * {@code apisession} from the post-login redirect URL.
 * <p>
 * Mirrors the community Selenium flow documented on ICICI Direct forums.
 */
public final class BreezeBrowserSessionCapture {

    private static final Logger log = LoggerFactory.getLogger(BreezeBrowserSessionCapture.class);
    private static final String LOGIN_BASE = "https://api.icicidirect.com/apiuser/login?api_key=";

    private static final List<By> USERNAME_SELECTORS = List.of(
            By.cssSelector("#txtuid"),
            By.cssSelector("input[name='userId']"),
            By.cssSelector("input[id='user-id']"),
            By.cssSelector("input[placeholder*='User']"),
            By.cssSelector("input[type='text']")
    );
    private static final List<By> PASSWORD_SELECTORS = List.of(
            By.cssSelector("#txtPass"),
            By.cssSelector("input[name='password']"),
            By.cssSelector("input[type='password']")
    );
    private static final List<By> SUBMIT_SELECTORS = List.of(
            By.cssSelector("#btnSubmit"),
            By.cssSelector("button[type='submit']"),
            By.cssSelector("input[type='submit']"),
            By.xpath("//button[contains(translate(normalize-space(.),'LOGIN','login'),'login')]")
    );
    private static final List<By> OTP_CONTAINER_SELECTORS = List.of(
            By.cssSelector("input[tg-nm='otp']"),
            By.cssSelector("input[name='otp']"),
            By.cssSelector("input[placeholder*='OTP']"),
            By.cssSelector("input[placeholder*='otp']")
    );

    private final BreezeConnectionSettings settings;
    private final BreezeTotpGenerator totpGenerator;

    public BreezeBrowserSessionCapture(BreezeConnectionSettings settings) {
        this(settings, new BreezeTotpGenerator());
    }

    BreezeBrowserSessionCapture(BreezeConnectionSettings settings, BreezeTotpGenerator totpGenerator) {
        this.settings = settings;
        this.totpGenerator = totpGenerator;
    }

    /**
     * Runs headless/headed Chrome login and returns the raw {@code API_Session} token.
     * Persists the token to {@link BreezeConnectionSettings#apiSessionFile()} for reuse.
     */
    public String captureApiSession() {
        String username = readSecretFile(settings.usernameFile(), "username");
        String password = readSecretFile(settings.passwordFile(), "password");
        String totpSecret = readSecretFile(settings.totpSecretFile(), "TOTP secret");

        long timeoutMs = settings.browserLoginTimeoutSeconds() * 1000L;
        BreezeApiSessionRedirectServer redirectServer = startRedirectServerIfAvailable();
        try {
            return runBrowserLogin(username, password, totpSecret, redirectServer, timeoutMs);
        } finally {
            if (redirectServer != null) {
                redirectServer.close();
            }
        }
    }

    private BreezeApiSessionRedirectServer startRedirectServerIfAvailable() {
        try {
            BreezeApiSessionRedirectServer server = new BreezeApiSessionRedirectServer(
                    settings.loginRedirectPort(),
                    settings.loginRedirectPath()
            );
            server.start();
            return server;
        } catch (IOException ex) {
            log.warn(
                    "ICICI redirect listener unavailable on 127.0.0.1:{}:{} ({}). "
                            + "Will capture apisession from browser URL instead.",
                    settings.loginRedirectPort(),
                    settings.loginRedirectPath(),
                    ex.getMessage()
            );
            return null;
        }
    }

    private String runBrowserLogin(
            String username,
            String password,
            String totpSecret,
            BreezeApiSessionRedirectServer redirectServer,
            long timeoutMs
    ) {
        ChromeOptions options = configureChromeOptions();

        WebDriver driver = new ChromeDriver(options);
        try {
            Duration waitTimeout = Duration.ofSeconds(Math.max(30, settings.browserLoginTimeoutSeconds()));
            WebDriverWait wait = new WebDriverWait(driver, waitTimeout);

            String loginUrl = LOGIN_BASE + URLEncoder.encode(settings.appKey(), StandardCharsets.UTF_8);
            log.info("Opening ICICI login URL (auto-submit form → tradelogin)");
            driver.get(loginUrl);

            // Initial page only contains hidden fields + JS auto-submit to tradelogin.
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#txtuid")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#txtPass")),
                    ExpectedConditions.urlContains("tradelogin")
            ));

            WebElement usernameInput = waitForAny(wait, USERNAME_SELECTORS);
            fillInput(driver, usernameInput, username);

            WebElement passwordInput = waitForAny(wait, PASSWORD_SELECTORS);
            fillInput(driver, passwordInput, password);

            acceptTermsAndConditions(driver, wait);
            clickLoginSubmit(driver, wait);

            enterTotpWithRetry(driver, wait, totpSecret);

            String apiSession = waitForApiSession(driver, wait, redirectServer);
            persistApiSession(apiSession);
            log.info("Captured ICICI API_Session via browser automation");
            return apiSession;
        } catch (BreezeBrowserAuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BreezeBrowserAuthException("ICICI browser login failed: " + ex.getMessage(), ex);
        } finally {
            driver.quit();
        }
    }

    private ChromeOptions configureChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        if (settings.browserHeadless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,900",
                "--disable-blink-features=AutomationControlled"
        );
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        return options;
    }

    private static void fillInput(WebDriver driver, WebElement element, String value) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        try {
            new Actions(driver).moveToElement(element).click().perform();
        } catch (RuntimeException ex) {
            js.executeScript("arguments[0].focus();", element);
        }
        element.clear();
        element.sendKeys(value);
        js.executeScript(
                "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                        + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));"
                        + "arguments[0].dispatchEvent(new Event('blur', {bubbles: true}));",
                element
        );
    }

    private static void acceptTermsAndConditions(WebDriver driver, WebDriverWait wait) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#chkssTnc")));
            Object checked = js.executeScript(
                    "var cb = document.getElementById('chkssTnc');"
                            + "if (!cb) { return false; }"
                            + "if (window.jQuery) {"
                            + "  window.jQuery(cb).prop('checked', true).trigger('change');"
                            + "} else {"
                            + "  cb.checked = true;"
                            + "  cb.dispatchEvent(new Event('change', {bubbles: true}));"
                            + "}"
                            + "return cb.checked === true;"
            );
            if (!Boolean.TRUE.equals(checked)) {
                throw new BreezeBrowserAuthException("Failed to accept ICICI Terms & Conditions (#chkssTnc)");
            }
            log.info("Accepted ICICI Terms & Conditions checkbox");
        } catch (BreezeBrowserAuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Older login skins may not show the T&C checkbox.
        }
    }

    private void enterTotpWithRetry(WebDriver driver, WebDriverWait wait, String totpSecret) {
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[tg-nm='otp']")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='otp']")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='OTP']")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder*='otp']")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("#txtOtp"))
        ));

        int[] windowOffsets = {0, -1, 1};
        for (int offset : windowOffsets) {
            String totpCode = totpGenerator.currentCode(totpSecret, offset);
            log.info("Entering ICICI TOTP (window offset {})", offset);
            fillTotpFields(driver, wait, totpCode);
            if (waitForTotpAccepted(driver, Duration.ofSeconds(20))) {
                log.info("ICICI accepted TOTP at window offset {}", offset);
                return;
            }
            log.warn("ICICI TOTP window offset {} did not advance login", offset);
        }
        throw new BreezeBrowserAuthException(
                "ICICI rejected TOTP after clock-drift retries. Verify config/icici-totp-secret.txt matches your authenticator app.");
    }

    private static boolean waitForTotpAccepted(WebDriver driver, Duration timeout) {
        WebDriverWait otpWait = new WebDriverWait(driver, timeout);
        try {
            Boolean accepted = otpWait.until(d -> {
                if (hasIncorrectTotpAlert(d)) {
                    return Boolean.FALSE;
                }
                if (captureApiSessionFromBrowser(d) != null) {
                    return Boolean.TRUE;
                }
                if (hasVisibleOtpError(d)) {
                    return Boolean.FALSE;
                }
                return null;
            });
            return Boolean.TRUE.equals(accepted);
        } catch (TimeoutException ex) {
            return false;
        }
    }

    private static boolean hasVisibleOtpError(WebDriver driver) {
        try {
            WebElement error = driver.findElement(By.cssSelector("#otperr"));
            return error.isDisplayed() && !error.getText().isBlank();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static void fillTotpFields(WebDriver driver, WebDriverWait wait, String totpCode) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        List<WebElement> digitInputs = driver.findElements(By.cssSelector("input[tg-nm='otp']"));
        if (digitInputs.size() >= 6) {
            Object result = js.executeScript(
                    "var code = arguments[0];"
                            + "var inputs = document.querySelectorAll(\"input[tg-nm='otp']\");"
                            + "if (inputs.length < 6) { return 'missing-inputs'; }"
                            + "for (var i = 0; i < 6; i++) {"
                            + "  if (window.jQuery) {"
                            + "    window.jQuery(inputs[i]).val(code.charAt(i)).trigger('input').trigger('keyup').trigger('change');"
                            + "  } else {"
                            + "    inputs[i].value = code.charAt(i);"
                            + "    inputs[i].dispatchEvent(new Event('input', {bubbles: true}));"
                            + "    inputs[i].dispatchEvent(new Event('keyup', {bubbles: true}));"
                            + "  }"
                            + "}"
                            + "if (typeof submitotp === 'function') { submitotp(); return 'submitotp'; }"
                            + "return 'filled-no-submit';",
                    totpCode
            );
            log.info("ICICI TOTP entry result: {}", result);
            return;
        }

        WebElement otpInput = waitForAny(wait, OTP_CONTAINER_SELECTORS);
        otpInput.clear();
        otpInput.sendKeys(totpCode);
        otpInput.sendKeys(Keys.ENTER);

        for (WebElement button : driver.findElements(By.cssSelector("button, input[type='button'], input[type='submit']"))) {
            if (!button.isDisplayed() || !button.isEnabled()) {
                continue;
            }
            String label = button.getText().toLowerCase(Locale.ROOT);
            if (label.contains("verify") || label.contains("submit") || label.contains("continue")) {
                button.click();
                return;
            }
        }
    }

    private static boolean hasIncorrectTotpAlert(WebDriver driver) {
        try {
            Alert alert = driver.switchTo().alert();
            String text = alert.getText();
            alert.accept();
            if (text != null && text.toLowerCase(Locale.ROOT).contains("terms and conditions")) {
                throw new BreezeBrowserAuthException(
                        "ICICI login blocked: Terms & Conditions checkbox (#chkssTnc) was not accepted");
            }
            return text != null && (text.toLowerCase(Locale.ROOT).contains("totp")
                    || text.toLowerCase(Locale.ROOT).contains("otp")
                    || text.toLowerCase(Locale.ROOT).contains("invalid"));
        } catch (org.openqa.selenium.NoAlertPresentException ex) {
            return false;
        }
    }

    private static String waitForApiSession(
            WebDriver driver,
            WebDriverWait wait,
            BreezeApiSessionRedirectServer redirectServer
    ) {
        try {
            return wait.until(d -> {
                if (redirectServer != null) {
                    String fromServer = redirectServer.pollApiSession();
                    if (fromServer != null && !fromServer.isBlank()) {
                        return fromServer;
                    }
                }
                String apiSession = captureApiSessionFromBrowser(d);
                return (apiSession == null || apiSession.isBlank()) ? null : apiSession;
            });
        } catch (TimeoutException ex) {
            throw new BreezeBrowserAuthException(
                    "Timed out waiting for apisession in browser redirect URL (last URL: "
                            + safeCurrentUrl(driver)
                            + "). ICICI typically redirects to https://api.icicidirect.com/?apisession=... "
                            + "after TOTP — ensure that matches your app's registered redirect URL."
            );
        }
    }

    private static String safeCurrentUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl();
        } catch (RuntimeException ex) {
            return "(unavailable: " + ex.getMessage() + ")";
        }
    }

    private static String captureApiSessionFromBrowser(WebDriver driver) {
        String fromUrl = BreezeApiSessionUrlParser.parseApiSession(safeCurrentUrl(driver));
        if (fromUrl != null && !fromUrl.isBlank()) {
            return fromUrl;
        }
        try {
            String fromPage = BreezeApiSessionUrlParser.parseApiSessionFromText(driver.getPageSource());
            if (fromPage != null && !fromPage.isBlank()) {
                log.info("Found apisession in page HTML (AJAX redirect); navigating to complete login");
                driver.get("https://api.icicidirect.com/?apisession=" + fromPage);
                return fromPage;
            }
        } catch (RuntimeException ex) {
            log.debug("Unable to scan page source for apisession: {}", ex.getMessage());
        }
        return null;
    }

    private static WebElement waitForAny(WebDriverWait wait, List<By> selectors) {
        for (By selector : selectors) {
            try {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
            } catch (RuntimeException ignored) {
                // try next selector
            }
        }
        throw new BreezeBrowserAuthException(
                "Could not locate ICICI login element for selectors: " + selectors);
    }

    private static void clickLoginSubmit(WebDriver driver, WebDriverWait wait) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#btnSubmit")));

        log.info("Submitting ICICI login via submitform()");
        Object result = js.executeScript(
                "try {"
                        + "  if (typeof submitform === 'function') { submitform(); return 'submitform'; }"
                        + "  var btn = document.getElementById('btnSubmit');"
                        + "  if (btn) { btn.click(); return 'btnSubmit.click'; }"
                        + "  return 'missing';"
                        + "} catch (e) { return 'error:' + e.message; }"
        );
        log.info("ICICI login submit result: {}", result);

        dismissTermsAlertIfPresent(driver, wait);

        WebDriverWait otpWait = new WebDriverWait(driver, Duration.ofSeconds(45));
        try {
            otpWait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[tg-nm='otp']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='otp']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#txtOtp")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#dvgetotp input"))
            ));
            log.info("ICICI login submit reached OTP page");
        } catch (TimeoutException ex) {
            throw new BreezeBrowserAuthException(
                    "Login submit did not reach OTP page (last URL: "
                            + safeCurrentUrl(driver)
                            + "). Ensure username/password are correct, #chkssTnc is checked, "
                            + "and try icici.browserHeadless=false if headless Chrome is blocked."
            );
        }
    }

    private static void dismissTermsAlertIfPresent(WebDriver driver, WebDriverWait wait) {
        try {
            Alert alert = driver.switchTo().alert();
            String text = alert.getText();
            alert.accept();
            if (text != null && text.toLowerCase(Locale.ROOT).contains("terms")) {
                acceptTermsAndConditions(driver, wait);
                ((JavascriptExecutor) driver).executeScript(
                        "if (typeof submitform === 'function') { submitform(); }"
                                + "else { document.getElementById('btnSubmit')?.click(); }"
                );
            }
        } catch (org.openqa.selenium.NoAlertPresentException ignored) {
        }
    }

    private static void clickFirst(WebDriverWait wait, List<By> selectors) {
        WebElement element = waitForAny(wait, selectors);
        element.click();
    }

    private void persistApiSession(String apiSession) {
        Path file = settings.apiSessionFile();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, apiSession + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BreezeBrowserAuthException("Failed to persist API session to " + file, ex);
        }
    }

    private static String readSecretFile(Path path, String label) {
        try {
            if (path == null || !Files.exists(path)) {
                throw new BreezeBrowserAuthException("ICICI " + label + " file missing: " + path);
            }
            String value = Files.readString(path).trim();
            if (value.isBlank()) {
                throw new BreezeBrowserAuthException("ICICI " + label + " file is blank: " + path);
            }
            return value;
        } catch (IOException ex) {
            throw new BreezeBrowserAuthException("Failed to read ICICI " + label + " from " + path, ex);
        }
    }
}
