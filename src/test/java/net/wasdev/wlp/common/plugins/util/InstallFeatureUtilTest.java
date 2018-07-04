/**
 * (C) Copyright IBM Corporation 2018.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.wasdev.wlp.common.plugins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class InstallFeatureUtilTest extends BaseInstallFeatureUtilTest {
    
    private static final String RESOLVER_JAR_PATH = "resolver/io/openliberty/features/repository-resolver/18.0.0.2/repository-resolver-18.0.0.2.jar";
    private static final String RESOLVER_SYMBOLIC_NAME = "com.ibm.ws.repository.resolver";

    @Test
    public void testConstructor() throws Exception {
        InstallFeatureUtil util = getNewInstallFeatureUtil();
        assertNotNull(util);
    }
    
    @Test(expected = PluginExecutionException.class)
    public void testConstructorNoProperties() throws Exception {
        File olProps = new File(installDir, "lib/versions/openliberty.properties");
        File wlProps = new File(installDir, "lib/versions/WebSphereApplicationServer.properties");
        assertTrue(olProps.delete());
        assertTrue(wlProps.delete());
        new InstallFeatureTestUtil(installDir, null, null, new HashSet<String>());
    }
    
    @Test
    public void testConstructorTo() throws Exception {
        InstallFeatureUtil util = new InstallFeatureTestUtil(installDir, null, "myextension", new HashSet<String>());
        assertNotNull(util);
    }
    
    /**
     * TODO remove the expected exception when "from" scenario is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorFrom() throws Exception {
        new InstallFeatureTestUtil(installDir, installDir.getAbsolutePath(), null, new HashSet<String>());
    }
    
    /**
     * TODO remove the expected exception when installing from ESAs is supported
     */
    @Test(expected = PluginScenarioException.class)
    public void testConstructorEsas() throws Exception {
        Set<String> esas = new HashSet<String>();
        esas.add("abc.esa");
        new InstallFeatureTestUtil(installDir, null, null, esas);
    }
    
    /**
     * The installFeatures method should be tested from the actual project that
     * uses it. It will throw an exception here because the test install map jar
     * does not contain anything.
     */
    @Test(expected = PluginExecutionException.class)
    public void testInstallFeatures() throws Exception {
        InstallFeatureUtil util = getNewInstallFeatureUtil();
        List<String> featuresToInstall = new ArrayList<String>();
        featuresToInstall.add("a-1.0");
        util.installFeatures(true, featuresToInstall);
    }
    
    @Test
    public void testCombineToSet() throws Exception {
        Set<String> a = new HashSet<String>();
        a.add("1");
        a.add("2");
        List<String> b = new ArrayList<String>();
        b.add("1");
        b.add("3");
        List<String> c = new ArrayList<String>();
        b.add("4");
        b.add("5");
        Set<String> result = InstallFeatureUtil.combineToSet(a, b, c);
        assertEquals(5, result.size());
    }

    @Test
    public void testExtractSymbolicName() throws Exception {
        String symbolicName = InstallFeatureUtil.extractSymbolicName(new File(RESOURCES_DIR, RESOLVER_JAR_PATH));
        assertEquals("Symbolic name does not match", RESOLVER_SYMBOLIC_NAME, symbolicName);
    }

    @Test
    public void testGetNextProductVersion() throws Exception {
        assertEquals("18.0.0.3", InstallFeatureUtil.getNextProductVersion("18.0.0.2"));
        assertEquals("18.0.0.10", InstallFeatureUtil.getNextProductVersion("18.0.0.9"));
        assertEquals("18.0.0.11", InstallFeatureUtil.getNextProductVersion("18.0.0.10"));
        assertEquals("1.1", InstallFeatureUtil.getNextProductVersion("1.0"));
        assertEquals("1.1.2", InstallFeatureUtil.getNextProductVersion("1.1.1"));
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNoPeriods() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18002");
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNonNumeric() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18.0.0.a");
    }

    @Test(expected = PluginExecutionException.class)
    public void testGetNextProductVersionNonNumeric2() throws Exception {
        InstallFeatureUtil.getNextProductVersion("18.0.0.2-a");
    }

    @Test
    public void testDownloadOverrideBundle() throws Exception {
        InstallFeatureUtil util = new InstallFeatureTestUtil(installDir, null, null, new HashSet<String>()) {
            @Override
            public File downloadArtifact(String groupId, String artifactId, String type, String version)
                    throws PluginExecutionException {
                if (artifactId.equals(InstallFeatureUtil.REPOSITORY_RESOLVER_ARTIFACT_ID)) {
                    assertEquals("[18.0.0.2, 18.0.0.3)", version);
                    String downloadVersion = "18.0.0.2";

                    String[] groupComponents = groupId.split("\\.");
                    StringBuilder sb = new StringBuilder("resolver");
                    for (String groupComponent : groupComponents) {
                        sb.append("/" + groupComponent);
                    }
                    sb.append("/" + artifactId + "/" + downloadVersion + "/" + artifactId + "-" + downloadVersion
                            + "." + type);
                    return new File(RESOURCES_DIR, sb.toString());
                } else {
                    return super.downloadArtifact(groupId, artifactId, type, version);
                }
            }
        };
        String result = util.downloadOverrideBundle(InstallFeatureUtil.REPOSITORY_RESOLVER_GROUP_ID,
                InstallFeatureUtil.REPOSITORY_RESOLVER_ARTIFACT_ID, "jar");
        String expectedEndsWith = RESOLVER_JAR_PATH + ";" + RESOLVER_SYMBOLIC_NAME;
        String expectedEndsWithWindows = expectedEndsWith.replaceAll("/", "\\\\");
        assertTrue(
                "downloadOverrideBundle should return a string that ends with " + expectedEndsWith + " or "
                        + expectedEndsWithWindows + ", but actual result was " + result,
                result.endsWith(expectedEndsWith) || result.endsWith(expectedEndsWithWindows));
    }

}
