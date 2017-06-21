package com.cisco.gradle.externalbuild

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ExternalBuildTest extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File outputFile

    static final String pluginInit = """
        plugins {
            id 'com.cisco.external-build'
        }

        import com.cisco.gradle.externalbuild.ExternalNativeExecutableSpec
        import com.cisco.gradle.externalbuild.ExternalNativeLibrarySpec
        import com.cisco.gradle.externalbuild.tasks.CMake
        import com.cisco.gradle.externalbuild.tasks.GnuMake
    """

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        outputFile = testProjectDir.newFile()
    }

    BuildResult runBuild() {
        return GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir.root)
                .withArguments('build')
                .build()
    }

    String outputText(String component, String buildType) {
        File outputFolder = new File(testProjectDir.root, "build/tmp/externalBuild${component.capitalize()}")
        return new File(outputFolder, "${buildType}-output.txt").text.trim()
    }

    List<String> folderContents(File folder, String subfolder='') {
        List<String> filenames = new File(folder, subfolder).listFiles()*.name
        if (filenames != null) {
            Collections.sort(filenames)
        }
        return filenames
    }

    def "no output"() {
        given:
        buildFile << """
            $pluginInit

            model {
                components {
                    foo(ExternalNativeExecutableSpec) {
                        buildConfig(GnuMake) {
                            executable 'echo'
                        }
                    }
                }
            }
        """

        when:
        def result = runBuild()

        then:
        result.task(":build").outcome == SUCCESS
        folderContents(testProjectDir.root, 'build/exe/foo') == null
    }

    def "basic make"() {
        given:
        buildFile << """
            $pluginInit

            model {
                components {
                    foo(ExternalNativeExecutableSpec) {
                        buildConfig(GnuMake) {
                            executable 'echo'
                            jobs 1
                            targets 'all', 'install'
                            args 'make-arg-1'
                        }

                        buildOutput {
                            outputFile = file('${outputFile.path}')
                        }
                    }
                }
            }
        """

        when:
        def result = runBuild()

        then:
        result.task(":build").outcome == SUCCESS
        outputText('fooExecutable', 'makeAll') == 'make-arg-1 -j 1 all'
        outputText('fooExecutable', 'makeInstall') == 'make-arg-1 -j 1 install'
        folderContents(testProjectDir.root, 'build/exe/foo') == ['foo']
    }

    def "basic cmake"() {
        given:
        buildFile << """
            $pluginInit

            model {
                components {
                    foo(ExternalNativeExecutableSpec) {
                        buildConfig(CMake) {
                            executable 'echo'
                            jobs 1
                            targets 'all'
                            cmakeExecutable 'echo'
                            cmakeArgs 'cmake-arg-1'
                        }

                        buildOutput {
                            outputFile = file('${outputFile.path}')
                        }
                    }
                }
            }
        """

        when:
        def result = runBuild()

        then:
        result.task(":build").outcome == SUCCESS
        outputText('fooExecutable', 'cmake') == 'cmake-arg-1'
        outputText('fooExecutable', 'makeAll') == '-j 1 all'
        folderContents(testProjectDir.root, 'build/exe/foo') == ['foo']
    }
}