/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.haulmont.gradle.enhance.CubaEnhancer
import groovy.io.FileType
import groovy.xml.QName
import groovy.xml.XmlUtil
import javassist.ClassPool
import org.apache.commons.io.FileUtils
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.SourceSet

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

class CubaEnhancingAction implements Action<Task> {

    protected static final String ABSTRACT_INSTANCE_FQN = 'com.haulmont.chile.core.model.impl.AbstractInstance'

    protected final Project project

    protected final String srcRoot
    protected final String classesRoot
    protected final SourceSet sourceSet

    protected final String enhancedDirPath

    // discuss: it seems that all settings below should be configured for a project
    // previously it was configured for the "enhance" task
    protected String persistenceConfig
    protected String metadataConfig

    @Deprecated
    protected String metadataXml
    protected String metadataPackageRegExp

    protected File customClassesDir

    CubaEnhancingAction(Project project) {
        this.project = project

        if (!project.name.contains("tests")) {
            srcRoot = 'src'
            classesRoot = 'main'
            sourceSet = project.sourceSets.main
        } else {
            srcRoot = 'test'
            classesRoot = 'test'
            sourceSet = project.sourceSets.test
        }

        enhancedDirPath = "$project.buildDir/enhanced-classes/$classesRoot"
    }

    @Override
    void execute(Task task) {
        // todo: remove
        log(' >>> Start enhancing')
        List<String> enhancedClassesFqn = enhanceClasses()

        // todo: remove
        log(' >>> Enhancing finished. Replace default classes by enhanced versions')
        replaceClasses(enhancedClassesFqn)

    }

    protected List<String> enhanceClasses() {
        def outputDir = new File(enhancedDirPath)
        List allClasses = []

        def javaOutputDir = getEntityClassesDir()

        log('[CubaEnhancing] Entity classes directory: ' + javaOutputDir.absolutePath)

        def ownMetadataXmlFiles = getOwnMetadataXmlFiles()
        log("[CubaEnhancing] Metadata XML files: ${ownMetadataXmlFiles}")

        if (!ownMetadataXmlFiles.isEmpty()) {
            File fullPersistenceXml = createFullPersistenceXml()

            if (javaOutputDir.exists()) {
                log("[CubaEnhancing] Start EclipseLink enhancing")
                project.javaexec {
                    main = 'org.eclipse.persistence.tools.weaving.jpa.CubaStaticWeave'
                    classpath(
                            sourceSet.compileClasspath,
                            javaOutputDir
                    )
                    args "-loglevel"
                    args "INFO"
                    args "-persistenceinfo"
                    args "$project.buildDir/tmp/persistence"
                    args "$javaOutputDir"
                    args enhancedDirPath
                    debug = System.getProperty("debugEnhance") ? Boolean.valueOf(System.getProperty("debugEnhance")) : false
                }
            }

            // EclipseLink enhancer copies all classes to build/enhanced-classes,
            // so we should delete files that are not in persistence.xml and metadata.xml
            def persistence = new XmlParser().parse(fullPersistenceXml)
            def persistenceUnit = persistence.'persistence-unit'[0]

            allClasses.addAll(persistenceUnit.'class'.collect { it.value()[0] })
            allClasses.addAll(getTransientEntities())
            // AbstractInstance is not registered but shouldn't be deleted
            allClasses.add(ABSTRACT_INSTANCE_FQN)

            if (outputDir.exists()) {
                outputDir.eachFileRecurse(FileType.FILES) { File file ->
                    Path path = outputDir.toPath().relativize(file.toPath())
                    String name = path.findAll().join('.')
                    name = name.substring(0, name.lastIndexOf('.'))
                    if (!allClasses.contains(name)) {
                        file.delete()
                    }
                }
                // delete empty dirs
                def emptyDirs = []
                outputDir.eachDirRecurse { File dir ->
                    if (dir.listFiles({ File file -> !file.isDirectory() } as FileFilter).toList().isEmpty()) {
                        emptyDirs.add(dir)
                    }
                }
                emptyDirs.reverse().each { File dir ->
                    if (dir.listFiles().toList().isEmpty())
                        dir.delete()
                }
            }
        } else {
            allClasses.addAll(getTransientEntities())

            if (outputDir.exists()) {
                allClasses.each { String className ->
                    Path srcFile = Paths.get("$javaOutputDir/${className.replace('.', '/')}.class")
                    Path dstFile = Paths.get("$enhancedDirPath/${className.replace('.', '/')}.class")
                    Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        if (outputDir.exists()) {
            // run CUBA enhancing on all classes remaining in build/enhanced-classes
            log("[CubaEnhancing] Start CUBA enhancing")

            ClassPool pool = new ClassPool(null)
            pool.appendSystemPath()
            sourceSet.compileClasspath.each { File file -> pool.insertClassPath(file.toString()) }
            pool.insertClassPath(javaOutputDir.toString())
            pool.insertClassPath(outputDir.toString())

            def cubaEnhancer = new CubaEnhancer(pool, outputDir.toString())
            allClasses.each { String name ->
                println " >>> Enhanced class: $name"
                cubaEnhancer.run(name)
            }
        }

        return allClasses
    }

    def replaceClasses(List<String> enhancedClassesFqn) {
        def javaOutputDir = getEntityClassesDir()

        enhancedClassesFqn.each { String classFqn ->
            def classPath = classFqn.replace('.', '/')

            Path srcFile = Paths.get("$enhancedDirPath/${classPath}.class")
            Path dstFile = Paths.get("$javaOutputDir/${classPath}.class")

            // todo: check whether this situation is correct
            if (srcFile.toFile().exists() && dstFile.toFile().exists()) {
                Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        // todo: remove
        log(' >>> Default classes are replaced by enhanced versions!')

        // todo: remove
        log(" >>> Deleting directory with enhanced classes: $enhancedDirPath")

        try {
            FileUtils.deleteDirectory(new File(enhancedDirPath))
            // todo: remove
            log(" >>> Directory with enhanced classes is deleted successfully!")
        } catch (IOException e) {
            // todo: remove
            log(" >>> Unable to delete directory with enhanced classes: $e.message")
        }
    }

    private File getEntityClassesDir() {
        return customClassesDir ?: sourceSet.java.outputDir
        /*if (customClassesDir) {
            return customClassesDir
        }
        return sourceSet.java.outputDir*/
    }

    private List<File> getOwnPersistenceXmlFiles() {
        List<File> files = []
        if (persistenceConfig) {
            List<String> fileNames = Arrays.asList(persistenceConfig.split(' '))
            fileNames.each { fileName ->
                File file = project.file("$srcRoot/$fileName")
                if (file.exists()) {
                    files.add(file)
                } else {
                    throw new IllegalArgumentException("File $file doesn't exist")
                }
            }
        } else {
            FileTree fileTree = project.fileTree(srcRoot).matching {
                include '**/*-persistence.xml'
                include '**/persistence.xml'
            }
            fileTree.each { files.add(it) }
        }
        return files
    }

    private File createFullPersistenceXml() {
        def fileNames = persistenceConfig ? persistenceConfig.tokenize() : null

        def xmlFiles = []

        def compileConf = project.configurations.findByName('compile')
        compileConf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            if (artifact.file.name.endsWith('.jar')) {
                def files = project.zipTree(artifact.file).matching {
                    if (fileNames) {
                        for (name in fileNames) {
                            include "$name"
                        }
                    } else {
                        include '**/*-persistence.xml'
                        include '**/persistence.xml'
                    }
                }
                files.each { xmlFiles.add(it) }
            }
        }

        def files = project.fileTree(srcRoot).matching {
            if (fileNames) {
                for (name in fileNames) {
                    include "$name"
                }
            } else {
                include '**/*-persistence.xml'
                include '**/persistence.xml'
            }
        }
        files.each { xmlFiles.add(it) }

        log("[CubaEnhancing] Persistence XML files: $xmlFiles")

        def parser = new XmlParser()
        Node doc = null
        for (File file in xmlFiles) {
            Node current = parser.parse(file)
            if (doc == null) {
                doc = current
            } else {
                def docPu = doc.'persistence-unit'[0]
                int idx = docPu.children().findLastIndexOf {
                    it instanceof Node && it.name().localPart == 'class'
                }

                if (idx == -1) {
                    idx = 0
                }

                def currentPu = current.'persistence-unit'[0]
                currentPu.'class'.each {
                    def classNode = parser.createNode(docPu, new QName('http://java.sun.com/xml/ns/persistence', 'class'), [:])
                    classNode.value = it.value()[0]

                    docPu.remove(classNode)
                    docPu.children().add(idx++, classNode)
                }
            }
        }

        def string = XmlUtil.serialize(doc)
        log('[CubaEnhancing] fullPersistenceXml:\n' + string)

        def fullPersistenceXml = new File("$project.buildDir/tmp/persistence/META-INF/persistence.xml")
        fullPersistenceXml.parentFile.mkdirs()
        fullPersistenceXml.write(string)
        return fullPersistenceXml
    }

    private List getTransientEntities() {
        List resultList = []
        getOwnMetadataXmlFiles().each { file ->
            def metadata = new XmlParser().parse(file)
            def metadataModel = metadata.'metadata-model'[0]
            List allClasses = metadataModel.'class'.collect { it.value()[0] }

            if (metadataPackageRegExp) {
                Pattern pattern = Pattern.compile(metadataPackageRegExp)
                resultList.addAll(allClasses.findAll { it.matches(pattern) })
            } else {
                resultList.addAll(allClasses)
            }
        }
        return resultList
    }

    private List<File> getOwnMetadataXmlFiles() {
        List<File> files = []

        // discuss: it seems that we should check if metadataXml & metadataConfig exist in the same time
        if (metadataXml) {
            File f = new File(metadataXml)
            if (f.exists()) {
                files.add(f)
            } else {
                throw new IllegalArgumentException("File $metadataXml doesn't exist")
            }
        } else if (metadataConfig) {
            Arrays.asList(metadataConfig.split(' ')).each { fileName ->
                File file = project.file("$srcRoot/$fileName")
                if (file.exists()) {
                    files.add(file)
                } else {
                    throw new IllegalArgumentException("File $file doesn't exist")
                }
            }
        } else {
            FileTree fileTree = project.fileTree(srcRoot).matching {
                include '**/*-metadata.xml'
                include '**/metadata.xml'
            }
            fileTree.each { files.add(it) }
        }
        return files
    }

    // todo: enable correct logging
    private void log(String message) {
        println("$project.name $message")
    }

    // discuss: it was used for @Input & @OutputFiles actions. It seems they are redundant now
    private List getPersistentEntities(List<File> persistenceXmlList) {
        List resultList = []
        persistenceXmlList.each { file ->
            def persistence = new XmlParser().parse(file)
            def pu = persistence.'persistence-unit'[0]
            resultList.addAll(pu.'class'.collect { it.value()[0] })
        }
        return resultList
    }
}
