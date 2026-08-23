---
name: eclipse-zide-reference
description: Eclipse ZIDE plugin architecture and behavior reference for the IntelliJ ZIDE plugin (com.zoho.dzide). Use when modifying project creation, launch configurations, code checks, commit integration, deployment management, hooks, config parsing, or any feature that mirrors the Eclipse ZIDE plugin (com.zoho.zide v2.4.7). Triggers on work in src/main/kotlin/com/zoho/dzide/ or when the user mentions Eclipse ZIDE, service creation, zide_config.xml, service.xml, zide_properties.xml, deployment patching, code checks, or commit checks.
---

# Eclipse ZIDE Plugin Reference for IntelliJ Development

This agent contains the complete architecture and behavior of the Eclipse ZIDE plugin (`com.zoho.zide` v2.4.7) so you can maintain and extend the IntelliJ ZIDE plugin (`com.zoho.dzide`) without needing the Eclipse decompiled source.

## Plugin Identity

- **Bundle:** `com.zoho.zide` (singleton), JavaSE-17, lazy activation
- **Activator:** `com.zoho.zide.activator.Activator` (bundle start/stop)
- **Startup:** `PluginStarter.earlyStartup()` — proxy config, formatter, launch listeners, service XML updates, VM startup

## Package Architecture (35 packages, ~250 classes)

| Package | Key Classes | Purpose |
|---|---|---|
| `activator` | Activator, PluginStarter | Plugin lifecycle, earlyStartup tasks |
| `codeassistant` | CAMenuHandler, ChatMessageHandler | AI chat panel (OpenAI-compatible) |
| `codecheck` | CodeCheck, CodeCheckRunner, CommitChecker, 15+ check classes | Code quality checks with Eclipse markers |
| `command` | ZideCommand, ProcessWrapper | Shell/process execution |
| `config.model` | **Service**, **ServiceConfig** | Core service creation/update/rename engine |
| `configuration` | TextReplacer, XMLReplacer, ReplacerFactory | Config file variable substitution |
| `core.launching` | LaunchUtil, 7 delegate classes | Launch configuration creation |
| `dbutil` | ConfigApiUtil, ConfigDBUtil, MysqlDBUtil | CMTools REST API, DB operations |
| `diagnostics` | NetworkCheckGroup, CheckRunnerUI | Network diagnostics with fixes |
| `hg` | ZideMercurialRepository, HgIgnoreHandler | Mercurial integration |
| `repository` | GitRepository, HgRepository, RepositoryFactory | VCS abstraction (repoType: 1=HG, 2=Git) |
| `repository.commitcheck` | ZideCommitAction, CommitChecker | Pre-commit code check enforcement |
| `repository.ignore` | GitIgnoreHandler, HgIgnoreHandler | .gitignore/.hgignore management |
| `resource` | ResourceHandler, HttpFileProvider | Multi-threaded HTTP download with resume |
| `scheduler` | SchedulerStartup, AutomaticUpdateJob | Auto-update scheduling |
| `ui.wizards.pages` | CreateServicePage, WorkingSetPage, RunnableServicePage | Wizard UI pages |
| `util` | ServiceAPI, ZideConfigAPI, ZidePropertiesAPI, FileUtils | Core utilities |
| `vm` | VagrantExecutor, ZideDockerService | VM/Docker management |

## Service Creation Flow (19 Steps)

The Eclipse `Service.create()` method in `config.model.Service` runs these steps:

1. **Init** — log start, add to temp list, resolve repo type (1=HG, 2=Git)
2. **Clone repository** — `RepositoryFactory.getRepository()` -> HG share or full clone / Git clone
3. **Add ZideProjectNature** — marks project for Zide menus/testers (IntelliJ: not needed)
4. **Create `.zide_resources/`** — metadata folder (derived)
5. **Add ignore entries** — `.hgignore` (with `re:` prefix) or `.git/info/exclude` patterns
6. **Download + extract build** — HTTP multi-threaded download, zip/tgz extraction, WAR extraction for M19
7. **Create `service_info.xml`** — 20+ metadata fields (see below)
8. **Create JUnit info** — `junit_status.txt`, `junit_checklist.txt`
9. **Set Java compilation prefs** — `zide.pref.enable_java_compilation_check`
10. **Pre-creation hooks** — `ProjectHook.preCreation()` via AntHookRunner
11. **Create natures/facets/classpath** — Java natures, WTP facets, source entries, user libraries from deployment JARs, JRE container, project dependencies, Java compliance level
12. **Set path variable** — `{SERVICE_NAME}_DEPLOYMENT_PATH`
13. **Create launch config** — Tomcat (WTP) or standalone Java launch via LaunchUtil
14. **Create builder config** — Ant builder for `build/ant.properties`
15. **Create Zide properties** — deployment properties (IAM URL, ports, DB)
16. **Update services menu** — refresh dynamic "Zide Services" menu
17. **Post-creation hooks** — `ProjectHook.postCreation()`
18. **Zide-module hooks** — `ProjectHook.zideModuleHookCreation()`
19. **Log completion** — timing info

**Rollback on failure:** deletes project, deployment folder, launch configs, Tomcat server/runtime.

## Launch System

7 launch configuration types, each with a delegate and classpath provider:

| Type | Delegate | Purpose |
|---|---|---|
| Zide Services | ZohoLaunchConfigurationDelegate | Standalone Java service launch |
| Zide Tomcat | ZohoTomcatConfigurationDelegate | Tomcat WTP launch |
| LookingGlass | LookingGlassLaunchConfigurationDelegate | Test automation |
| Hacksaw | HacksawLaunchConfigurationDelegate | Security scan |
| UDE | UDELaunchConfiguration | User Data Emulation |
| JUnit | JunitLaunchConfiguration | Test runner |
| Builder | (via LaunchUtil) | Ant build |

`LaunchUtil` resolves: main class, VM args, program args, classpath, source paths from `Zide.properties`.

## Code Check Framework

`CodeCheck` interface: `getResource()`, `getMarkerType()`, `getViolations()`, `isValidExtension()`, `isExcluded()`

15+ checks: I18NCheck, PMDCheck, SecurityCheck, DollarIDCheck, JSCheck, JsHintCheck, CtrlMCheck, JavaPackageCheck, ZohoKeywordCheck, PropertiesStandardCheck, SMCCheck, WrapperCheck, HacksawReportMarker, CustomCheckMarker, JunitCheckMarker.

`CodeCheckRunner` orchestrates all checks, creates Eclipse IMarker instances. `CodeCheckScheduler` triggers on resource changes or schedule.

## Commit Integration

`ZideCommitAction` overrides EGit's commit action:
1. For each staged resource: `CommitChecker.canCommit()` checks for unresolved markers
2. Exclude patterns from `.zide/codecheck.conf` are applied
3. Legal declaration enforced: `LICENSE COMPATIBLE:YES` required
4. Java compilation errors block commit if enabled
5. JUnit test execution required if enabled

## Configuration System

**`zide_config.xml`** (8000+ lines): centralized service definitions with deployment, library, launch, and hook configuration per service. Parsed by `ZideConfig` singleton.

**`Zide.properties`**: per-module properties at `deployment/{moduleDir}/{deployType}/Zide.properties`. Contains launch VM args, classpath config, hook tasks, auto-resource-copy mappings.

**Config Replacers**: `TextReplacer` (find-and-replace), `XMLReplacer` (XPath-based), `ReplacerFactory` selects by type. Variables: `{HOSTNAME}`, `{HTTP_PORT}`, `{HTTPS_PORT}`, `{IAM_URL}`, DB credentials.

## Hooks System

Hooks defined per-service in the zide config repository. Executed via `AntHookRunner`:
- **precreationhook** — before natures/facets
- **postcreationhook** — after full setup
- **zidemodulehook** — module-level hooks
- **postservicetarget** — used during deployment updates (with DB properties)

Hook build.xml location: `.zide_resources/zide_hook/build.xml`
Properties passed: `REPOSITORY_PATH`, `DEPLOYMENT_PATH`, `ZIDE.PARENT_SERVICE`

## Eclipse-to-IntelliJ Mapping

| Eclipse | IntelliJ |
|---|---|
| BundleActivator | ProjectActivity / StartupActivity |
| IProjectNature | Facet or marker file check |
| Launch Configuration | RunConfiguration + ConfigurationType |
| Launch Delegate | RunProfileState / CommandLineState |
| Classpath Provider | Module dependencies |
| IMarker | ExternalAnnotator / LocalInspectionTool |
| IMarkerResolution | IntentionAction / QuickFix |
| Property Tester | AnAction.update() |
| Preference Page | Configurable (applicationConfigurable EP) |
| Property Page | ProjectConfigurable |
| Command + Handler | AnAction |
| View (ViewPart) | ToolWindowFactory |
| SWT Browser | JBCefBrowser (JCEF) |
| Eclipse Job | Task.Backgroundable |
| ResourceChangeListener | BulkFileListener |
| IStartup | postStartupActivity |
| Working Set | Scopes / Module Groups |
| SWT Dialog | DialogWrapper |
| SWT Wizard | ModuleBuilder / multi-step DialogWrapper |

## Deployment Folder Structure

```
{WORKSPACE}/deployment/{SERVICE_NAME}/
  AdventNet/Sas/tomcat/          (Tomcat-based M19 services)
    bin/catalina.sh
    conf/server.xml
    lib/
    webapps/{SERVICE_NAME}/      (extracted from ROOT.war)
      WEB-INF/
        classes/
        lib/
        conf/Persistence/persistence-configurations.xml
        security-properties.xml
        web.xml
  AdventNet/Sas/                 (standalone services)
    lib/
    bin/
```

## Key Behavioral Notes

- `isWarModel()` returns true when `deployType == "M19"` (Tomcat deployment)
- `isDeployable()` checks if the service has a download URL in zide_config.xml
- Runnable services are sorted with ZOHOACCOUNTS first
- Credential stripping: HgrcCredentialUtil removes passwords from `.hgrc` and git origin
- Download supports resume: if interrupted, user prompted to continue
- Proxy bypass hosts: `cm-server.csez.zohocorpin.com, cmsuite.csez.zohocorpin.com, cmtools.csez.zohocorpin.com, build.zohocorp.com, git.csez.zohocorpin.com, zide, zide.csez.zohocorpin.com`

## .zide_resources/ Complete Layout

```
.zide_resources/
  service.xml              -- Service metadata (20+ ZIDE.* fields)
  zide_properties.xml      -- Deployment properties (host, ports, IAM, DB)
  repository.properties    -- repositorypath=<absolute project path>
  zide_hook/
    build.xml              -- ANT targets: precreationhook, postcreationhook, zidemodulehook
  zide_build/
    build.xml              -- ANT target: postservicetarget (deploy-sync + update deployment)
```

If the cloned repository already contains these files, the plugin preserves them and does not overwrite.

## service.xml Field Reference

| Field | Example Value | Description |
|---|---|---|
| `ZIDE.REPOSITORY_TRUNK` | `default`, `master` | Branch/revision cloned |
| `ZIDE.SSH_USERNAME` | `hari` | Clone username |
| `ZIDE.REPOSITORY_MODULE_DIR` | `zharehub` | Repository module directory name |
| `ZIDE.DOWNLOAD_URL` | `https://build.zohocorp.com/...zip` | Build archive download URL |
| `ZIDE.LOCAL_DOWNLOAD_URL` | `/path/to/local.zip` | Local build path (if used) |
| `ZIDE.PARENT_SERVICE` | `zharehub` | Parent service name (for dependencies) |
| `ZIDE.DEPLOYMENT_FOLDER` | `/Users/.../deployment/zharehub` | Absolute path to deployment |
| `ZIDE.DEPEND_SERVICES` | `dep1,dep2` | Comma-separated dependent service names |
| `ZIDE.RUNNABLE_SERVICES` | `ZOHOACCOUNTS` | Comma-separated runnable services |
| `ZIDE.SUBMODULES` | `sub1,sub2` | Comma-separated sub-module names |
| `ZIDE.SERVICE_KEY` | `ZHAREHUB` | Service key from zide_config.xml |
| `ZIDE.COLD_START` | `true` | Whether this is a fresh deployment |
| `ZIDE.DO_REPLACE` | `false` | Whether config replacement has been done |
| `ZIDE.PERMISSION` | `0` or `1` | 0=read-only, 1=read-write |
| `ZIDE.SOURCES` | `src/main/java` | Comma-separated source folder paths |
| `ZIDE.REPO_TYPE` | `1` or `2` | 1=Mercurial, 2=Git |
| `ZIDE.DEPLOY_TYPE` | `` or `M19` | Empty=standalone, M19=Tomcat WAR model |
| `ZIDE.MI_DEPLOYMENT` | `true`/`false` | Whether MI WAR was extracted |
| `ZIDE.TOMCAT_VERSION` | `9.0.65` | Auto-detected from catalina.jar |
| `ZIDE.PROJECT_JRE_HOME` | `/usr/lib/jvm/java-17` | JDK home path |

## zide_properties.xml Field Reference

| Field | Example Value | Description |
|---|---|---|
| `ZIDE.HOST_NAME` | `hari-19343.csez.zohocorpin.com` | Machine hostname |
| `ZIDE.HTTP_PORT` | `8080` | HTTP port |
| `ZIDE.HTTPS_PORT` | `8443` | HTTPS port |
| `ZIDE.IAM_SERVER` | `https://accounts.csez.zohocorpin.com` | IAM server URL |
| `ZIDE.IAM_SERVICENAME` | `ZhareHub` | IAM service name |
| `ZIDE.USER_NAME` | `hari` | Developer username |
| `ZIDE.USER_EMAIL` | `hari@zohocorp.com` | Developer email |
| `ZIDE.MACHINE_IP` | `10.0.0.5` | Machine IP address |
| `ZIDE_DB_NAME` | `zharehub` | Database name |
| `ZIDE.SCHEMA_NAME` | `jbossdb` | Database schema name |

## zide_config.xml Service Definition Schema

```xml
<service key="SERVICE_KEY" isdeployable="true|false" name="DisplayName" moduledir="repo_dir">
  <deployment>
    <folder>path/in/archive</folder>
    <archivename>BuildArchive.zip</archivename>
  </deployment>
  <runnables>ZOHOACCOUNTS,OTHER_SERVICE</runnables>
  <project>
    <pathconfiguration>
      <libraryconfiguration>
        <libraries>
          <library name="LIB_NAME">
            <folder>relative/lib/path</folder>
          </library>
        </libraries>
        <classlibraries>
          <folder>relative/classpath/path</folder>
        </classlibraries>
        <jars>
          <folder>relative/jar/path</folder>
        </jars>
      </libraryconfiguration>
    </pathconfiguration>
    <auto-resourcecopy enabled="true">
      <copysets>
        <copy>
          <source>webapps/ROOT</source>
          <destination>webapps/ROOT</destination>
        </copy>
      </copysets>
    </auto-resourcecopy>
  </project>
  <configpage>
    <fields>uname_0,umail_0,dbname,url_0,label_0,iam_0,http_0,https_0,iamserv_0</fields>
  </configpage>
  <repository>
    <http>http://integ-build3/cgi-bin2/hgwebdir.cgi/</http>
    <ssh>ssh://{USER_NAME}@integ-build3//advent/hg/</ssh>
    <https>https://git.csez.zohocorpin.com/</https>
  </repository>
</service>
```

100+ services defined including: ZOHOACCOUNTS, ZOHOMAIL, ZOHONOTEBOOK, CRM, ZSECCERT, CODESEARCH, SHORTENURL, ZOHO_BAAS, and many more.

## Code Check Marker Types

| Marker Type ID | Check Class | Description |
|---|---|---|
| `com.zoho.zide.i18NMarker` | I18NCheck | Internationalization validation |
| `com.zoho.zide.pmdMarker` | PMDCheck | PMD static analysis |
| `com.zoho.zide.SecurityMarker` | SecurityCheck | Security patterns |
| `com.zoho.zide.DollarIDMarker` | DollarIDCheck | `$Id$` keyword |
| `com.zoho.zide.JSCheckMarker` | JSCheck | JavaScript validation |
| `com.zoho.zide.JsHintMarker` | JsHintCheck | JSHint linting |
| `com.zoho.zide.CtrlMMarker` | CtrlMCheck | Carriage return detection |
| `com.zoho.zide.JavaPackageMarker` | JavaPackageCheck | Package vs folder validation |
| `com.zoho.zide.ZohoKeywordMarker` | ZohoKeywordCheck | Prohibited keywords |
| `com.zoho.zide.PropStandardMarker` | PropertiesStandardCheck | .properties standards |
| `com.zoho.zide.SMCMarker` | SMCCheck | SMC compliance |
| `com.zoho.zide.HacksawMarker` | HacksawReportMarker | Security scan results |
| `com.zoho.zide.CustomCheckMarker` | CustomCheckMarker | User-defined checks |
| `com.zoho.zide.JUnitMarker` | JunitCheckMarker | JUnit test results |
| `com.zoho.zide.TPJSMarker` | (ThirdPartyJS) | Third-party JS detection |
| `com.zoho.zide.RepoMarker` | (Repository) | Repository restriction |

## Commit Check Configuration

`.zide/codecheck.conf` format:
```properties
i18n.excludes=
pmd.excludes=
dollarid.excludes=
```

Legal declaration template enforced in commit messages:
```
LEGAL DECLARATION:I hereby declare that the code submitted by me has not been copied
from any third party source without due verification of license terms and does not
infringe third party intellectual property rights. I also hereby declare that the code
does not contain any offensive or abusive content.
LICENSE COMPATIBLE:YES
```

## Launch Configuration Properties

| Key | Description |
|---|---|
| `launch.mainclass` | Java main class |
| `launch.vmarguments` | JVM arguments (-Xmx, -D flags, agent jars) |
| `launch.programarguments` | Program arguments |
| `launch.classpath` | Additional classpath entries |
| `launch.workingdirectory` | Working directory |
| `builder.antfile` | Ant build file path |
| `builder.targets` | Ant targets to run |
| `classpath.exclude.libraries` | Libraries to exclude from classpath |

## Config Replacer Variables

| Variable | Source |
|---|---|
| `{HOSTNAME}` | `ZIDE.HOST_NAME` from zide_properties.xml |
| `{HTTP_PORT}` | `ZIDE.HTTP_PORT` |
| `{HTTPS_PORT}` | `ZIDE.HTTPS_PORT` |
| `{IAM_URL}` | `ZIDE.IAM_SERVER` |
| `{IAM_SERVICENAME}` | `ZIDE.IAM_SERVICENAME` |
| `{DB_NAME}` | `ZIDE_DB_NAME` |
| `{SCHEMA_NAME}` | `ZIDE.SCHEMA_NAME` |
| `{USER_NAME}` | `ZIDE.USER_NAME` |
| `{USER_EMAIL}` | `ZIDE.USER_EMAIL` |
| `{MACHINE_IP}` | `ZIDE.MACHINE_IP` |

## Standard .gitignore / .hgignore Entries

**.gitignore** (written to `.git/info/exclude`):
```
.zide_resources/
deployment/
*.class
.classpath
.project
.settings/
bin/
```

**.hgignore** (with `re:` prefix for regexp syntax):
```
syntax: regexp
re:.zide_resources
re:deployment/
re:\.classpath
re:\.project
re:\.settings
re:bin/
```

## ServiceConfig Inner Classes

- **RepositoryConfig** — clone URL prefixes (HTTP/SSH/HTTPS), module dir, username, password, trunk
- **DeploymentConfig** — download URL, local URL, deployment folder path, isDeployable check
- **PathConfig** — libraries (named user libraries), library JARs, class libraries; reads from `zide_config.xml` or `Zide.properties` depending on war model
- **LaunchConfig** — VM args, main class, program args from `Zide.properties` `[launch]` section
- **BuilderConfig** — Ant build targets and properties from `Zide.properties` `[builder]` section

## Bundled Libraries

| Library | Version |
|---|---|
| mysql_connector.jar | (bundled) |
| postgresql-9.4-1201.jdbc41.jar | 9.4-1201 |
| commons-compress-1.0.jar | 1.0 |
| commons-io-2.11.0.jar | 2.11.0 |
| commons-lang3-3.12.0.jar | 3.12.0 |
| commons-text-1.10.0.jar | 1.10.0 |
| httpclient5-5.2.1.jar | 5.2.1 |
| httpcore5-5.2.1.jar | 5.2.1 |
| json.jar | (org.json) |
| slf4j-api-1.7.36.jar | 1.7.36 |
