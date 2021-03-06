/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.projects;

import io.ballerina.projects.environment.PackageCache;
import io.ballerina.projects.environment.ProjectEnvironment;
import io.ballerina.projects.internal.DefaultDiagnosticResult;
import io.ballerina.projects.internal.jballerina.JarWriter;
import io.ballerina.projects.testsuite.TestSuite;
import io.ballerina.projects.testsuite.TesterinaRegistry;
import io.ballerina.projects.util.ProjectUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.Location;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.wso2.ballerinalang.compiler.CompiledJarFile;
import org.wso2.ballerinalang.compiler.bir.codegen.CodeGenerator;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.util.Lists;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static io.ballerina.projects.util.FileUtils.getFileNameWithoutExtension;
import static org.ballerinalang.compiler.CompilerOptionName.SKIP_TESTS;

/**
 * This class represents the Ballerina compiler backend that produces executables that runs on the JVM.
 *
 * @since 2.0.0
 */
// TODO move this class to a separate Java package. e.g. io.ballerina.projects.platform.jballerina
//    todo that, we would have to move PackageContext class into an internal package.
public class JBallerinaBackend extends CompilerBackend {
    private static final String JAR_FILE_EXTENSION = ".jar";
    private static final String TEST_JAR_FILE_NAME_SUFFIX = "-testable";
    private static final String JAR_FILE_NAME_SUFFIX = "";
    private static final HashSet<String> excludeExtensions = new HashSet<>(Lists.of("DSA", "SF"));

    private final PackageResolution pkgResolution;
    private final JdkVersion jdkVersion;
    private final PackageContext packageContext;
    private final PackageCache packageCache;
    private final CompilerContext compilerContext;
    private final CodeGenerator jvmCodeGenerator;
    private DiagnosticResult diagnosticResult;
    private boolean codeGenCompleted;
    private final JarResolver jarResolver;
    private final CompilerOptions compilerOptions;

    public static JBallerinaBackend from(PackageCompilation packageCompilation, JdkVersion jdkVersion) {
        return packageCompilation.getCompilerBackend(jdkVersion,
                (targetPlatform -> new JBallerinaBackend(packageCompilation, jdkVersion)));
    }

    private JBallerinaBackend(PackageCompilation packageCompilation, JdkVersion jdkVersion) {
        this.jdkVersion = jdkVersion;
        this.packageContext = packageCompilation.packageContext();
        this.pkgResolution = packageContext.getResolution();
        this.jarResolver = new JarResolver(this, packageContext.getResolution());

        ProjectEnvironment projectEnvContext = this.packageContext.project().projectEnvironmentContext();
        this.packageCache = projectEnvContext.getService(PackageCache.class);
        this.compilerContext = projectEnvContext.getService(CompilerContext.class);
        this.jvmCodeGenerator = CodeGenerator.getInstance(compilerContext);
        this.compilerOptions = CompilerOptions.getInstance(compilerContext);

        // TODO The following line is a temporary solution to cleanup the TesterinaRegistry
        TesterinaRegistry.reset();

        // Trigger code generation
        performCodeGen();
    }

    private void performCodeGen() {
        if (codeGenCompleted) {
            return;
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ModuleContext moduleContext : pkgResolution.topologicallySortedModuleList()) {
            moduleContext.generatePlatformSpecificCode(compilerContext, this);
            diagnostics.addAll(moduleContext.diagnostics());
        }

        this.diagnosticResult = new DefaultDiagnosticResult(diagnostics);
        codeGenCompleted = true;
    }

    public DiagnosticResult diagnosticResult() {
        return diagnosticResult;
    }

    // TODO EmitResult should not contain compilation diagnostics.
    public EmitResult emit(OutputType outputType, Path filePath) {
        if (diagnosticResult.hasErrors()) {
            return new EmitResult(false, diagnosticResult);
        }

        switch (outputType) {
            case EXEC:
                emitExecutable(filePath);
                break;
            case BALO:
                emitBalo(filePath);
                break;
            default:
                throw new RuntimeException("Unexpected output type: " + outputType);
        }
        // TODO handle the EmitResult properly
        return new EmitResult(true, diagnosticResult);
    }

    private void emitBalo(Path filePath) {
        JBallerinaBaloWriter writer = new JBallerinaBaloWriter(jdkVersion);
        Package pkg = packageCache.getPackageOrThrow(packageContext.packageId());
        writer.write(pkg, filePath);
    }

    @Override
    public Collection<PlatformLibrary> platformLibraryDependencies(PackageId packageId) {
        Package pkg = packageCache.getPackageOrThrow(packageId);
        PackageManifest.Platform javaPlatform = pkg.manifest().platform(jdkVersion.code());
        if (javaPlatform == null || javaPlatform.dependencies().isEmpty()) {
            return Collections.emptyList();
        }

        Collection<PlatformLibrary> platformLibraries = new ArrayList<>();
        for (Map<String, Object> dependency : javaPlatform.dependencies()) {
            String dependencyFilePath = (String) dependency.get(JarLibrary.KEY_PATH);
            // if the path is relative we will covert to absolute relative to Ballerina.toml file
            Path jarPath = Paths.get(dependencyFilePath);
            if (!jarPath.isAbsolute()) {
                jarPath = pkg.project().sourceRoot().resolve(jarPath);
            }
            platformLibraries.add(new JarLibrary(jarPath));
        }

        // TODO Where can we cache this collection
        return platformLibraries;
    }

    @Override
    public PlatformLibrary codeGeneratedLibrary(PackageId packageId, ModuleName moduleName) {
        return codeGeneratedLibrary(packageId, moduleName, JAR_FILE_NAME_SUFFIX);
    }

    @Override
    public PlatformLibrary codeGeneratedTestLibrary(PackageId packageId, ModuleName moduleName) {
        return codeGeneratedLibrary(packageId, moduleName, TEST_JAR_FILE_NAME_SUFFIX + JAR_FILE_NAME_SUFFIX);
    }

    @Override
    public PlatformLibrary runtimeLibrary() {
        return new JarLibrary(ProjectUtils.getBallerinaRTJarPath());
    }

    @Override
    public TargetPlatform targetPlatform() {
        return jdkVersion;
    }

    // TODO This method should be moved to some other class owned by the JBallerinaBackend
    @Override
    public void performCodeGen(ModuleContext moduleContext, CompilationCache compilationCache) {
        BLangPackage bLangPackage = moduleContext.bLangPackage();
        CompiledJarFile compiledJarFile = jvmCodeGenerator.generate(moduleContext.moduleId(), this, bLangPackage);
        String jarFileName = getJarFileName(moduleContext) + JAR_FILE_NAME_SUFFIX;
        try {
            ByteArrayOutputStream byteStream = JarWriter.write(compiledJarFile);
            compilationCache.cachePlatformSpecificLibrary(this, jarFileName, byteStream);
        } catch (IOException e) {
            throw new ProjectException("Failed to cache generated jar, module: " + moduleContext.moduleName());
        }

        // skip generation of the test jar if --skip-tests option is set to true
        if (Boolean.parseBoolean(compilerOptions.get(SKIP_TESTS))) {
            return;
        }

        if (!bLangPackage.hasTestablePackage()) {
            return;
        }

        String testJarFileName = jarFileName + TEST_JAR_FILE_NAME_SUFFIX;
        CompiledJarFile compiledTestJarFile = jvmCodeGenerator.generateTestModule(
                moduleContext.moduleId(), this, bLangPackage.testablePkgs.get(0));
        try {
            ByteArrayOutputStream byteStream = JarWriter.write(compiledTestJarFile);
            compilationCache.cachePlatformSpecificLibrary(this, testJarFileName, byteStream);
        } catch (IOException e) {
            throw new ProjectException("Failed to cache generated test jar, module: " + moduleContext.moduleName());
        }
    }

    @Override
    public String libraryFileExtension() {
        return JAR_FILE_EXTENSION;
    }

    public JarResolver jarResolver() {
        return jarResolver;
    }

    /**
     * Generate and return the testsuite for module tests.
     *
     * @param module module
     * @return test suite
     */
    public Optional<TestSuite> testSuite(Module module) {
        if (module.project().kind() != ProjectKind.SINGLE_FILE_PROJECT
                && !module.moduleContext().bLangPackage().hasTestablePackage()) {
            return Optional.empty();
        }
        // skip generation of the testsuite if --skip-tests option is set to true
        if (Boolean.getBoolean(compilerOptions.get(SKIP_TESTS))) {
            return Optional.empty();
        }

        return Optional.of(generateTestSuite(module.moduleContext(), compilerContext));
    }

    private TestSuite generateTestSuite(ModuleContext moduleContext, CompilerContext compilerContext) {
        BLangPackage bLangPackage = moduleContext.bLangPackage();
        TestSuite testSuite = new TestSuite(bLangPackage.packageID.name.value,
                bLangPackage.packageID.toString(),
                bLangPackage.packageID.orgName.value,
                bLangPackage.packageID.version.value);
        TesterinaRegistry.getInstance().getTestSuites().put(
                moduleContext.descriptor().name().toString(), testSuite);

        // set data
        testSuite.setInitFunctionName(bLangPackage.initFunction.name.value);
        testSuite.setStartFunctionName(bLangPackage.startFunction.name.value);
        testSuite.setStopFunctionName(bLangPackage.stopFunction.name.value);
        testSuite.setPackageName(bLangPackage.packageID.toString());
        testSuite.setSourceRootPath(moduleContext.project().sourceRoot().toString());

        // add functions of module/standalone file
        bLangPackage.functions.forEach(function -> {
            Location pos = function.pos;
            if (pos != null && !(function.getFlags().contains(Flag.RESOURCE) ||
                    function.getFlags().contains(Flag.REMOTE))) {
                // Remove the duplicated annotations.
                String className = pos.lineRange().filePath().replace(".bal", "")
                        .replace("/", ".");
                String functionClassName = JarResolver.getQualifiedClassName(
                        bLangPackage.packageID.orgName.value,
                        bLangPackage.packageID.name.value,
                        bLangPackage.packageID.version.value,
                        className);
                testSuite.addTestUtilityFunction(function.name.value, functionClassName);
            }
        });

        BLangPackage testablePkg;
        if (moduleContext.project().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            testablePkg = bLangPackage;
        } else {
            testablePkg = bLangPackage.getTestablePkg();
            testSuite.setTestInitFunctionName(testablePkg.initFunction.name.value);
            testSuite.setTestStartFunctionName(testablePkg.startFunction.name.value);
            testSuite.setTestStopFunctionName(testablePkg.stopFunction.name.value);

            testablePkg.functions.forEach(function -> {
                Location location = function.pos;
                if (location != null && !(function.getFlags().contains(Flag.RESOURCE) ||
                        function.getFlags().contains(Flag.REMOTE))) {
                    String className = location.lineRange().filePath().replace(".bal", "").
                            replace("/", ".");
                    String functionClassName = JarResolver.getQualifiedClassName(
                            bLangPackage.packageID.orgName.value,
                            bLangPackage.packageID.name.value,
                            bLangPackage.packageID.version.value,
                            className);
                    testSuite.addTestUtilityFunction(function.name.value, functionClassName);
                }
            });
        }

        // process annotations in test functions
        TestAnnotationProcessor testAnnotationProcessor = new TestAnnotationProcessor();
        testAnnotationProcessor.init(compilerContext, testablePkg);
        testablePkg.functions.forEach(testAnnotationProcessor::processFunction);

        testablePkg.topLevelNodes.stream().filter(topLevelNode ->
                topLevelNode instanceof BLangSimpleVariable).map(topLevelNode ->
                (SimpleVariableNode) topLevelNode).forEach(testAnnotationProcessor::processMockFunction);
        return testSuite;
    }

    // TODO Can we move this method to Module.displayName()
    private String getJarFileName(ModuleContext moduleContext) {
        String jarName;
        if (moduleContext.project().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            DocumentId documentId = moduleContext.srcDocumentIds().iterator().next();
            String documentName = moduleContext.documentContext(documentId).name();
            jarName = getFileNameWithoutExtension(documentName);
        } else {
            jarName = moduleContext.moduleName().toString();
        }

        return jarName;
    }

    private void assembleExecutableJar(Path executableFilePath,
                                       Manifest manifest,
                                       Collection<Path> jarFilePaths) throws IOException {
        // Used to prevent adding duplicated entries during the final jar creation.
        HashSet<String> copiedEntries = new HashSet<>();

        // Used to process SPI related metadata entries separately. The reason is unlike the other entry types,
        // service loader related information should be merged together in the final executable jar creation.
        HashMap<String, StringBuilder> serviceEntries = new HashMap<>();

        try (ZipArchiveOutputStream outStream = new ZipArchiveOutputStream(
                new BufferedOutputStream(new FileOutputStream(executableFilePath.toString())))) {
            writeManifest(manifest, outStream);

            // Copy all the jars
            for (Path jarFilePath : jarFilePaths) {
                copyJar(outStream, jarFilePath, copiedEntries, serviceEntries);
            }

            // Copy merged spi services.
            for (Map.Entry<String, StringBuilder> entry : serviceEntries.entrySet()) {
                String s = entry.getKey();
                StringBuilder service = entry.getValue();
                JarArchiveEntry e = new JarArchiveEntry(s);
                outStream.putArchiveEntry(e);
                outStream.write(service.toString().getBytes(StandardCharsets.UTF_8));
                outStream.closeArchiveEntry();
            }
        }
    }

    private void writeManifest(Manifest manifest, ZipArchiveOutputStream outStream) throws IOException {
        JarArchiveEntry e = new JarArchiveEntry(JarFile.MANIFEST_NAME);
        outStream.putArchiveEntry(e);
        manifest.write(new BufferedOutputStream(outStream));
        outStream.closeArchiveEntry();
    }

    private Manifest createManifest() {
        // Getting the jarFileName of the root module of this executable
        PlatformLibrary rootModuleJarFile = codeGeneratedLibrary(packageContext.packageId(),
                packageContext.defaultModuleContext().moduleName());

        String mainClassName;
        try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(rootModuleJarFile.path()))) {
            Manifest mf = jarStream.getManifest();
            mainClassName = (String) mf.getMainAttributes().get(Attributes.Name.MAIN_CLASS);
        } catch (IOException e) {
            throw new RuntimeException("Generated jar file cannot be found for the module: " +
                    packageContext.defaultModuleContext().moduleName());
        }

        Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClassName);
        return manifest;
    }

    /**
     * Copies a given jar file into the executable fat jar.
     *
     * @param outStream     Output stream of the final uber jar.
     * @param jarFilePath   Path of the source jar file.
     * @param copiedEntries Entries set will be used to ignore duplicate files.
     * @param services      Services will be used to temporary hold merged spi files.
     * @throws IOException If jar file copying is failed.
     */
    private void copyJar(ZipArchiveOutputStream outStream, Path jarFilePath, HashSet<String> copiedEntries,
                         HashMap<String, StringBuilder> services) throws IOException {

        ZipFile zipFile = new ZipFile(jarFilePath.toFile());
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
                    throw new RuntimeException(e);
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
    }

    private static boolean isCopiedOrExcludedEntry(String entryName, HashSet<String> copiedEntries) {
        return copiedEntries.contains(entryName) ||
                excludeExtensions.contains(entryName.substring(entryName.lastIndexOf(".") + 1));
    }

    private PlatformLibrary codeGeneratedLibrary(PackageId packageId,
                                                 ModuleName moduleName,
                                                 String fileNameSuffix) {
        Package pkg = packageCache.getPackageOrThrow(packageId);
        ProjectEnvironment projectEnvironment = pkg.project().projectEnvironmentContext();
        CompilationCache compilationCache = projectEnvironment.getService(CompilationCache.class);
        String jarFileName = getJarFileName(pkg.packageContext().moduleContext(moduleName)) + fileNameSuffix;
        Optional<Path> platformSpecificLibrary = compilationCache.getPlatformSpecificLibrary(
                this, jarFileName);
        return new JarLibrary(platformSpecificLibrary.orElseThrow(
                () -> new IllegalStateException("Cannot find the generated jar library for module: " + moduleName)));
    }

    private void emitExecutable(Path executableFilePath) {
        if (!this.packageContext.defaultModuleContext().entryPointExists()) {
            // TODO Improve error handling
            throw new ProjectException("no entrypoint found in package: " + this.packageContext.packageName());
        }

        Manifest manifest = createManifest();
        Collection<Path> jarLibraryPaths = jarResolver.getJarFilePathsRequiredForExecution();

        try {
            assembleExecutableJar(executableFilePath, manifest, jarLibraryPaths);
        } catch (IOException e) {
            throw new ProjectException("error while creating the executable jar file for package: " +
                    this.packageContext.packageName(), e);
        }
    }

    /**
     * Enum to represent output types.
     */
    public enum OutputType {
        EXEC("exec"),
        BALO("balo"),
        ;

        private String value;

        OutputType(String value) {
            this.value = value;
        }
    }
}
