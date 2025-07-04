/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.projects.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.ModuleName;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageDependencyScope;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.PackageManifest;
import io.ballerina.projects.PackageName;
import io.ballerina.projects.PackageOrg;
import io.ballerina.projects.PackageVersion;
import io.ballerina.projects.PlatformLibraryScope;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.ResolvedPackageDependency;
import io.ballerina.projects.SemanticVersion;
import io.ballerina.projects.Settings;
import io.ballerina.projects.environment.PackageLockingMode;
import io.ballerina.projects.internal.model.BuildJson;
import io.ballerina.projects.internal.model.Dependency;
import io.ballerina.projects.internal.model.ToolDependency;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.ballerinalang.compiler.BLangCompilerException;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.util.Lists;
import org.wso2.ballerinalang.util.RepoUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.ballerina.projects.util.FileUtils.getFileNameWithoutExtension;
import static io.ballerina.projects.util.ProjectConstants.ASM_COMMONS_JAR;
import static io.ballerina.projects.util.ProjectConstants.ASM_JAR;
import static io.ballerina.projects.util.ProjectConstants.ASM_TREE_JAR;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_HOME;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_HOME_BRE;
import static io.ballerina.projects.util.ProjectConstants.BALLERINA_TOML;
import static io.ballerina.projects.util.ProjectConstants.BLANG_COMPILED_JAR_EXT;
import static io.ballerina.projects.util.ProjectConstants.BLANG_COMPILED_PKG_BINARY_EXT;
import static io.ballerina.projects.util.ProjectConstants.BUILD_FILE;
import static io.ballerina.projects.util.ProjectConstants.CACHES_DIR_NAME;
import static io.ballerina.projects.util.ProjectConstants.DIFF_UTILS_JAR;
import static io.ballerina.projects.util.ProjectConstants.DIR_PATH_SEPARATOR;
import static io.ballerina.projects.util.ProjectConstants.DOT;
import static io.ballerina.projects.util.ProjectConstants.JACOCO_CORE_JAR;
import static io.ballerina.projects.util.ProjectConstants.JACOCO_REPORT_JAR;
import static io.ballerina.projects.util.ProjectConstants.LIB_DIR;
import static io.ballerina.projects.util.ProjectConstants.RESOURCE_DIR_NAME;
import static io.ballerina.projects.util.ProjectConstants.TARGET_DIR_NAME;
import static io.ballerina.projects.util.ProjectConstants.TEST_CORE_JAR_PREFIX;
import static io.ballerina.projects.util.ProjectConstants.TEST_RUNTIME_JAR_PREFIX;
import static io.ballerina.projects.util.ProjectConstants.TOOL_DIR;
import static io.ballerina.projects.util.ProjectConstants.USER_NAME;
import static io.ballerina.projects.util.ProjectConstants.WILD_CARD;

/**
 * Project related util methods.
 *
 * @since 2.0.0
 */
public final class ProjectUtils {

    private static final String USER_HOME = "user.home";
    private static final Pattern separatedIdentifierPattern = Pattern.compile("^[a-zA-Z0-9_.]*$");
    private static final Pattern onlyDotsPattern = Pattern.compile("^[.]+$");
    private static final Pattern onlyNonAlphanumericPattern = Pattern.compile("^[^a-zA-Z0-9]+$");
    private static final Pattern orgNamePattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    private static final Pattern separatedIdentifierWithHyphenPattern = Pattern.compile("^[a-zA-Z0-9_.-]*$");
    private static final List<Diagnostic> projectLoadingDiagnostic = new ArrayList<>();

    private ProjectUtils() {
    }

    /**
     * Validates the org-name.
     *
     * @param orgName The org-name
     * @return True if valid org-name or package name, else false.
     */
    public static boolean validateOrgName(String orgName) {
        Matcher m = orgNamePattern.matcher(orgName);
        return m.matches();
    }

    /**
     * Validates the package name.
     *
     * @param packageName The package name.
     * @return True if valid package name, else false.
     */
    public static boolean validatePackageName(String packageName) {
        return validateDotSeparatedIdentifiers(packageName)
                && validateUnderscoresOfName(packageName)
                && validateInitialNumericsOfName(packageName);
    }

    /**
     * Validates the package name.
     *
     * @param toolName The package name.
     * @return True if valid package name, else false.
     */
    public static boolean validateToolName(String toolName) {
        return validateDotSeparatedIdentifiersWithHyphen(toolName)
                && validateUnderscoresOfName(toolName)
                && validateInitialNumericsOfName(toolName);
    }

    /**
     * Validates the package name.
     *
     * @param orgName     The organization name.
     * @param packageName The package name.
     * @return True if valid package name, else false.
     */
    public static boolean validatePackageName(String orgName, String packageName) {
        if (isLangLibPackage(PackageOrg.from(orgName), PackageName.from(packageName))) {
            return validateDotSeparatedIdentifiers(packageName)
                    && validateInitialNumericsOfName(packageName);
        }
        return validateDotSeparatedIdentifiers(packageName)
                && validateUnderscoresOfName(packageName)
                && validateInitialNumericsOfName(packageName);
    }

    public static Set<String> getPackageImports(Package pkg) {
        Set<String> imports = new HashSet<>();
        for (ModuleId moduleId : pkg.moduleIds()) {
            Module module = pkg.module(moduleId);
            Collection<DocumentId> documentIds = module.documentIds();
            getPackageImports(imports, module, documentIds);
            Collection<DocumentId> testDocumentIds = module.testDocumentIds();
            getPackageImports(imports, module, testDocumentIds);
        }
        return imports;
    }

    private static void getPackageImports(Set<String> imports, Module module, Collection<DocumentId> documentIds) {
        for (DocumentId docId : documentIds) {
            Document document = module.document(docId);
            ModulePartNode modulePartNode = document.syntaxTree().rootNode();
            for (ImportDeclarationNode importDcl : modulePartNode.imports()) {
                boolean isErrorInImport = false;
                for (Diagnostic diagnostic : importDcl.diagnostics()) {
                    if (diagnostic.diagnosticInfo().severity() == DiagnosticSeverity.ERROR) {
                        isErrorInImport = true;
                        break;
                    }
                }
                if (isErrorInImport) {
                    continue;
                }
                String orgName = "";
                if (importDcl.orgName().isPresent()) {
                    orgName = importDcl.orgName().get().orgName().text();
                }
                SeparatedNodeList<IdentifierToken> identifierTokenList = importDcl.moduleName();
                StringJoiner stringJoiner = new StringJoiner(".");
                for (int i = 0; i < identifierTokenList.size(); i++) {
                    stringJoiner.add(identifierTokenList.get(i).text());
                }
                String moduleName = stringJoiner.toString();
                imports.add(orgName + "/" + moduleName);
            }
        }
    }

    /**
     * Validates the module name.
     *
     * @param moduleName The module name.
     * @return True if valid module name, else false.
     */
    public static boolean validateModuleName(String moduleName) {
        return validateDotSeparatedIdentifiers(moduleName);
    }

    /**
     * Validates the organization, package or module name length.
     * Maximum length is 256 characters.
     *
     * @param name name.
     * @return true if valid name length, else false.
     */
    public static boolean validateNameLength(String name) {
        return name.length() <= 256;
    }

    /**
     * Checks the organization, package or module name has initial, trailing or consecutive underscores.
     *
     * @param name name.
     * @return true if name does not have initial, trailing or consecutive underscores, else false.
     */
    public static boolean validateUnderscoresOfName(String name) {
        return !(name.startsWith("_") || name.endsWith("_") || name.contains("__"));
    }

    /**
     * Checks the organization, package or module name has initial numeric characters.
     *
     * @param name name.
     * @return true if name does not have initial numeric characters, else false.
     */
    public static boolean validateInitialNumericsOfName(String name) {
        return !name.matches("[0-9].*");
    }

    /**
     * Remove last character of the given string.
     *
     * @param aString given string
     * @return string removed last character
     */
    public static String removeLastChar(String aString) {
        return aString.substring(0, aString.length() - 1);
    }

    /**
     * Remove first character of the given string.
     *
     * @param aString given string
     * @return string removed last character
     */
    public static String removeFirstChar(String aString) {
        return aString.substring(1);
    }

    public static String getPackageValidationError(String packageName) {
        if (!validateDotSeparatedIdentifiers(packageName)) {
            return "Package name can only contain alphanumerics and underscores.";
        } else if (!validateInitialNumericsOfName(packageName)) {
            return "Package name cannot have initial numeric characters.";
        } else {
            return getValidateUnderscoreError(packageName, "Package");
        }
    }

    /**
     * Get specific error message when organization, package or module name has initial, trailing or
     * consecutive underscores.
     *
     * @param name            name.
     * @param packageOrModule package or module.
     * @return specific error message.
     */
    public static String getValidateUnderscoreError(String name, String packageOrModule) {
        if (name.startsWith("_")) {
            return packageOrModule + " name cannot have initial underscore characters.";
        } else if (name.endsWith("_")) {
            return packageOrModule + " name cannot have trailing underscore characters.";
        } else {
            return packageOrModule + " name cannot have consecutive underscore characters.";
        }
    }

    /**
     * Find the project root by recursively up to the root.
     *
     * @param filePath project path
     * @return project root
     */
    public static Path findProjectRoot(Path filePath) {
        if (filePath != null) {
            filePath = filePath.toAbsolutePath().normalize();
            if (filePath.toFile().isDirectory()) {
                if (Files.exists(filePath.resolve(BALLERINA_TOML))) {
                    return filePath;
                }
            }
            return findProjectRoot(filePath.getParent());
        }
        return null;
    }

    /**
     * Checks if the path is a Ballerina project.
     *
     * @param sourceRoot source root of the project.
     * @return true if the directory is a project repo, false if its the home repo
     */
    public static boolean isBallerinaProject(Path sourceRoot) {
        Path ballerinaToml = sourceRoot.resolve(BALLERINA_TOML);
        return Files.isDirectory(sourceRoot)
                && Files.exists(ballerinaToml)
                && Files.isRegularFile(ballerinaToml);
    }

    /**
     * Guess organization name based on user name in system.
     *
     * @return organization name
     */
    public static String guessOrgName() {
        String guessOrgName = System.getProperty(USER_NAME);
        if (guessOrgName == null) {
            guessOrgName = "my_org";
        } else {
            if (!validateOrgName(guessOrgName)) {
                guessOrgName =  guessOrgName.replaceAll("[^a-zA-Z0-9_]", "_");
            }
        }
        return guessOrgName.toLowerCase(Locale.getDefault());
    }

    /**
     * Guess package name with valid pattern.
     *
     * @param packageName package name
     * @param template    template name
     * @return package name
     */
    public static String guessPkgName(String packageName, String template) {
        if (!validateOnlyNonAlphanumeric(packageName)) {
            packageName = "my_package";
        }

        if (!validatePackageName(packageName)) {
            packageName = packageName.replaceAll("[^a-zA-Z0-9_.]", "_");
        }

        // if package name is starting with numeric character, prepend `app` / `lib` / `tool`
        if (packageName.matches("[0-9].*")) {
            if (template.equalsIgnoreCase(LIB_DIR)) {
                packageName = LIB_DIR + packageName;
            } else if (template.equalsIgnoreCase(TOOL_DIR)) {
                packageName = TOOL_DIR + packageName;
            }  else {
                packageName = "app" + packageName;
            }
        }

        // if package name is starting with underscore remove it
        if (packageName.startsWith("_")) {
            packageName = removeFirstChar(packageName);
        }

        // if package name has consecutive underscores, replace them with a single underscore
        if (packageName.contains("__")) {
            packageName = packageName.replace("__", "_");
        }

        // if package name has trailing underscore remove it
        if (packageName.endsWith("_")) {
            packageName = removeLastChar(packageName);
        }
        return packageName;
    }

    /**
     * Guess module name with valid pattern.
     *
     * @param moduleName module name
     * @return module name
     */
    public static String guessModuleName(String moduleName) {
        if (!validateModuleName(moduleName)) {
            return moduleName.replaceAll("[^a-zA-Z0-9_.]", "_");
        }
        return moduleName;
    }

    public static PackageOrg defaultOrg() {
        return PackageOrg.from(guessOrgName());
    }

    public static PackageName defaultName(Path projectPath) {
        return PackageName.from(guessPkgName(Optional.ofNullable(projectPath.getFileName())
                .map(Path::toString).orElse(""), "app"));
    }

    public static PackageVersion defaultVersion() {
        return PackageVersion.from(ProjectConstants.INTERNAL_VERSION);
    }

    public static String getBalaName(PackageManifest pkgDesc) {
        return ProjectUtils.getBalaName(pkgDesc.org().toString(),
                                        pkgDesc.name().toString(),
                                        pkgDesc.version().toString(),
                                        null
        );
    }

    public static String getBalaName(String org, String pkgName, String version, String platform) {
        // <orgname>-<packagename>-<platform>-<version>.bala
        if (platform == null || platform.isEmpty()) {
            platform = "any";
        }
        return org + "-" + pkgName + "-" + platform + "-" + version + BLANG_COMPILED_PKG_BINARY_EXT;
    }

    /**
     * Returns the relative path of extracted bala beginning from the package org.
     *
     * @param org package org
     * @param pkgName package name
     * @param version package version
     * @param platform version, null converts to `any`
     * @return relative bala path
     */
    public static Path getRelativeBalaPath(String org, String pkgName, String version, String platform) {
        // <orgname>-<packagename>-<platform>-<version>.bala
        if (platform == null || platform.isEmpty()) {
            platform = "any";
        }
        return Path.of(org, pkgName, version, platform);
    }

    public static String getJarFileName(Package pkg) {
        // <orgname>-<packagename>-<version>.jar
        return pkg.packageOrg().toString() + "-" + pkg.packageName().toString()
                + "-" + pkg.packageVersion() + BLANG_COMPILED_JAR_EXT;
    }

    public static String getExecutableName(Package pkg) {
        // <packagename>.jar
        return pkg.packageName().toString() + BLANG_COMPILED_JAR_EXT;
    }

    public static String getOrgFromBalaName(String balaName) {
        return balaName.split("-")[0];
    }

    public static String getPackageNameFromBalaName(String balaName) {
        return balaName.split("-")[1];
    }

    public static String getVersionFromBalaName(String balaName) {
        // TODO validate this method of getting the version of the bala
        String versionAndExtension = balaName.split("-")[3];
        int extensionIndex = versionAndExtension.indexOf(BLANG_COMPILED_PKG_BINARY_EXT);
        return versionAndExtension.substring(0, extensionIndex);
    }

    private static final HashSet<String> excludeExtensions = new HashSet<>(Lists.of("DSA", "SF"));

    public static Path getBalHomePath() {
        return Path.of(System.getProperty(BALLERINA_HOME));
    }

    public static Path getBallerinaRTJarPath() {
        String ballerinaVersion = RepoUtils.getBallerinaPackVersion();
        String runtimeJarName = "ballerina-rt-" + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        return getBalHomePath().resolve("bre").resolve("lib").resolve(runtimeJarName);
    }

    public static List<JarLibrary> testDependencies() {
        List<JarLibrary> dependencies = new ArrayList<>();
        String testPkgName = "ballerina/test";

        String ballerinaVersion = RepoUtils.getBallerinaPackVersion();
        Path homeLibPath = getBalHomePath().resolve(BALLERINA_HOME_BRE).resolve(LIB_DIR);
        String testRuntimeJarName = TEST_RUNTIME_JAR_PREFIX + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        String testCoreJarName = TEST_CORE_JAR_PREFIX + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        String langJarName = "ballerina-lang-" + ballerinaVersion + BLANG_COMPILED_JAR_EXT;

        Path testRuntimeJarPath = homeLibPath.resolve(testRuntimeJarName);
        Path testCoreJarPath = homeLibPath.resolve(testCoreJarName);
        Path langJarPath = homeLibPath.resolve(langJarName);
        Path jacocoCoreJarPath = homeLibPath.resolve(JACOCO_CORE_JAR);
        Path jacocoReportJarPath = homeLibPath.resolve(JACOCO_REPORT_JAR);
        Path asmJarPath = homeLibPath.resolve(ASM_JAR);
        Path asmTreeJarPath = homeLibPath.resolve(ASM_TREE_JAR);
        Path asmCommonsJarPath = homeLibPath.resolve(ASM_COMMONS_JAR);
        Path diffUtilsJarPath = homeLibPath.resolve(DIFF_UTILS_JAR);

        dependencies.add(new JarLibrary(testRuntimeJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(testCoreJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(langJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(jacocoCoreJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(jacocoReportJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmTreeJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(asmCommonsJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        dependencies.add(new JarLibrary(diffUtilsJarPath, PlatformLibraryScope.TEST_ONLY, testPkgName));
        return dependencies;
    }

    public static Path generateObservabilitySymbolsJar(String packageName) throws IOException {
        Path jarPath = Files.createTempFile(packageName + "-", "-observability-symbols.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(jarPath.toFile())), manifest);
        jarOutputStream.close();
        return jarPath;
    }

    /**
     * Copies a given jar file into the executable fat jar.
     *
     * @param ballerinaRTJarPath Ballerina runtime jar path.
     * @throws IOException If jar file copying is failed.
     */
    public static void copyRuntimeJar(ZipArchiveOutputStream outStream,
                                      Path ballerinaRTJarPath,
                                      HashSet<String> copiedEntries) throws IOException {
        // TODO This code is copied from the current executable jar creation logic. We may need to refactor this.
        HashMap<String, StringBuilder> services = new HashMap<>();
        ZipFile zipFile = new ZipFile(ballerinaRTJarPath.toString());
        ZipArchiveEntryPredicate predicate = entry -> {

            String entryName = entry.getName();
            if (entryName.equals("META-INF/MANIFEST.MF")) {
                return false;
            }

            if (entryName.startsWith("META-INF/services")) {
                StringBuilder s = services.get(entryName);
                if (s == null) {
                    s = new StringBuilder();
                    services.put(entryName, s);
                }
                char c = '\n';

                int len;
                try (BufferedInputStream inStream = new BufferedInputStream(zipFile.getInputStream(entry))) {
                    while ((len = inStream.read()) != -1) {
                        c = (char) len;
                        s.append(c);
                    }
                } catch (IOException e) {
                    throw new ProjectException(e);
                }
                if (c != '\n') {
                    s.append('\n');
                }

                // Its not required to copy SPI entries in here as we'll be adding merged SPI related entries
                // separately. Therefore the predicate should be set as false.
                return false;
            }

            // Skip already copied files or excluded extensions.
            if (isCopiedOrExcludedEntry(entryName, copiedEntries)) {
                return false;
            }
            // SPIs will be merged first and then put into jar separately.
            copiedEntries.add(entryName);
            return true;
        };

        // Transfers selected entries from this zip file to the output stream, while preserving its compression and
        // all the other original attributes.
        zipFile.copyRawEntries(outStream, predicate);
        zipFile.close();

        for (Map.Entry<String, StringBuilder> entry : services.entrySet()) {
            String s = entry.getKey();
            StringBuilder service = entry.getValue();
            JarArchiveEntry e = new JarArchiveEntry(s);
            outStream.putArchiveEntry(e);
            outStream.write(service.toString().getBytes(StandardCharsets.UTF_8));
            outStream.closeArchiveEntry();
        }
    }

    private static boolean isCopiedOrExcludedEntry(String entryName, HashSet<String> copiedEntries) {
        return copiedEntries.contains(entryName) ||
                excludeExtensions.contains(entryName.substring(entryName.lastIndexOf(".") + 1));
    }

    /**
     * Construct and return the thin jar name of the provided module.
     *
     * @param module Module instance
     * @return the name of the thin jar
     */
    public static String getJarFileName(Module module) {
        String jarName;
        if (module.packageInstance().manifest().org().anonymous()) {
            DocumentId documentId = module.documentIds().iterator().next();
            String documentName = module.document(documentId).name();
            jarName = getFileNameWithoutExtension(documentName);
        } else {
            ModuleName moduleName = module.moduleName();
            if (moduleName.isDefaultModuleName()) {
                jarName = moduleName.packageName().toString();
            } else {
                jarName = moduleName.moduleNamePart();
            }
        }
        return jarName;
    }

    /**
     * Construct and return the thin jar moduleName.
     *
     * @param org        organization
     * @param moduleName module name
     * @param version    version
     * @return the moduleName of the thin jar
     */
    public static String getThinJarFileName(PackageOrg org, String moduleName, PackageVersion version) {
        return org.value() + "-" + moduleName + "-" + version.value();
    }

    /**
     * Create and get the home repository path.
     *
     * @return home repository path
     */
    public static Path createAndGetHomeReposPath() {
        Path homeRepoPath;
        String homeRepoDir = System.getenv(ProjectConstants.HOME_REPO_ENV_KEY);
        if (homeRepoDir == null || homeRepoDir.isEmpty()) {
            String userHomeDir = System.getProperty(USER_HOME);
            if (userHomeDir == null || userHomeDir.isEmpty()) {
                throw new BLangCompilerException("Error creating home repository: unable to get user home directory");
            }
            homeRepoPath = Path.of(userHomeDir, ProjectConstants.HOME_REPO_DEFAULT_DIRNAME);
        } else {
            // User has specified the home repo path with env variable.
            homeRepoPath = Path.of(homeRepoDir);
        }

        homeRepoPath = homeRepoPath.toAbsolutePath();
        if (Files.exists(homeRepoPath) && !Files.isDirectory(homeRepoPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new BLangCompilerException("Home repository is not a directory: " + homeRepoPath);
        }
        return homeRepoPath;
    }

    /**
     * Check if a ballerina module exist.
     * @param projectPath project path
     * @param moduleName module name
     * @return module exist
     */
    public static boolean isModuleExist(Path projectPath, String moduleName) {
        Path modulePath = projectPath.resolve(ProjectConstants.MODULES_ROOT).resolve(moduleName);
        return Files.exists(modulePath);
    }

    /**
     * Initialize proxy if proxy is available in settings.toml.
     *
     * @param proxy toml model proxy
     * @return proxy
     */
    public static Proxy initializeProxy(io.ballerina.projects.internal.model.Proxy proxy) {
        if (proxy != null && !"".equals(proxy.host()) && proxy.port() > 0) {
            InetSocketAddress proxyInet = new InetSocketAddress(proxy.host(), proxy.port());
            return new Proxy(Proxy.Type.HTTP, proxyInet);
        }
        return null;
    }

    /**
     * Read the access token generated for the CLI.
     *
     * @return access token for generated for the CLI
     */
    public static String getAccessTokenOfCLI(Settings settings) {
        // The access token can be specified as an environment variable or in 'Settings.toml'. First we would check if
        // the access token was specified as an environment variable. If not we would read it from 'Settings.toml'
        String tokenAsEnvVar = System.getenv(ProjectConstants.BALLERINA_CENTRAL_ACCESS_TOKEN);
        if (tokenAsEnvVar != null) {
            return tokenAsEnvVar;
        }
        if (settings.getCentral() != null) {
            return settings.getCentral().getAccessToken();
        }
        return "";
    }

    public static void checkWritePermission(Path path) {
        if (!path.toFile().canWrite()) {
            throw new ProjectException("'" + path.normalize() + "' does not have write permissions");
        }
    }

    public static void checkReadPermission(Path path) {
        if (!path.toFile().canRead()) {
            throw new ProjectException("'" + path.normalize() + "' does not have read permissions");
        }
    }

    public static void checkExecutePermission(Path path) {
        if (!path.toFile().canExecute()) {
            throw new ProjectException("'" + path.normalize() + "' does not have execute permissions");
        }
    }

    private static boolean validateDotSeparatedIdentifiers(String identifiers) {
        Matcher m = separatedIdentifierPattern.matcher(identifiers);
        Matcher mm = onlyDotsPattern.matcher(identifiers);

        return m.matches() && !mm.matches();
    }

    private static boolean validateDotSeparatedIdentifiersWithHyphen(String identifiers) {
        Matcher m = separatedIdentifierWithHyphenPattern.matcher(identifiers);
        Matcher mm = onlyDotsPattern.matcher(identifiers);

        return m.matches() && !mm.matches();
    }

    private static boolean validateOnlyNonAlphanumeric(String identifiers) {
        Matcher m = onlyNonAlphanumericPattern.matcher(identifiers);

        return !m.matches();
    }

    /**
     * Get `Dependencies.toml` content as a string.
     *
     * @param pkgGraphDependencies    direct dependencies of the package dependency graph
     * @return Dependencies.toml` content
     */
    public static String getDependenciesTomlContent(Collection<ResolvedPackageDependency> pkgGraphDependencies) {
        String comment = """
                # AUTO-GENERATED FILE. DO NOT MODIFY.

                # This file is auto-generated by Ballerina for managing dependency versions.
                # It should not be modified by hand.

                """;
        StringBuilder content = new StringBuilder(comment);
        content.append("[ballerina]\n");
        content.append("version = \"").append(RepoUtils.getBallerinaShortVersion()).append("\"\n");
        content.append("dependencies-toml-version = \"").append(ProjectConstants.DEPENDENCIES_TOML_VERSION)
                .append("\"\n");

        // write dependencies from package dependency graph
        pkgGraphDependencies.forEach(graphDependency -> {
            content.append("\n");
            PackageDescriptor descriptor = graphDependency.packageInstance().descriptor();
            addDependencyContent(content, descriptor.org().value(), descriptor.name().value(),
                                 descriptor.version().value().toString(), null, Collections.emptyList(),
                                 Collections.emptyList());
        });
        return String.valueOf(content);
    }

    /**
     * Get `Dependencies.toml` content as a string.
     *
     * @param pkgDependencies       direct dependencies of the package dependency graph
     * @return Dependencies.toml` content
     */
    public static String getDependenciesTomlContent(List<Dependency> pkgDependencies,
                                                    List<ToolDependency> toolDependencies) {
        String comment = """
                # AUTO-GENERATED FILE. DO NOT MODIFY.

                # This file is auto-generated by Ballerina for managing dependency versions.
                # It should not be modified by hand.

                """;
        StringBuilder content = new StringBuilder(comment);
        content.append("[ballerina]\n");
        content.append("dependencies-toml-version = \"").append(ProjectConstants.DEPENDENCIES_TOML_VERSION)
                .append("\"\n");
        content.append("distribution-version = \"").append(RepoUtils.getBallerinaShortVersion()).append("\"\n");

        // write dependencies from package dependency graph
        pkgDependencies.forEach(dependency -> {
            content.append("\n");
            addDependencyContent(content, dependency.getOrg(), dependency.getName(), dependency.getVersion(),
                                 getDependencyScope(dependency.getScope()), dependency.getDependencies(),
                                 dependency.getModules());
        });

        // write tool dependencies
        toolDependencies.forEach(toolDependency -> {
            content.append("\n");
            addToolDependencyContent(
                    content,
                    toolDependency.getId(),
                    toolDependency.getOrg(),
                    toolDependency.getName(),
                    toolDependency.getVersion());
        });
        return String.valueOf(content);
    }

    private static void addDependencyContent(StringBuilder content, String org, String name, String version,
                                             String scope, List<Dependency> dependencies,
                                             List<Dependency.Module> modules) {
        content.append("[[package]]\n");
        content.append("org = \"").append(org).append("\"\n");
        content.append("name = \"").append(name).append("\"\n");
        content.append("version = \"").append(version).append("\"\n");
        if (scope != null) {
            content.append("scope = \"").append(scope).append("\"\n");
        }

        // write dependencies
        if (!dependencies.isEmpty()) {
            var count = 1;
            content.append("dependencies = [\n");
            for (Dependency transDependency : dependencies) {
                content.append("\t{");
                content.append("org = \"").append(transDependency.getOrg()).append("\", ");
                content.append("name = \"").append(transDependency.getName()).append("\"");
                content.append("}");

                if (count != dependencies.size()) {
                    content.append(",\n");
                } else {
                    content.append("\n");
                }
                count++;
            }
            content.append("]\n");
        }

        // write modules
        if (!modules.isEmpty()) {
            var count = 1;
            content.append("modules = [\n");
            for (Dependency.Module module : modules) {
                content.append("\t{");
                content.append("org = \"").append(module.org()).append("\", ");
                content.append("packageName = \"").append(module.packageName()).append("\", ");
                content.append("moduleName = \"").append(module.moduleName()).append("\"");
                content.append("}");

                if (count != modules.size()) {
                    content.append(",\n");
                } else {
                    content.append("\n");
                }
                count++;
            }
            content.append("]\n");
        }
    }

    private static void addToolDependencyContent(
            StringBuilder content,
            String id,
            String org,
            String name,
            String version) {
        content.append("[[tool]]\n");
        content.append("id = \"").append(id).append("\"\n");
        content.append("org = \"").append(org).append("\"\n");
        content.append("name = \"").append(name).append("\"\n");
        content.append("version = \"").append(version).append("\"\n");
    }

    private static String getDependencyScope(PackageDependencyScope scope) {
        if (scope == PackageDependencyScope.TEST_ONLY) {
            return "testOnly";
        }
        return null;
    }

    public static List<PackageName> getPossiblePackageNames(PackageOrg packageOrg, String moduleName) {
        var pkgNameBuilder = new StringJoiner(".");

        // If built in package, return moduleName as it is
        if (isBuiltInPackage(packageOrg, moduleName)) {
            pkgNameBuilder.add(moduleName);
            return Collections.singletonList(PackageName.from(pkgNameBuilder.toString()));
        }

        String[] modNameParts = moduleName.split("\\.");
        List<PackageName> possiblePkgNames = new ArrayList<>(modNameParts.length);
        for (String modNamePart : modNameParts) {
            pkgNameBuilder.add(modNamePart);
            possiblePkgNames.add(PackageName.from(pkgNameBuilder.toString()));
        }
        return possiblePkgNames;
    }

    public static boolean isBuiltInPackage(PackageOrg org, String moduleName) {
        return (org.isBallerinaOrg() && moduleName.startsWith("lang.")) ||
                (org.value().equals(Names.BALLERINA_INTERNAL_ORG.getValue())) ||
                (org.isBallerinaOrg() && moduleName.equals(Names.JAVA.getValue())) ||
                (org.isBallerinaOrg() && moduleName.equals(Names.TEST.getValue()));
    }

    public static boolean isLangLibPackage(PackageOrg org, PackageName packageName) {
        return (org.isBallerinaOrg() && packageName.value().startsWith("lang.")) ||
                (org.isBallerinaOrg() && packageName.value().equals(Names.JAVA.getValue()));
    }

    /**
     * Extracts a .bala file into the provided destination directory.
     *
     * @param balaFilePath .bala file path
     * @param balaFileDestPath directory into which the .bala should be extracted
     * @throws IOException if extraction fails
     */
    public static void extractBala(Path balaFilePath, Path balaFileDestPath) throws IOException {
        if (Files.exists(balaFileDestPath) && Files.isDirectory(balaFilePath)) {
            deleteDirectory(balaFileDestPath);
        } else {
            Files.createDirectories(balaFileDestPath);
        }

        byte[] buffer = new byte[1024 * 4];
        try (FileInputStream fileInputStream = new FileInputStream(balaFilePath.toString())) {
            // Get the zip file content.
            try (ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
                // Get the zipped file entry.
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                while (zipEntry != null) {
                    // Get the name.
                    String fileName = zipEntry.getName();
                    // Construct the output file.
                    Path outputPath = balaFileDestPath.resolve(fileName);
                    // If the zip entry is for a directory, we create the directory and continue with the next entry.
                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(outputPath);
                        zipEntry = zipInputStream.getNextEntry();
                        continue;
                    }

                    // Create all non-existing directories.
                    Files.createDirectories(Optional.of(outputPath.getParent()).get());
                    // Create a new file output stream.
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                        // Write the content from zip input stream to the file output stream.
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    }
                    // Continue with the next entry.
                    zipEntry = zipInputStream.getNextEntry();
                }
                // Close zip input stream.
                zipInputStream.closeEntry();
            }
        }
    }

    /**
     * Delete the given directory along with all files and sub directories.
     *
     * @param directoryPath Directory to delete.
     */
    public static boolean deleteDirectory(Path directoryPath) {
        File directory = new File(String.valueOf(directoryPath));
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    boolean success = deleteDirectory(f.toPath());
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return directory.delete();
    }

    /**
     * Delete the all contents in the given directory except for selected files.
     *
     * @param directoryPath Directory to delete.
     * @param filesToKeep files to keep.
     */
    public static boolean deleteSelectedFilesInDirectory(Path directoryPath, List<Path> filesToKeep) {
        if (filesToKeep.isEmpty()) {
            return deleteDirectory(directoryPath);
        }
        File directory = new File(String.valueOf(directoryPath));
        File[] files = directory.listFiles();
        boolean success = true;
        if (files != null) {
            for (File f : files) {
                if (!filesToKeep.contains(f.toPath()) && f.isDirectory()) {
                    success = deleteDirectory(f.toPath());
                } else if (!filesToKeep.contains(f.toPath()) && f.isFile()) {
                    success = f.delete();
                }

            }
            return success;
        }
        return true;
    }

    /**
     * Read build file from given path.
     *
     * @param buildJsonPath build file path
     * @return build json object
     * @throws JsonSyntaxException incorrect json syntax
     * @throws IOException if json read fails
     */
    public static BuildJson readBuildJson(Path buildJsonPath) throws JsonSyntaxException, IOException {
        try (BufferedReader bufferedReader = Files.newBufferedReader(buildJsonPath)) {
            return new Gson().fromJson(bufferedReader, BuildJson.class);
        }
    }

    /**
     * Check project files are updated.
     *
     * @param project project instance
     * @return is project files are updated
     */
    public static boolean isProjectUpdated(Project project) {
        // If observability included and Syntax Tree Json not in the caches, return true
        Path observeJarCachePath = project.targetDir()
                .resolve(CACHES_DIR_NAME)
                .resolve(project.currentPackage().packageOrg().value())
                .resolve(project.currentPackage().packageName().value())
                .resolve(project.currentPackage().packageVersion().value().toString())
                .resolve("observe")
                .resolve(project.currentPackage().packageOrg().value() + "-"
                        + project.currentPackage().packageName().value()
                        + "-observability-symbols.jar");
        if (project.buildOptions().observabilityIncluded() &&
                !observeJarCachePath.toFile().exists()) {
            return true;
        }

        Path buildFile = project.sourceRoot().resolve(TARGET_DIR_NAME).resolve(BUILD_FILE);
        if (buildFile.toFile().exists()) {
            try {
                BuildJson buildJson = readBuildJson(buildFile);
                long lastProjectUpdatedTime = FileUtils.lastModifiedTimeOfBalProject(project.sourceRoot());
                if (buildJson != null
                        && buildJson.getLastModifiedTime() != null
                        && !buildJson.getLastModifiedTime().entrySet().isEmpty()) {
                    Long defaultModuleLastModifiedTime = buildJson.getLastModifiedTime()
                            .get(project.currentPackage().packageName().value());
                    if (defaultModuleLastModifiedTime == null) {
                        // package name has changed
                        return true;
                    }
                    return lastProjectUpdatedTime > defaultModuleLastModifiedTime;
                }
            } catch (IOException e) {
                // if reading `build` file fails
                // delete `build` file and return true
                try {
                    Files.deleteIfExists(buildFile);
                } catch (IOException ex) {
                    // ignore
                }
                return true;
            }
        }
        return true; // return true if `build` file does not exist
    }

    /**
     * Get temporary target path.
     *
     * @return temporary target path
     */
    public static String getTemporaryTargetPath() {
        return Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("ballerina-cache" + System.nanoTime()).toString();
    }

    /**
     * Write build file from given object.
     *
     * @param buildFilePath build file path
     * @param buildJson     BuildJson object
     */
    public static void writeBuildFile(Path buildFilePath, BuildJson buildJson) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Check write permissions
        if (!buildFilePath.toFile().canWrite()) {
            throw new ProjectException("'build' file does not have write permissions");
        }

        // write build file
        try {
            Files.write(buildFilePath, Collections.singleton(gson.toJson(buildJson)));
        } catch (IOException e) {
            throw new ProjectException("Failed to write to the '" + BUILD_FILE + "' file");
        }
    }

    /**
     * Compare and get latest of two package versions.
     *
     * @param v1 package version 1
     * @param v2 package version 2
     * @return latest package version from given two package versions
     */
    public static PackageVersion getLatest(PackageVersion v1, PackageVersion v2) {
        SemanticVersion semVer1 = v1.value();
        SemanticVersion semVer2 = v2.value();
        boolean isV1PreReleaseVersion = semVer1.isPreReleaseVersion();
        boolean isV2PreReleaseVersion = semVer2.isPreReleaseVersion();
        if (isV1PreReleaseVersion ^ isV2PreReleaseVersion) {
            // Only one version is a pre-release version
            // Return the version which is not a pre-release version
            return isV1PreReleaseVersion ? v2 : v1;
        } else {
            // Both versions are pre-release versions or both are not pre-release versions
            // Find the latest version
            return semVer1.greaterThanOrEqualTo(semVer2) ? v1 : v2;
        }
    }

    /**
     * Checks if a given project does not contain ballerina source files or test files.
     *
     * @param project project for checking for emptiness
     * @return true if the project is empty
     */
    public static boolean isProjectEmpty(Project project) {
        for (ModuleId moduleId : project.currentPackage().moduleIds()) {
            Module module = project.currentPackage().module(moduleId);
            if (!module.documentIds().isEmpty() || !module.testDocumentIds().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Given a list of patterns in include field, find the directories and files in the package that match the patterns.
     *
     * @param patterns list of string patterns to be matched
     * @param packageRoot package root
     * @return the list of matching paths
     */
    public static List<Path> getPathsMatchingIncludePatterns(List<String> patterns, Path packageRoot) {
        List<Path> allMatchingPaths = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern.startsWith("!")) {
                removeNegatedIncludePaths(pattern.substring(1), allMatchingPaths);
            } else {
                addMatchingIncludePaths(pattern, allMatchingPaths, packageRoot);
            }
        }
        return allMatchingPaths;
    }

    public static boolean isNewUpdateDistribution(SemanticVersion prevDistributionVersion,
                                            SemanticVersion currentDistributionVersion) {
        return currentDistributionVersion.major() == prevDistributionVersion.major()
                && currentDistributionVersion.minor() > prevDistributionVersion.minor();
    }

    private static void removeNegatedIncludePaths(String pattern, List<Path> allMatchingPaths) {
        String combinedPattern = getGlobFormatPattern(pattern);
        Stream<Path> pathStream = allMatchingPaths.stream();
        List<Path> patternPaths = filterPathStream(pathStream, combinedPattern);
        allMatchingPaths.removeAll(patternPaths);
    }

    private static void addMatchingIncludePaths(String pattern, List<Path> allMatchingPaths, Path packageRoot) {
        String combinedPattern = getGlobFormatPattern(pattern);
        try (Stream<Path> pathStream = Files.walk(packageRoot)) {
            List<Path> patternPaths = filterPathStream(pathStream, combinedPattern);
            for (Path absolutePath : patternPaths) {
                if (isCorrectPatternPathMatch(absolutePath, packageRoot, pattern)) {
                    Path relativePath = packageRoot.relativize(absolutePath);
                    allMatchingPaths.add(relativePath);
                }
            }
        } catch (IOException e) {
            throw new ProjectException("Failed to read files matching the include pattern '" + pattern + "': " +
                    e.getMessage(), e);
        }
    }

    private static boolean isCorrectPatternPathMatch(Path absolutePath, Path packageRoot, String pattern) {
        Path relativePath = packageRoot.relativize(absolutePath);
        boolean correctMatch = true;
        if (relativePath.startsWith(TARGET_DIR_NAME)) {
            // ignore paths inside target directory
            correctMatch = false;
        } else if (pattern.startsWith("/") && !packageRoot.equals(absolutePath.getParent())) {
            // ignore non-root level paths if the pattern is root directory only
            correctMatch = false;
        } else if (pattern.endsWith("/") && absolutePath.toFile().isFile()) {
            // ignore files if the pattern is directory only
            correctMatch = false;
        }
        return correctMatch;
    }

    private static List<Path> filterPathStream(Stream<Path> pathStream, String combinedPattern) {
        return pathStream.filter(
                        FileSystems.getDefault().getPathMatcher("glob:" + combinedPattern)::matches)
                .toList();
    }

    private static String getGlobFormatPattern(String pattern) {
        String patternPrefix = getPatternPrefix(pattern);
        String globPattern = removeTrailingSlashes(pattern);
        return patternPrefix + globPattern;
    }

    private static String getPatternPrefix(String pattern) {
        // if the pattern already contains '/', only "**" should be added for the glob to work.
        if (pattern.startsWith("/")) {
            return "**";
        }
        return "**/";
    }

    private static String removeTrailingSlashes(String pattern) {
        while (pattern.endsWith("/")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }
        return pattern;
    }

    /**
     * Return the path of a bala with the available platform directory (java21 or any).
     *
     * @param balaDirPath path to the bala directory
     * @param org org name of the bala
     * @param name package name of the bala
     * @param version version of the bala
     * @return path of the bala file
     */
    public static Path getPackagePath(Path balaDirPath, String org, String name, String version) {
        //First we will check for a bala that match any platform
        Path balaPath = balaDirPath.resolve(
                ProjectUtils.getRelativeBalaPath(org, name, version, null));
        if (!Files.exists(balaPath)) {
            for (JvmTarget jvmTarget : JvmTarget.values()) {
                balaPath = balaDirPath.resolve(ProjectUtils.getRelativeBalaPath(org, name, version, jvmTarget.code()));
                if (Files.exists(balaPath)) {
                    break;
                }
            }
        }
        return balaPath;
    }

    /**
     * Get the sticky status of a project.
     *
     * @param project project instance
     * @return true if the project is sticky, false otherwise
     */
    public static boolean getSticky(Project project) {
        boolean sticky = project.buildOptions().sticky();
        if (sticky) {
            return true;
        }

        // set sticky only if `build` file exists and `last_update_time` not passed 24 hours
        if (project.kind() == ProjectKind.BUILD_PROJECT) {
            Path buildFilePath = project.targetDir().resolve(BUILD_FILE);
            if (Files.exists(buildFilePath) && buildFilePath.toFile().length() > 0) {
                try {
                    BuildJson buildJson = readBuildJson(buildFilePath);
                    // if distribution is not same, we anyway return sticky as false
                    if (buildJson != null && buildJson.distributionVersion() != null &&
                            buildJson.distributionVersion().equals(RepoUtils.getBallerinaShortVersion()) &&
                            !buildJson.isExpiredLastUpdateTime()) {
                        return true;
                    }
                } catch (IOException | JsonSyntaxException e) {
                    // ignore
                }
            }
        }
        return false;
    }

    /**
     * From a list of versions, get the versions within the compatible range.
     *
     * @param minVersion minimum compatible version
     * @param versions all versions available
     * @param compatibleRange compatibility range
     * @return compatible versions
     */
    public static List<SemanticVersion> getVersionsInCompatibleRange(
            SemanticVersion minVersion,
            List<SemanticVersion> versions,
            CompatibleRange compatibleRange) {
        if (compatibleRange.equals(CompatibleRange.LATEST)) {
            // If minVersion is null, range is LATEST
            return versions;
        }
        if (compatibleRange.equals(CompatibleRange.LOCK_MAJOR)) {
            return versions.stream().filter(version ->
                    version.major() == minVersion.major() && version.greaterThanOrEqualTo(minVersion)).toList();
        }
        if (compatibleRange.equals(CompatibleRange.LOCK_MINOR)) {
            return versions.stream().filter(version ->
                            version.major() == minVersion.major() && version.minor() == minVersion.minor()
                                    && version.greaterThanOrEqualTo(minVersion)).toList();
        }
        if (versions.contains(minVersion)) {
            return Collections.singletonList(minVersion);
        }
        return Collections.emptyList();
    }

    /**
     * Get the range of version compatibility of a given project.
     *
     * @param version minimum compatible version
     * @param packageLockingMode locking mode of the project
     * @return compatible range
     */
    public static CompatibleRange getCompatibleRange(SemanticVersion version, PackageLockingMode packageLockingMode) {
        if (version == null) {
            return CompatibleRange.LATEST;
        }
        if (packageLockingMode.equals(PackageLockingMode.HARD)) {
            return CompatibleRange.EXACT;
        }
        if (packageLockingMode.equals(PackageLockingMode.MEDIUM) || version.isInitialVersion()) {
            return CompatibleRange.LOCK_MINOR;
        }
        // Locking mode SOFT
        return CompatibleRange.LOCK_MAJOR;
    }

    public static Map<String, byte[]> getAllGeneratedResources(Path generatedResourcesPath) {
        Map<String, byte[]> resourcesMap = new HashMap<>();
        if (Files.isDirectory(generatedResourcesPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                    generatedResourcesPath, Files::isRegularFile)) {
                for (Path entry : stream) {
                    Path entryName = entry.getFileName();
                    if (entryName == null) {
                        continue;
                    }
                    String resourcePath = RESOURCE_DIR_NAME + DIR_PATH_SEPARATOR + entryName.toString();
                    resourcesMap.put(resourcePath, Files.readAllBytes(entry));
                }
            } catch (IOException e) {
                throw new ProjectException("An error occurred while reading the cached resources from: " +
                        generatedResourcesPath, e);
            }
        }

        return resourcesMap;
    }

    public static String getConflictingResourcesMsg(String packageDesc, List<String> conflictingResourceFiles) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("failed due to generated resources conflicting with the " +
                "resources in the current package '").append(packageDesc).append("'. Conflicting resource files:");
        if (conflictingResourceFiles != null && !conflictingResourceFiles.isEmpty()) {
            for (String file : conflictingResourceFiles) {
                errorMessage.append("\n").append(file);
            }
        }
        return errorMessage.toString();
    }

    public static String getResourcesPath() {
        return "'" + RESOURCE_DIR_NAME + DIR_PATH_SEPARATOR +
                DOT + WILD_CARD + "'";
    }

    /**
     * Denote the compatibility range of a given tool version.
     */
    public enum CompatibleRange {
        /**
         * Latest stable (if any), else latest pre-release.
         */
        LATEST,
        /**
         * Latest minor version of the locked major version.
         */
        LOCK_MAJOR,
        /**
         * Latest patch version of the locked major and minor versions.
         */
        LOCK_MINOR,
        /**
         * Exact version provided.
         */
        EXACT
    }

    // TODO: Remove this with https://github.com/ballerina-platform/ballerina-lang/issues/43212
    //  once diagnostic support for project loading stage is added.
    public static void addMiscellaneousProjectDiagnostics(Diagnostic diagnosticMessage) {
        projectLoadingDiagnostic.add(diagnosticMessage);
    }

    public static List<Diagnostic> getProjectLoadingDiagnostic() {
        return projectLoadingDiagnostic;
    }

    // This is needed to clear the diagnostics when unit testing
    public static void clearDiagnostics() {
        projectLoadingDiagnostic.clear();
    }

    /**
     * Checks if there are any services in the default module of the project.
     *
     * @param pkg package instance
     * @return true if there are services in the default module, false otherwise
     */
    public static boolean containsDefaultModuleService(Package pkg) {
        // Here, we are looking at the services only in the default module, since they are run during a bal run.
        // However, we can extend this to look at other services
        // (including within dependencies) that get engaged during run.
        Module defaultModule = pkg.getDefaultModule();
        for (DocumentId documentId: pkg.getDefaultModule().documentIds()) {
                ModulePartNode rootNode = defaultModule.document(documentId).syntaxTree().rootNode();
                if (rootNode.members().stream().anyMatch(member -> member.kind() == SyntaxKind.SERVICE_DECLARATION)) {
                    return true;
                }
            }
        return false;
    }
}
