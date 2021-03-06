/*
 * Copyright (c) 2008-2017 Haulmont.
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

package com.haulmont.gradle.task.db;

import com.haulmont.gradle.hsql.CubaHSQLDBServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.WaitFor;
import org.apache.tools.ant.taskdefs.WaitFor.Unit;
import org.apache.tools.ant.taskdefs.condition.Socket;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

public class CubaHsqlStart extends CubaHsqlTask {

    public static final String HSQLDB_SERVER_MAIN = "org.hsqldb.server.Server";

    protected File dbDataDir;
    protected boolean showUi = true;

    @Override
    protected void init() {
        super.init();

        if (dbDataDir == null) {
            File projectDir = getProject().getRootProject().getProjectDir();
            dbDataDir = new File(projectDir, "deploy/hsqldb");
        }
    }

    @TaskAction
    public void startDb() {
        init();

        //noinspection ResultOfMethodCallIgnored
        dbDataDir.mkdirs();

        if (isShowUi() && !GraphicsEnvironment.isHeadless()) {
            getProject().getLogger().info("[CubaHsqlStart] Starting HSQL UI");

            URL[] classloaderUrls = ((URLClassLoader) CubaHSQLDBServer.class.getClassLoader()).getURLs();

            String[] paths = new String[classloaderUrls.length];
            for (int i = 0; i < classloaderUrls.length; i++) {
                try {
                    paths[i] = new File(classloaderUrls[i].toURI()).getAbsolutePath();
                } catch (URISyntaxException e) {
                    throw new GradleException("Unable to compose path", e);
                }
            }

            String dbServerClassPath = driverClasspath + File.pathSeparator + StringUtils.join(paths, File.pathSeparator);

            Java java = new Java();
            java.setProject(getProject().getAnt().getProject());
            java.setClassname(CubaHSQLDBServer.class.getName());
            java.createClasspath().setPath(dbServerClassPath);
            java.setFork(true);
            java.setSpawn(true);
            java.setDir(dbDataDir);

            java.createArg()
                    .setLine("" + dbPort + " \"" + dbDataDir.getAbsolutePath() + "\" \"" + dbName + "\"");

            java.execute();
        } else {
            getProject().getLogger().info("[CubaHsqlStart] Starting HSQL headless");

            Execute exec = new Execute();
            exec.setAntRun(getProject().getAnt().getProject());
            exec.setWorkingDirectory(dbDataDir);

            if (SystemUtils.IS_OS_WINDOWS) {
                exec.setCommandline(new String[]{
                        "cmd.exe", "/C",
                        "java.exe",
                        "-cp", "\"" + driverClasspath + "\"",
                        HSQLDB_SERVER_MAIN,
                        "--port", String.valueOf(dbPort),
                        "--database.0", "file:\"" + dbName + "\"",
                        "--dbname.0", "\"" + dbName + "\""
                });
            } else {
                exec.setCommandline(new String[]{
                        "java",
                        "-cp", driverClasspath,
                        HSQLDB_SERVER_MAIN,
                        "--port", String.valueOf(dbPort),
                        "--database.0", "file:\"" + dbName + "\"",
                        "--dbname.0", dbName
                });
            }

            getProject().getLogger().info("[CubaHsqlStart] Starting HSQL process: {}",
                    StringUtils.join(exec.getCommandline()));

            try {
                exec.spawn();
            } catch (IOException e) {
                throw new GradleException("Unable to start HSQL server", e);
            }
        }

        Unit UNIT_SECOND = (Unit) Unit.getInstance(Unit.class, "second");

        WaitFor waitFor = new WaitFor();
        waitFor.setProject(getProject().getAnt().getProject());
        waitFor.setMaxWait(10);
        waitFor.setMaxWaitUnit(UNIT_SECOND);
        waitFor.setCheckEvery(1);
        waitFor.setCheckEveryUnit(UNIT_SECOND);

        Socket socket = new Socket();
        socket.setPort(dbPort);
        socket.setServer("localhost");

        waitFor.addSocket(socket);
        waitFor.execute();
    }

    public boolean isShowUi() {
        return showUi;
    }

    public void setShowUi(boolean showUi) {
        this.showUi = showUi;
    }

    public File getDbDataDir() {
        return dbDataDir;
    }

    public void setDbDataDir(File dbDataDir) {
        this.dbDataDir = dbDataDir;
    }
}