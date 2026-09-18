# Automation Anywhere Bot Command Package — Development Best Practices

A reusable reference for building new AA custom packages (Java, `package-compileonly-sdk` /
`package-runtime-sdk`, Gradle + Shadow). Distilled from `playwright_package`,
`SLM-utility-package`, and `Enterprise-Agent-Package`. Copy this file into any new package repo
as a starting checklist.

---

## 1. Project skeleton

```
my-package/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/
├── libs/                                  # flatDir repo: package-*-sdk jars
├── src/
│   ├── main/
│   │   ├── java/com/automationanywhere/botcommand/
│   │   │   ├── <ActionName>.java          # one class per bot action
│   │   │   └── utils/                     # shared helpers, connection objects, managers
│   │   └── resources/
│   │       ├── package.template
│   │       ├── version.properties         # build-number tracking (see §3)
│   │       ├── icons/pkg.svg
│   │       ├── locales/{en_US,de_DE,...}.json
│   │       └── com/automationanywhere/botcommand/samples/messages*.properties
│   └── test/java/                         # TestNG, one Test<ActionName>.java per action
└── build/libs/                            # shadow (fat) JAR output — gitignored
```

Naming: package name in `settings.gradle` (`rootProject.name`) should match the label shown in
Control Room. Action class names are PascalCase and match the `name` in `@CommandPkg`.

---

## 2. Gradle setup

### 2.1 Minimum working `build.gradle` skeleton

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
        flatDir { dirs 'libs' }
        dependencies {
            classpath name: 'package-compileonly-sdk', version: '1.6.0'  // pin, see §2.4
            classpath "gradle.plugin.com.github.johnrengelman:shadow:7.1.1"
        }
    }
}

plugins { id 'java' }

configure(allprojects) {
    apply plugin: 'com.github.johnrengelman.shadow'
    apply plugin: "com.automationanywhere.command-codegen"

    ext {
        groupName = 'com.automationanywhere'
        testNgVersion = '6.14.3'
        loggerVersion = '2.26.1'   // check mvnrepository for current patch before starting
        jnaVersion = '5.19.1'      // only if you need native platform access
    }
    group "$groupName"

    // Auto-incrementing build number: version = 2.x.{buildNum}
    def versionPropsFile = rootProject.file('src/main/resources/version.properties')
    def versionProps = new Properties()
    if (versionPropsFile.exists()) { versionPropsFile.withInputStream { versionProps.load(it) } }
    def nextBuildNum = ((versionProps['build.number'] ?: '0') as Integer) + 1
    version = "2.0.${nextBuildNum}"          // bump the major.minor by hand on breaking changes

    sourceCompatibility = JavaVersion.VERSION_11

    compileJava.options.encoding = 'UTF-8'
    compileTestJava.options.encoding = 'UTF-8'

    repositories {
        mavenLocal()
        mavenCentral()
        flatDir { dirs 'libs' }
    }

    packageJson {
        artifactName = project.name
        group = "$groupName"
        author = "<Your Name>"
        generatePackageWithDateTime = false
    }

    task incrementBuildNumber {
        doFirst {
            def props = new Properties()
            versionPropsFile.withInputStream { props.load(it) }
            def buildNum = (props['build.number'] as Integer) + 1
            props['build.number'] = buildNum.toString()
            versionPropsFile.withOutputStream { props.store(it, null) }
            println "Build version: 2.0.${buildNum}"
        }
    }
    processResources.dependsOn incrementBuildNumber

    classes.dependsOn commandCodeGen

    shadowJar {
        dependsOn incrementBuildNumber
        archiveBaseName = project.name
        classifier = null
        mergeServiceFiles('META-INF/spring.*')
        mergeServiceFiles('META-INF/services/java.sql.Driver')
        zip64 = true
        exclude 'META-INF/MANIFEST.MF'
        exclude 'META-INF/*.SF'
        exclude 'META-INF/*.DSA'
        exclude 'META-INF/*.RSA'
        // Never bundle AA SDK annotation/model classes — the Bot Runner provides
        // them, and a stale bundled copy causes classloading failures on-agent.
        exclude 'com/automationanywhere/commandsdk/annotations/**'
        exclude 'com/automationanywhere/commandsdk/model/**'
        exclude 'com/automationanywhere/commandsdk/generator/**'
        exclude 'com/automationanywhere/commandsdk/processing/**'
        exclude 'com/automationanywhere/commandsdk/transformer/**'
    }
    assemble.dependsOn shadowJar
    jar.enabled = false   // avoid a duplicate thin-jar artifact in build/libs

    dependencies {
        annotationProcessor name: 'package-compileonly-sdk', version: '1.6.0'
        compileOnly name: 'package-compileonly-sdk', version: '1.6.0'
        compileOnly group: "org.apache.logging.log4j", name: "log4j-api", version: "$loggerVersion"
        compileOnly group: "net.java.dev.jna", name: "jna-platform", version: "$jnaVersion"

        implementation name: 'package-runtime-sdk', version: '1.0.0'

        testImplementation name: 'package-compileonly-sdk', version: '1.6.0'
        testImplementation group: "org.testng", name: "testng", version: "$testNgVersion"
    }

    test {
        useTestNG()
        // Prevents intermittent SSL cert errors from TestNG's DTD resolver
        systemProperty 'javax.xml.accessExternalDTD', ''
        systemProperty 'javax.xml.accessExternalSchema', ''
    }
}
```

### 2.2 Build with JDK 11 — do not use JDK 17 with Gradle 7.6.2

`sourceCompatibility = JavaVersion.VERSION_11` is not just a target-bytecode setting here —
**the build must actually run on a JDK 11 JVM.** On JDK 17, the AA SDK's annotation processor
(compiled against Java 8, run by Gradle 7.6.2's annotation-processing worker) fails with:

```
ProcessingException: Incorrectly typed data found for annotation element ...
CommandPkg$Returns[] ... (Found data of type CommandPkg.Returns[])
```

This looks like a module-access problem but isn't — it's a **classloader identity mismatch** in
javac's `AnnotationProxyMaker` when reflecting over annotation-typed array elements
(`CommandPkg$Returns[]`) across two different classloaders. `--add-opens` flags (whether on
`compileJava.options.forkOptions.jvmArgs`, `gradle.properties`' `org.gradle.jvmargs`, or both)
only relax *module* encapsulation — they do not fix a classloader identity mismatch, and in
practice do **not** resolve this error under Gradle 7.6.2, confirmed by direct testing
(`--no-daemon`, fresh daemon, both flags set — still fails).

**Fix: install and build with JDK 11** (e.g. `brew install --cask temurin@11`), and point
`JAVA_HOME` at it for any `./gradlew` invocation against this SDK/Gradle combination:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home
./gradlew clean build
```

If a future package upgrades to a Gradle version with real JDK 17+ annotation-processing
support (or the AA SDK ships a JDK 17-safe annotation processor), re-evaluate — until then,
treat JDK 11 as a hard build requirement, not a preference, regardless of what JDK you use for
day-to-day editing/IDE tooling.

### 2.3 log4j-api / jna-platform are `compileOnly`, not `implementation`

The Bot Agent runtime provides log4j and JNA on its classpath. Declaring them
`implementation` bloats the shadow JAR and risks version clashes with what the agent already
loads. Always use `Logger`/`LogManager` freely in code — they resolve at compile time via
`compileOnly` and are supplied at runtime by the host process.

### 2.4 Pin `package-compileonly-sdk` / `package-runtime-sdk` versions deliberately

Check `libs/` in sibling packages for the SDK version your Control Room tenant expects
(`1.6.0` vs `1.7.0` seen in different repos on this machine). Mismatches between the SDK
version used to compile and the one Bot Agent runs against can cause silent annotation
metadata drift. Bump only when you've confirmed compatibility.

### 2.5 Embedding large native binaries (optional pattern)

If your package needs a native binary/model file bundled for airgapped environments (see
`SLM-utility-package`'s llama.cpp download task), download at build time into
`src/main/resources/<dir>/`, gitignore the archives, and make `processResources` depend on the
download task so `./gradlew build` always has fresh binaries without committing large blobs.

---

## 3. Versioning

- `version.properties` holds a single `build.number` key. Every `processResources` run
  increments it and stamps `version = "<major>.<minor>.<buildNumber>"`.
- Bump `major.minor` by hand for breaking API/behavior changes (new required params, changed
  return shape, removed actions). Never reuse a version string — Control Room caches by
  version, and a stale re-upload of the same version can silently fail to update.
- Commit `version.properties` to git so build numbers are monotonic across machines/CI.

---

## 4. Action class anatomy

Every action is a plain class with one `@Execute` method. Template:

```java
package com.automationanywhere.botcommand;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.StringValue;
import com.automationanywhere.botcommand.exception.BotCommandException;
import com.automationanywhere.commandsdk.annotations.*;
import com.automationanywhere.commandsdk.annotations.rules.NotEmpty;
import com.automationanywhere.commandsdk.model.AllowedTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.automationanywhere.commandsdk.model.AttributeType.*;
import static com.automationanywhere.commandsdk.model.DataType.STRING;

@BotCommand
@CommandPkg(
        name = "myAction",                       // unique, camelCase, matches class purpose
        label = "My Action",                      // or "[[MyAction.label]]" for i18n, see §8
        description = "One sentence, user-facing, explains what it does and why.",
        node_label = "Do thing with {{inputParam}}",  // shown on the canvas node
        icon = "pkg.svg",
        comment = true,
        allowed_agent_targets = {
                AllowedTarget.WINDOWS,
                AllowedTarget.MAC_OS
                // Custom packages can only target WINDOWS/MAC_OS. HEADLESS and
                // ONDEMAND_CLOUD run in Automation Anywhere's cloud compute, which
                // requires a signed jar — signing is done only by the AAI product
                // team, never by custom package authors. A custom package claiming
                // either target will fail Control Room install with
                // "package.invalid.signature".
        },
        return_type = STRING,
        return_required = true
)
public class MyAction {

    private static final Logger logger = LogManager.getLogger(MyAction.class);

    @Execute
    public Value<String> execute(
            @Idx(index = "1", type = TEXT)
            @Pkg(label = "Input Param", description = "What this controls")
            @NotEmpty String inputParam
    ) {
        logger.info("MyAction started - inputParam: {}", inputParam);
        try {
            String result = doWork(inputParam);
            logger.info("MyAction completed");
            return new StringValue(result);
        } catch (BotCommandException e) {
            throw e;   // already user-facing, don't double-wrap
        } catch (Exception e) {
            logger.error("MyAction failed", e);
            throw new BotCommandException("MyAction failed: " + e.getMessage(), e);
        }
    }
}
```

### 4.1 Rules of thumb

- **One responsibility per action.** Don't let one command branch into three unrelated
  behaviors based on a mode flag if they can be three separate, clearly-named actions.
- **`@Idx` index numbers are contiguous strings starting at "1"**, and option sub-indexes use
  `"N.1"`, `"N.2"`, etc. Never reuse or skip an index once a package version has shipped —
  Control Room maps stored bot XML to positional index, and reordering breaks existing bots.
- **Always `@NotEmpty` on required string/credential params.** Let the SDK validate before your
  code runs, rather than hand-rolling null/empty checks for required fields (do still validate
  optional fields you apply defaults to).
- **Catch broad `Exception`, log it, then re-throw as `BotCommandException`** with a message a
  non-developer bot author can act on (what failed + likely cause), not a raw stack trace or
  `e.toString()`.
- **Never catch and re-wrap a `BotCommandException`** you threw yourself earlier in the same
  method — let it propagate as-is.
- **Log at action start (`info`, with key params) and on failure (`error`, with the
  exception).** Avoid `debug`-level spam in the default path; reserve `debug` for
  payload/prompt dumps that are only useful when actively troubleshooting.
- **Never log secrets** — `SecureString`/credential values must never appear in a log line,
  even at `debug`.

---

## 5. Variable / parameter types (`AttributeType` × `DataType`)

`@Idx(type = <AttributeType>)` controls the **UI widget** shown in the bot builder.
`@Pkg(default_value_type = <DataType>)` and `@CommandPkg(return_type = <DataType>)` control the
**underlying value type** for defaults and return values. They are related but distinct — e.g.
a `SELECT` widget still carries a `STRING` value.

| AttributeType | UI widget | Java param type | Typical `DataType` | Notes |
|---|---|---|---|---|
| `TEXT` | single-line text box | `String` | `STRING` | Most common input. |
| `TEXTAREA` | multi-line text box | `String` | `STRING` | Use for prompts, scripts, long free text. |
| `NUMBER` | numeric input | `Double` | `NUMBER` | Always `Double` in the method signature even for integer-only values; call `.intValue()`/`.longValue()` where needed. |
| `CHECKBOX` | boolean toggle | `Boolean` | `BOOLEAN` | Default via `default_value = "true"/"false"`. |
| `SELECT` | dropdown | `String` | `STRING` | Requires `options = { @Idx.Option(...) }`; each option's `value` is what your code receives, `label` is what's shown. |
| `CREDENTIAL` | AA Credential Vault picker | `SecureString` | n/a | Use `apiKey.getInsecureString()` only inside `execute()`, never log it, never persist it unencrypted. |
| `HELP` | non-interactive section header / description text | `String` (unused, no value flows) | n/a | Use to visually group a long parameter list into sections (see `Enterprise-Agent-Package`'s "Agent Tools" / "Capabilities" headers). |
| `FILE` | file picker | `String` (path) | `STRING` | Validate the file exists / is readable before use; don't assume the path is local if `ONDEMAND_CLOUD` is an allowed target. |
| `VARIABLE` | variable picker (bind to a bot variable) | depends on bound type | matches bound `DataType` | Use sparingly — prefer typed params where the UI can constrain input. |

### 5.1 `DataType` (used for `default_value_type`, `return_type`, `return_sub_type`)

| DataType | Java return wrapper | Use for |
|---|---|---|
| `STRING` | `StringValue` | Text output. |
| `NUMBER` | `NumberValue` | Numeric output. |
| `BOOLEAN` | `BooleanValue` | True/false output. |
| `DICTIONARY` | `DictionaryValue` | Structured output — **prefer this for any action returning more than one logical field** (status, payload, timing, error message together). Always set `return_sub_type` to the value type of the dictionary's entries (usually `STRING`). |
| `LIST` | `ListValue` | Homogeneous collections (e.g. list of extracted items). |

### 5.2 Structured (dictionary) returns — recommended envelope pattern

For any action with more than a single trivial return value, return a `DictionaryValue` with a
consistent envelope so bot authors always know what fields to expect, regardless of which
action they're calling:

```java
LinkedHashMap<String, Value<?>> fields = new LinkedHashMap<>();
fields.put("result", new StringValue(payload));       // action-specific fields first
fields.put("status", new StringValue("success"));      // "success" | "error"
fields.put("model", new StringValue(modelId));          // "" if not applicable
fields.put("elapsed_ms", new StringValue(String.valueOf(elapsedMs)));
fields.put("error_message", new StringValue(""));       // populated only on error
return new DictionaryValue(fields);
```

Extract this into a shared `DictionaryHelper` util (see §6) with `success(...)` / `error(...)`
factory methods so every action in the package returns the exact same envelope shape.

### 5.3 Sessions / stateful connections (`@Sessions`)

For packages that model a long-lived resource (browser, DB connection, model handle) across
multiple actions:

```java
@Sessions
private Map<String, Object> sessions;   // injected by Control Room runtime

public void setSessions(Map<String, Object> sessions) {   // required for injection to work
    this.sessions = sessions;
}
```

- Wrap the actual resource(s) in a small connection object (e.g. `PlaywrightConnection`) rather
  than storing raw SDK objects directly — gives you one place to implement `close()`.
- Always check `connObj instanceof YourConnectionType` before casting; a bot author can pass a
  session name that was never opened, or was opened by a different action.
- Provide an explicit "close/release" action (`CloseBrowser`, `DisconnectDatabase`, etc.) and
  make it idempotent — calling it twice, or on an unknown session name, should return a
  friendly message, not throw.
- **Resource cleanup on partial failure**: if creating a session-backed resource involves
  multiple steps that can each fail (e.g. driver created, browser launch fails), catch the
  failure, close whatever was already created, then re-throw. Never leak a native process or
  handle because a later step in the same `execute()` call failed.
- `close()` implementations should be best-effort per sub-resource (try/catch + log each step)
  so a failure closing resource A doesn't prevent resource B from also being released.

---

## 6. Shared `utils/` package conventions

Put anything used by 2+ actions here. Common util classes worth having from day one:

- **`<Domain>Connection.java`** — wraps a stateful external resource (browser, DB, model
  handle) for use with `@Sessions`. Owns `close()`.
- **`DictionaryHelper.java`** — `success(...)` / `error(...)` factories for the standard
  envelope (§5.2). Keeps every action's return shape consistent without copy-pasted
  `LinkedHashMap` boilerplate.
- **`ActionUtils.java`** — small cross-cutting helpers (e.g. resolving/validating an enum-like
  string param into a typed value, with a friendly `BotCommandException` on invalid input).
- **`<Domain>Locator.java` / `<Domain>Manager.java`** — encapsulate any "given a
  loosely-typed selector/identifier string, do X" logic so action classes stay thin
  orchestration layers, not business logic.

Rule: **action classes parse/validate input and format output; utils do the actual work.**
This keeps action classes easy to skim and keeps the reusable logic unit-testable without the
AA SDK annotation machinery in the way.

---

## 7. Error handling standards

- User-facing messages in `BotCommandException` should answer: *what failed*, and *what should
  the bot author do about it* (increase timeout, check credentials, verify selector, etc.).
  Avoid bare `e.getMessage()` with no context when the underlying exception is a generic
  `NullPointerException` or similar — translate it into domain language.
- Classify common failure modes and give tailored messages, e.g.:
  ```java
  if (e.getMessage() != null && e.getMessage().contains("timeout")) {
      errorMsg = "Operation timed out after " + timeoutSeconds + "s. Try increasing timeout.";
  } else {
      errorMsg = "Operation failed: " + e.getMessage();
  }
  ```
- Never swallow an exception silently. If a failure is recoverable (e.g. empty
  optional-field response), log a `warn` and fall back to a sane default — don't just return
  `null`/empty without a log line explaining why.
- Validate all `@NotEmpty`-exempt optional parameters yourself and apply defaults explicitly
  (null checks, trim, range checks) at the top of `execute()`, not scattered through the method
  body.

---

## 8. Internationalization (i18n)

- `label`, `description`, `node_label`, and `@Pkg(label=...)` can reference locale keys via
  `"[[ActionName.fieldName.label]]"` instead of hardcoded English strings.
- Keys live in `src/main/resources/locales/<locale>.json`, one file per supported locale
  (`en_US`, `de_DE`, `ja_JP`, `zh_CN`, etc.), all with the same key set.
- Top-level `"label"` / `"description"` keys in each locale file are the package's own
  label/description (shown in the package browser), separate from per-action keys.
- For a first version of a new package it's fine to hardcode English strings directly in the
  annotations and skip `[[...]]` indirection — only invest in full i18n once the package is
  headed for a multi-locale audience. But if you do use i18n, do it consistently for every
  string in every action, not half-and-half.
- `messages*.properties` (one per locale, e.g. `messages_de_DE.properties`) is for
  **runtime-only** strings (thrown inside Java logic, not annotation metadata) —
  e.g. `emptyInputString=Input string '%s' is empty.` Use `MessagesFactory`-style lookups here,
  not for `@Pkg` labels.

---

## 9. Testing standards

- **Framework: TestNG** (`testImplementation "org.testng:testng:$testNgVersion"`), one
  `Test<ActionName>.java` per action in `src/test/java/`. Mirror the production package
  structure only where the action itself needs it — top-level test classes are fine for a
  small package.
- **Session-based actions**: build a `HashMap<String, Object>` session map manually, call
  `setSessions(...)` on each action instance under test, exactly as Control Room would inject
  it. This lets you chain e.g. Launch → Click → Close in one test without mocking the SDK.
- **Assert on both the return value and side effects** — e.g. after `CloseBrowser`, assert the
  session key was actually removed from the map, not just that the return string looks right.
- **Prefer real integration tests over mocks for this SDK.** Because `@Execute` methods are
  thin wrappers around a real external system (browser, DB, model), a mock-heavy unit test
  suite tends to test the mock, not the integration. Real TestNG tests that spin up a real
  headless browser / local model / local DB are more valuable here — keep them fast enough to
  run in CI (seconds, not minutes, per test) and clearly isolate slow/flaky ones behind a
  TestNG `@Test(groups = "slow")` or similar if the suite grows.
- **Pure logic extracted to utils is the exception** — anything in `utils/` that doesn't touch
  the SDK or an external system (a parser, an escaper, a formatter) should get plain unit tests
  with no session/SDK setup at all. If a method is `private` and worth testing in isolation,
  consider making it package-private (no modifier) specifically so a test in the same package
  can call it directly — see `RedactPII.parseRedactionResponse` for this pattern.
- **Add a regression test for every real bug you fix**, not just for new features. If a
  resource-leak or off-by-one is found in review, the fix isn't done until there's a test that
  would have caught it.
- Configure TestNG to skip external DTD resolution (see §2.1's `test { }` block) — a common
  source of flaky CI failures unrelated to your actual code.

---

## 10. Logging standards

- One `private static final Logger logger = LogManager.getLogger(ThisClass.class);` per class
  that does meaningful work (every action class, every non-trivial util class).
- `info`: action start (with key input params, never secrets) and action success/completion
  (with timing if the operation is non-trivial).
- `warn`: recoverable/degraded situations (empty response falling back to a default, a cleanup
  step failing non-fatally).
- `error`: always paired with the actual exception object (`logger.error("X failed", e)`, not
  `logger.error("X failed: " + e.getMessage())`) so stack traces aren't lost.
- `debug`: verbose diagnostic detail (full prompt text, full payload) — never at `info` or
  above, since these can be large and may contain sensitive data.
- Use parameterized logging (`logger.info("Started - {}", value)`), not string concatenation —
  avoids the concatenation cost when the log level filters the line out.

---

## 11. Security checklist

- Credentials always flow through `@Idx(type = CREDENTIAL)` / `SecureString`, never `TEXT`.
- Never log a `SecureString`'s insecure value, at any log level.
- If building dynamic JS/SQL/shell strings from user input (e.g. a "JS Path" selector mode),
  escape/parameterize rather than trusting raw string interpolation. Prefer parameterized APIs
  (Playwright's `page.evaluate(fn, arg)` two-arg form, prepared statements) over string-building
  wherever the underlying SDK supports it.
- Validate file paths and URLs from bot input before use if the action can run on
  `ONDEMAND_CLOUD` — don't assume a local filesystem or trusted network.
- Exclude AA SDK internal packages from the shadow JAR (see §2.1) to avoid shipping/overriding
  platform-provided security-relevant classes.

---

## 12. Pre-release checklist for a new package/action

- [ ] `@CommandPkg` has a clear `label`, one-sentence `description`, and a `node_label` that
      renders sensibly with real values interpolated.
- [ ] Every required param has `@NotEmpty`; every optional param has an explicit default
      applied in code, not just in the annotation.
- [ ] `allowed_agent_targets` is `WINDOWS`/`MAC_OS` only — custom packages cannot target
      `HEADLESS`/`ONDEMAND_CLOUD` at all, since those run in Automation Anywhere's cloud
      compute and require a jar signed by the AAI product team. Claiming either target
      produces a `package.invalid.signature` error at install time, not a runtime warning.
- [ ] Logging added at start/success/error for the action.
- [ ] Return type uses `DICTIONARY` + envelope pattern if there's more than one logical output.
- [ ] Resource cleanup on both the happy path and every failure branch (no leaked processes,
      handles, or sessions).
- [ ] TestNG test exists and passes locally (`./gradlew test`).
- [ ] `./gradlew build` produces the shadow JAR cleanly with no excluded-package
      classloading warnings.
- [ ] Version bumped appropriately (§3) — never re-ship an already-used version string.
- [ ] Dependency versions checked against current releases (Playwright, log4j, JNA, or any
      other 3rd-party lib) — don't let a new package start life already out of date.
- [ ] No secrets, API keys, or tokens committed to the repo (check `.gitignore` covers any
      locally-downloaded binaries/models).

---

## 13. Quick reference: common Gradle commands

```bash
./gradlew build        # compile, run tests, produce shadow JAR in build/libs/
./gradlew test          # run TestNG suite only
./gradlew compileJava    # fast syntax/annotation-processing check without full build
./gradlew clean build   # full rebuild, use if build-number/version state looks stale
```
