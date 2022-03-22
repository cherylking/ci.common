/**
 * (C) Copyright IBM Corporation 2021, 2022.
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
package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class BinaryScannerUtil {

    public static final String BINARY_SCANNER_MAVEN_GROUP_ID = "com.ibm.websphere.appmod.tools";
    public static final String BINARY_SCANNER_MAVEN_ARTIFACT_ID = "binary-app-scanner";
    public static final String BINARY_SCANNER_MAVEN_TYPE = "jar";
    public static final String BINARY_SCANNER_MAVEN_VERSION = "[21.0.0.5-SNAPSHOT,)";

    public static final String GENERATED_FEATURES_FILE_NAME = "generated-features.xml";
    public static final String GENERATED_FEATURES_FILE_PATH = "configDropins/overrides/" + GENERATED_FEATURES_FILE_NAME;
    private static final String FEATURE_MODIFIED_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.RequiredFeatureModifiedException";
    private static final String FEATURE_CONFLICT_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.FeatureConflictException";
    private static final String PROVIDED_FEATURE_EXCEPTION = "com.ibm.websphere.binary.cmdline.exceptions.ProvidedFeatureConflictException";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE1 = "A working set of features could not be generated due to conflicts " +
            "between configured features and the application's API usage: %s. Review and update your server configuration and " +
            "application to ensure they are not using conflicting features and APIs from different levels of MicroProfile, " +
            "Java EE, or Jakarta EE. Refer to the following set of suggested features for guidance: %s.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE2 = "A working set of features could not be generated due to conflicts " +
            "between configured features: %s. Review and update your server configuration to ensure it is not using conflicting " +
            "features from different levels of MicroProfile, Java EE, or Jakarta EE. Refer to the following set of " +
            "suggested features for guidance: %s.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE3 = "A working set of features could not be generated due to conflicts " +
            "in the application’s API usage: %s. Review and update your application to ensure it is not using conflicting APIs " +
            "from different levels of MicroProfile, Java EE, or Jakarta EE.";
    public static final String BINARY_SCANNER_CONFLICT_MESSAGE4 = "[None available]"; // format should match JVM Set.toString()

    // Strings recognized by the binary scanner arguments for Java/Jakarta EE and MicroProfile
    public static final String BINARY_SCANNER_EEV6 = "ee6";
    public static final String BINARY_SCANNER_EEV7 = "ee7";
    public static final String BINARY_SCANNER_EEV8 = "ee8";
    public static final String BINARY_SCANNER_EEV9 = "ee9";

    public static final String BINARY_SCANNER_PREFIX = "mp";
    public static final String BINARY_SCANNER_MPV1 = "mp1";
    public static final String BINARY_SCANNER_MPV2 = "mp2";
    public static final String BINARY_SCANNER_MPV3 = "mp3";
    public static final String BINARY_SCANNER_MPV4 = "mp4";
    public static final String BINARY_SCANNER_MPV5 = "mp5";

    public static final Map<String, String> BINARY_SCANNER_MP = new HashMap<String, String>();
    static {
        BINARY_SCANNER_MP.put("1.2", "mp1.2");
        BINARY_SCANNER_MP.put("1.3", "mp1.3");
        BINARY_SCANNER_MP.put("1.4", "mp1.4");
        BINARY_SCANNER_MP.put("2.0", "mp2.0");
        BINARY_SCANNER_MP.put("2.1", "mp2.1");
        BINARY_SCANNER_MP.put("2.2", "mp2.2");
        BINARY_SCANNER_MP.put("3.0", "mp3.0");
        BINARY_SCANNER_MP.put("3.2", "mp3.2");
        BINARY_SCANNER_MP.put("3.3", "mp3.3");
        BINARY_SCANNER_MP.put("4.0", "mp4.0");
        BINARY_SCANNER_MP.put("4.1", "mp4.1");
    }

    public abstract void debug(String message);
    public abstract void debug(String message, Throwable e);
    public abstract void error(String message);
    public abstract void warn(String message);
    public abstract void info(String message);
    public abstract boolean isDebugEnabled();

    // The jar file containing the binary scanner code
    private File binaryScanner;
    private URLClassLoader binaryScannerClassLoader = null;
    private Class binaryScannerClass = null;
    private Method binaryScannerMethod = null;

    public BinaryScannerUtil(File binaryScanner) {
        this.binaryScanner = binaryScanner;
    }

    /**
     * Call the binary scanner to generate a list of Liberty features to run an application. It will scan the
     * classFiles parameter or scan all the classes in the allClassesDirectories parameter depending on the
     * optimize parameter. The currentFeatureSet parameter indicates the starting list of features and all the
     * generated features will be compatible. The generated features will also be compatible with the indicated
     * versions of Java EE or Jakarta EE and MicroProfile.
     * 
     * @param currentFeatureSet - the features already specified in the server configuration
     * @param classFiles - a set of class files for the scanner to handle. Should be a subset of allClassesDirectories
     * @param allClassesDirectories - the directories containing all the class files of the application
     * @param logLocation - directory name relative to project or absolute path passed to binary scanner
     * @param targetJavaEE - generate features valid for the indicated version of EE
     * @param targetMicroProfile - generate features valid for the indicated version of MicroProfile
     * @param optimize - true value means to scan all the classes in allClassesDirectories rather than just the
     *                   classes in the classFiles parameter. currentFeatureSet is still used as the basis of
     *                   the feature set.
     * @return - a set of features that will allow the application to run in a Liberty server
     * @throws PluginExecutionException - any exception that prevents the scanner from running
     * @throws NoRecommendationException - indicates a problem and there are no recommended features
     * @throws RecommendationSetException - indicates a problem but the scanner was able to generate a set of
     *                                      features that should work to run the application
     */
    public Set<String> runBinaryScanner(Set<String> currentFeatureSet, List<String> classFiles, Set<String> allClassesDirectories,
            String logLocation, String targetJavaEE, String targetMicroProfile, boolean optimize)
            throws PluginExecutionException, NoRecommendationException, RecommendationSetException, FeatureModifiedException {
        Set<String> featureList = null;
        if (binaryScanner != null && binaryScanner.exists()) {
            try {
                Method generateFeatureSetMethod = getScannerMethod();
                // names: binaryInputs, targetJavaEE, targetMicroProfile, currentFeatures, logLocation, logLevel, locale
                Set<String> binaryInputs = getBinaryInputs(classFiles, allClassesDirectories, optimize);
                String logLevel;
                if (isDebugEnabled()) {
                    logLevel = "*=FINE";  // generate messages for debugging by support team
                } else {
                    logLevel = null;
                    logLocation = null;
                }
                debug("Calling " + binaryScanner.getName() + " with the following inputs...\n" +
                        "  binaryInputs: " + binaryInputs + "\n" +
                        "  targetJavaEE: " + targetJavaEE + "\n" +
                        "  targetMicroP: " + targetMicroProfile + "\n" +
                        "  currentFeatures: " + currentFeatureSet + "\n" +
                        "  logLocation: " + logLocation + "\n" +
                        "  logLevel: " + logLevel + "\n" +
                        "  locale: " + java.util.Locale.getDefault());
                featureList = (Set<String>) generateFeatureSetMethod.invoke(null, binaryInputs, targetJavaEE, targetMicroProfile,
                        currentFeatureSet, logLocation, logLevel, java.util.Locale.getDefault());
                for (String s : featureList) {debug(s);};
            } catch (InvocationTargetException ite) {
                // This is the exception from the JVM that indicates there was an exception in the method we
                // called through reflection. We must extract the actual exception from the 'cause' field.
                // 1. ProvidedFeatureConflictException means the currentFeatureSet contains conflicts.
                // 2. FeatureConflictException means the binary files scanned conflict with each other or with
                // the currentFeatureSet parameter.
                // 3. RequiredFeatureModifiedException means the scanner can make a working list of features but
                // only if certain inputs are changed.
                Throwable scannerException = ite.getCause();
                if (scannerException.getClass().getName().equals(PROVIDED_FEATURE_EXCEPTION)) {
                    // The list of features from the app is passed in but it contains conflicts
                    Set<String> conflicts = getFeatures(scannerException);
                    Set<String> sampleFeatureList = reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile);
                    if (sampleFeatureList == null) {
                        throw new NoRecommendationException(conflicts);
                    } else {
                        throw new RecommendationSetException(true, conflicts, sampleFeatureList);
                    }
                } else if (scannerException.getClass().getName().equals(FEATURE_CONFLICT_EXCEPTION)) {
                    // The scanned files conflict with each other or with current features
                    Set<String> conflicts = getFeatures(scannerException);
                    Set<String> sampleFeatureList = reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile);
                    if (sampleFeatureList == null) {
                        throw new NoRecommendationException(conflicts);
                    } else {
                        throw new RecommendationSetException(false, conflicts, sampleFeatureList);
                    }
                } else if (scannerException.getClass().getName().equals(FEATURE_MODIFIED_EXCEPTION)) {
                    // The scanned files conflict and the scanner suggests modifying some features
                    Set<String> modifications = getFeatures(scannerException);
                    Set<String> sampleFeatureList = reRunBinaryScanner(allClassesDirectories, logLocation, targetJavaEE, targetMicroProfile);
                    throw new FeatureModifiedException(modifications, 
                            (sampleFeatureList == null) ? getNoSampleFeatureList() : sampleFeatureList);
                } else {
                    debug("Exception from binary scanner.", scannerException);
                    throw new PluginExecutionException("Error scanning the application for Liberty features: " + scannerException.toString());
                }
            } catch (MalformedURLException|ClassNotFoundException|NoSuchMethodException|IllegalAccessException loadingException){
                Object o = loadingException.getCause();
                if (o != null) {
                    debug("Caused by exception:"+loadingException.getCause().getClass().getName());
                    debug("Caused by exception message:"+loadingException.getCause().getMessage());
                }
                throw new PluginExecutionException("An error occurred when trying to call the binary scanner jar: " + loadingException.toString());
            }
        } else {
            if (binaryScanner == null) {
                throw new PluginExecutionException("The binary scanner jar location is not defined.");
            } else {
                throw new PluginExecutionException("Could not find the binary scanner jar at " + binaryScanner.getAbsolutePath());
            }
        }
        return featureList;
    }

    /**
     * The method is intended to call the binary scanner to generate a list of the optimal features for an
     * application. This optimal list can be reported to the user as a suggested list of features.
     * 
     * In order to generate the optimal list we must scan all classes in the application and we do not consider
     * the features already specified in the server configuration (server.xml).
     * 
     * @param allClassesDirectories - the scanner will find all the class files in this set of directories
     * @param logLocation - directory name relative to project or absolute path passed to binary scanner
     * @param targetJavaEE - generate features valid for the indicated version of EE
     * @param targetMicroProfile - generate features valid for the indicated version of MicroProfile
     * @return - a set of features that will allow the application to run in a Liberty server
     * @throws PluginExecutionException - any exception that prevents the scanner from running
     */
    public Set<String> reRunBinaryScanner(Set<String> allClassesDirectories, String logLocation, String targetJavaEE, String targetMicroProfile)
            throws PluginExecutionException {
        Set<String> featureList = null;
        try {
            Method generateFeatureSetMethod = getScannerMethod();
            Set<String> binaryInputs = allClassesDirectories;
            Set<String> currentFeaturesSet = new HashSet<String>(); // when re-running always pass in no features
            String logLevel;
            if (isDebugEnabled()) {
                logLevel = "*=FINE";  // generate messages for debugging by support team
            } else {
                logLevel = null;
                logLocation = null;
            }
            debug("Recalling binary scanner with the following inputs...\n" +
                  "  binaryInputs: " + binaryInputs + "\n" +
                  "  targetJavaEE: " + targetJavaEE + "\n" +
                  "  targetMicroP: " + targetMicroProfile + "\n" +
                  "  currentFeatures: " + currentFeaturesSet + "\n" +
                  "  logLocation: " + logLocation + "\n" +
                  "  logLevel: " + logLevel + "\n" +
                  "  locale: " + java.util.Locale.getDefault());
            featureList = (Set<String>) generateFeatureSetMethod.invoke(null, binaryInputs, targetJavaEE, targetMicroProfile,
                    currentFeaturesSet, logLocation, logLevel, java.util.Locale.getDefault());
            for (String s : featureList) {debug(s);};
        } catch (InvocationTargetException ite) {
            Throwable scannerException = ite.getCause();
            if (scannerException.getClass().getName().equals(PROVIDED_FEATURE_EXCEPTION)) {
                // this happens when the list of features passed in contains conflicts so now no recommendation possible
                debug("RuntimeException from re-run of binary scanner", scannerException); // shouldn't happen
                featureList = null;
            } else if (scannerException.getClass().getName().equals(FEATURE_CONFLICT_EXCEPTION)) {
                // The features in the scanned files conflict with each other, no recommendation possible
                featureList = getNoSampleFeatureList();
            } else if (scannerException.getClass().getName().equals(FEATURE_MODIFIED_EXCEPTION)) {
                // The features in the scanned files conflict with each other, no recommendation possible
                featureList = getNoSampleFeatureList();
            } else {
                debug("Exception from rerunning binary scanner.", scannerException);
                throw new PluginExecutionException("Error scanning the application for Liberty feature recommendations: " + scannerException.toString());
            }
        } catch (MalformedURLException|ClassNotFoundException|NoSuchMethodException|IllegalAccessException loadingException){
            Object o = loadingException.getCause();
            if (o != null) {
                debug("Caused by exception2:"+loadingException.getCause().getClass().getName());
                debug("Caused by exception message2:"+loadingException.getCause().getMessage());
            }
            throw new PluginExecutionException("An error occurred when trying to call the binary scanner jar for recommendations: " + loadingException.toString());
        }
        return featureList;
    }

    private Set<String> getNoSampleFeatureList() {
        Set<String> sampleFeatureList;
        sampleFeatureList = new HashSet<String>();
        sampleFeatureList.add(BINARY_SCANNER_CONFLICT_MESSAGE4);
        return sampleFeatureList;
    }

    private ClassLoader getScannerClassLoader() throws MalformedURLException {
        if (binaryScannerClassLoader == null) {
            ClassLoader cl = this.getClass().getClassLoader();
            binaryScannerClassLoader = new URLClassLoader(new URL[] { binaryScanner.toURI().toURL() }, cl);
        }
        return binaryScannerClassLoader;
    }

    private Class getScannerClass() throws MalformedURLException, ClassNotFoundException {
        if (binaryScannerClass == null) {
            ClassLoader cl = getScannerClassLoader();
            binaryScannerClass = cl.loadClass("com.ibm.websphere.binary.cmdline.BinaryScanner");
        }
        return binaryScannerClass;
    }

    private Method getScannerMethod() throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, PluginExecutionException, SecurityException {
        if (binaryScannerMethod == null) {
            Class driveScan = getScannerClass();
            // args: Set<String>, String, String, Set<String>, String, String, Locale
            // names: binaryInputs, targetJavaEE, targetMicroProfile, currentFeatures, logLocation, logLevel, locale
            binaryScannerMethod = driveScan.getMethod("generateFeatureList", Set.class, String.class, String.class,
                    Set.class, String.class, String.class, java.util.Locale.class);
            if (binaryScannerMethod == null) {
                throw new PluginExecutionException("Error finding binary scanner method using reflection");
            }
        }
        return binaryScannerMethod;
    }

    private static Set<String> getBinaryInputs(List<String> classFiles, Set<String> classDirectories, boolean optimize) throws PluginExecutionException {
        Set<String> resultSet;
        if (optimize) {
            if (classDirectories == null || classDirectories.isEmpty()) {
                return new HashSet<String>();
            }
            resultSet = classDirectories;
        } else {
            if (classFiles != null && !classFiles.isEmpty()) {
                resultSet = new HashSet<String>(classFiles);
            } else {
                return new HashSet<String>();
            }
        }
        return resultSet;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getFeatures(Throwable scannerResponse) {
        try {
            ClassLoader cl = getScannerClassLoader();
            @SuppressWarnings("rawtypes")
            Class featureConflictException = cl.loadClass(scannerResponse.getClass().getName());
            Method featureMethod = featureConflictException.getMethod("getFeatures");
            if (featureMethod == null) {
                debug("Error finding " + scannerResponse.getClass().getName() + " method getFeatures() using reflection");
                return null;
            }
            return (Set<String>) featureMethod.invoke(scannerResponse);
        } catch (ClassNotFoundException | MalformedURLException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException x) {
            debug("An error occurred when trying to call the binary scanner jar getFeatures():"+x.getClass().getName(), x);
            Throwable cause = x.getCause();
            if (cause != null) {
                debug("Caused by exception:"+cause.getClass().getName());
                debug("Caused by exception message:" + cause.getMessage());
            }
        }
        return null;
    }

    // A class to pass the list of conflicts back to the caller.
    public class NoRecommendationException extends Exception {
        private static final long serialVersionUID = 1L;
        Set<String> conflicts;
        NoRecommendationException(Set<String> conflictSet) {
            conflicts = conflictSet;
        }
        public Set<String> getConflicts() {
            return conflicts;
        }
    }

    // A class that encapsulates a list of conflicting features, a suggested list of replacements
    // and a flag that indicates whether the conflicts were found in the features existing in the
    // app's server config or if the conflicts exist in the binary files we examined.
    public class RecommendationSetException extends Exception {
        private static final long serialVersionUID = 1L;
        boolean existingFeaturesConflict;
        Set<String> conflicts;
        Set<String> suggestions;
        RecommendationSetException(boolean existing, Set<String> conflictSet, Set<String> suggestionSet) {
            existingFeaturesConflict = existing;
            conflicts = conflictSet;
            suggestions = suggestionSet;
        }
        public boolean isExistingFeaturesConflict() {
            return existingFeaturesConflict;
        }
        public Set<String> getConflicts() {
            return conflicts;
        }
        public Set<String> getSuggestions() {
            return suggestions;
        }
    }

    // A class to pass the list of modified features back to the caller.
    public class FeatureModifiedException extends Exception {
        private static final long serialVersionUID = 1L;
        Set<String> features;
        Set<String> suggestions;
        FeatureModifiedException(Set<String> featureSet, Set<String> suggestionSet) {
            features = featureSet;
            suggestions = suggestionSet;
        }
        public Set<String> getFeatures() {
            return features;
        }
        public Set<String> getSuggestions() {
            return suggestions;
        }
    }
}
