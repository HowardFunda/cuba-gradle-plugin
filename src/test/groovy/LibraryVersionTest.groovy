/*
 * Copyright (c) 2008-2016 Haulmont.
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
 *
 */

import static CubaDeployment.getLibraryDefinition
import static CubaDeployment.getLowestVersion

/**
 */
class LibraryVersionTest extends GroovyTestCase {

    void testLibraryVersionMatcher() {
        def testData = [
            "charts-global-4.0-SNAPSHOT.jar": ["charts-global", "4.0-SNAPSHOT"],
            "asm-3.2-RELEASE.jar": ["asm", "3.2-RELEASE"],
            "tika-core-0.9.jar": ["tika-core", "0.9"],
            "spring-context-support-3.1.3.RELEASE.jar": ["spring-context-support", "3.1.3.RELEASE"],
            "slf4j-api-1.5.6.jar": ["slf4j-api", "1.5.6"],
            "core-renderer-1.1.3-SNAPSHOT.jar": ["core-renderer", "1.1.3-SNAPSHOT"],
            "javassist-3.4.GA.jar": ["javassist", "3.4.GA"],
            "antlr-runtime-3.2.haulmont.jar": ["antlr-runtime", "3.2.haulmont"],
            "core-renderer-R8-SNAPSHOT.jar": ["core-renderer", "R8-SNAPSHOT"],
            "core-renderer-1.1.3.RELEASE.jar": ["core-renderer", "1.1.3.RELEASE"],
            "core-lib-renderer2-3.1.haulmont-SNAPSHOT.jar": ["core-lib-renderer2", "3.1.haulmont-SNAPSHOT"],
            "xpp3_min-1.1.4c.jar": ["xpp3_min", "1.1.4c"],
            "some-lib-.jar": ["some-lib-", null],
            "some-lib-without-version.jar": ["some-lib-without-version", null],
            "some-test-SNAPSHOT.jar" : ["some-test", "SNAPSHOT"],
            "taxi-core-21.jar" : ["taxi-core", "21"]
        ]

        for (pair in testData) {
            def libraryName = pair.key
            def libraryDefinition = getLibraryDefinition(libraryName)
            assertNotNull(libraryDefinition);
            assertEquals(libraryDefinition.name, pair.getValue().get(0))
            assertEquals(libraryDefinition.version, pair.getValue().get(1))
        }
    }

    void testLibraryVersionsCompare() {
        def testData = [
                ["1.0.12", "1.1", "1.0.12"],
                ["7.3.9.cuba.9", "7.3.9.cuba.20", "7.3.9.cuba.9"],
                ["7.4.0", "7.4-SNAPSHOT", "7.4.0"],
                ["7.5", "7.4-SNAPSHOT", "7.4-SNAPSHOT"],
                ["7.5.0", "7.4-SNAPSHOT", "7.4-SNAPSHOT"],
                ["7.5.0", "7.5.0.RC1", "7.5.0.RC1"],
                ["6.1-SNAPSHOT", "6.1.0", "6.1.0"],
                ["6.1.0.RC2", "6.1.0", "6.1.0.RC2"],
                ["6.1.0.RC2", "6.1.0.RC1", "6.1.0.RC1"],
                ["6.1.0.RC2", "6.1.0.RC21", "6.1.0.RC2"],
                ["6.1.0.RC3", "6.1.0.RC21", "6.1.0.RC3"],
                ["6.1.0.RC3", "6.1.0.RC", "6.1.0.RC"],
                ["6.0.9", "6.1.0.RC1", "6.0.9"]
        ]

        for (def cv : testData) {
            assertEquals(cv[2], getLowestVersion(cv[0], cv[1]))
            assertEquals(cv[2], getLowestVersion(cv[1], cv[0]))
        }
    }
}