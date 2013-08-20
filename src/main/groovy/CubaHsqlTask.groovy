/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */


import org.gradle.api.DefaultTask

/**
 * @author krivopustov
 * @version $Id$
 */
abstract class CubaHsqlTask extends DefaultTask {

    def String driverClasspath
    def String dbName
    def File dbDataDir

    protected void init() {
        if (!dbName)
            dbName = 'cubadb'
        if (!dbDataDir)
            dbDataDir = new File("$project.rootProject.projectDir/data")

        if (!driverClasspath) {
            driverClasspath = project.configurations.jdbc.fileCollection { true }.asPath
            project.configurations.jdbc.fileCollection { true }.files.each { File file ->
                GroovyObject.class.classLoader.addURL(file.toURI().toURL())
            }
        } else {
            driverClasspath.tokenize(File.pathSeparator).each { String path ->
                def url = new File(path).toURI().toURL()
                GroovyObject.class.classLoader.addURL(url)
            }
        }
        project.logger.info(">>> driverClasspath: $driverClasspath")
    }
}
