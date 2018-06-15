package jaci.openrio.gradle.ide

import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import jaci.gradle.nativedeps.DelegatedDependencySet
import jaci.openrio.gradle.wpi.toolchain.WPIToolchainPlugin
import jaci.openrio.gradle.wpi.toolchain.install.LinuxToolchainInstaller
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec

@CompileStatic
class EditorConfigurationTask extends DefaultTask {

    // todo: add -g and -Og when debugging.

    @TaskAction
    void generate() {
        def isWin = OperatingSystem.current().isWindows()

        def cfg = [:]

        // Compiler
        def toolchainD = project.plugins.getPlugin(WPIToolchainPlugin).discoverRoborioToolchain()
        def dCompiler = [
            toolchainDir     : toolchainD.rootDir().get().absolutePath,
            gdbPath          : toolchainD.gdbFile().get().absolutePath,
            compilerPath     : toolchainD.gccFile().get().absolutePath,
            sysroot          : toolchainD.sysroot().map({ File f -> f.absolutePath }).orElse(null),
            compilerHeaders  : toolchainD.includeDir().get(),
            compilerLibraries: toolchainD.libDir().get()
        ]

        cfg['compiler'] = dCompiler

        // Components
        def dComponents = [:]
        def ext = project.extensions.getByType(EditorConfigurationExtension)
        ext._binaries.each { NativeExecutableBinarySpec bin ->
            def srcpaths = []
            def exportedHeaders = []
            def headerpaths = []
            def sourcepaths = []
            def sopaths = []

            bin.inputs.withType(HeaderExportingSourceSet) { HeaderExportingSourceSet ss ->
                srcpaths += ss.source.srcDirs
                exportedHeaders += ss.exportedHeaders.srcDirs
            }
            bin.libs.each { NativeDependencySet ds ->
                headerpaths += ds.getIncludeRoots()
                sopaths += ds.getRuntimeFiles().files
                if (ds instanceof DelegatedDependencySet) {
                    sourcepaths += (ds as DelegatedDependencySet).getSourceFiles()
                }
            }

            def dCurrent = [
                launchfile        : bin.executable.file.absolutePath,
                srcDirs           : (srcpaths as List<File>).collect { it.absolutePath },
                libHeaderDirs     : (headerpaths as List<File>).collect { it.absolutePath },
                libSharedFilePaths: (sopaths as List<File>).collect { it.absolutePath },
                libSourceFiles    : (sourcepaths as List<File>).collect { it.absolutePath },
                exportedHeaders   : (exportedHeaders as List<File>).collect { it.absolutePath }
            ]

            dComponents[bin.component.name] = dCurrent
        }

        cfg['components'] = dComponents

        def gbuilder = new GsonBuilder()
        gbuilder.setPrettyPrinting()
        def json = gbuilder.create().toJson(cfg)

        println(json)
        def outfile = new File(project.buildDir, "editor/.editcfg")
        outfile.parentFile.mkdirs()
        outfile.text = json
    }

}
