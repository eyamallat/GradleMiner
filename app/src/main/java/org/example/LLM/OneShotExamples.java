package org.example.LLM;

public final class OneShotExamples {
    public static final String EXAMPLES = """
refactoring_types_definitions =[
       {
    "RefactoringType": "RENAME_FIELD",
    "Example": "- ext.oldVersion = '1.0'\\n+ ext.newVersion = '1.0'",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"RENAME_FIELD","Output":"RENAME_FIELD: Rename 'oldVersion' to 'newVersion'"}]},
    "Explanation of the Example": "The identifier changes from 'oldVersion' to 'newVersion', but the assigned value remains '1.0', so the change is a rename rather than extraction, movement, or behavior change."
        },
        
        {
    "RefactoringType": "REMOVE_UNUSED_CODE",
     "Example": "'build.gradle':\\n```diff\\n@@ -18,3 +18,0 @@\\n- repositories {\\n-   jcenter()\\n- }\\n```",
     "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"REMOVE_UNUSED_CODE","Output":"REMOVE_UNUSED_CODE: Remove unused element 'build.gradle:repositories (18)'"}]},
     "Explanation of the Example": "The repositories block is deleted from the Gradle build file. No added, moved, or equivalent replacement logic explains the deletion, so after more specific refactorings are ruled out, the removed block is reported as residual unused code."
            },

            {
     "RefactoringType": "SCHEDULING_TASKS",
     "Example": "'build.gradle':\\n```diff\\n@@ -23,0 +24,1 @@\\n+ test.dependsOn integrationTest\\n```",
     "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"SCHEDULING_TASKS","Output":"SCHEDULING_TASKS: Introduce task relation 'dependsOn' from 'test' to 'integrationTest' in 'build.gradle:dependsOn (24)'"}]},
     "Explanation of the Example": "Both 'test' and 'integrationTest' already exist before the commit. The added dependsOn statement only introduces a scheduling relation between existing tasks, so it is reported as SCHEDULING_TASKS rather than as a newly added task."
            },

            {
     "RefactoringType": "REFORMAT_CODE",
     "Example": "-implementation'junit:junit:4.13.2'\\n+implementation 'junit:junit:4.13.2'",
     "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"REFORMAT_CODE","Output":"REFORMAT_CODE: Reformat code (no semantic change)"}]},
      "Explanation of the Example": "Only spacing is changed in the Gradle dependency declaration. The dependency, configuration, and build behavior remain the same, so this is a formatting-only refactoring."
            },

        {
    "RefactoringType": "EXTRACT_AND_PULL_UP_MODULE",
    "Example": "Code Changes for 'bintray_upload.gradle':\\n"
               "```diff\\n"
               "+apply plugin: 'com.github.dcendents.android-maven'\\n"
               "+apply plugin: 'com.jfrog.bintray'\\n"
               "+def projectName = 'gene-rate'\\n"
               "+install {\\n"
               "+    repositories.mavenInstaller {\\n"
               "+        pom.project {\\n"
               "+            name projectName\\n"
               "+            description 'Generate a better rating for your Android app'\\n"
               "+            url 'https://github.com/Pixplicity/gene-rate'\\n"
               "+            inceptionYear '2017'\\n"
               "+            packaging 'aar'\\n"
               "+            group 'com.pixplicity.generate'\\n"
               "+            groupId 'com.pixplicity.generate'\\n"
               "+            artifactId projectName\\n"
               "+            version '1.0.0'\\n"
               "+        }\\n"
               "+    }\\n"
               "+}\\n"
               "+bintray {\\n"
               "+    user = project.hasProperty('bintray.user') ? project.property('bintray.user') : System.getenv('BINTRAY_USER')\\n"
               "+    key = project.hasProperty('bintray.apikey') ? project.property('bintray.apikey') : System.getenv('BINTRAY_API_KEY')\\n"
               "+    configurations = ['archives']\\n"
               "+    pkg {\\n"
               "+        repo = 'android'\\n"
               "+        name = projectName\\n"
               "+        userOrg = 'pixplicity'\\n"
               "+        licenses = ['Apache-2.0']\\n"
               "+        vcsUrl = 'https://github.com/Pixplicity/gene-rate'\\n"
               "+        version {\\n"
               "+            name = '1.0'\\n"
               "+            desc = 'Android Gene-rate Library 1.0'\\n"
               "+            released = new Date()\\n"
               "+            vcsTag = '1.0'\\n"
               "+        }\\n"
               "+    }\\n"
               "+}\\n"
               "Code Changes for 'library/build.gradle':\\n"
               "```diff\\n"
               "- install {\\n"
               "-    repositories.mavenInstaller {\\n"
               "-        pom.project {\\n"
               "-            name projectName\\n"
               "-            description 'Generate a better rating for your Android app'\\n"
               "-            url 'https://github.com/Pixplicity/gene-rate'\\n"
               "-            inceptionYear '2017'\\n"
               "-            packaging 'aar'\\n"
               "-            group 'com.pixplicity.generate'\\n"
               "-            groupId 'com.pixplicity.generate'\\n"
               "-            artifactId projectName\\n"
               "-            version '1.0.0'\\n"
               "-        }\\n"
               "-    }\\n"
               "-}\\n"
               "-bintray {\\n"
               "-    user = project.hasProperty('bintray.user') ? project.property('bintray.user') : System.getenv('BINTRAY_USER')\\n"
               "-    key = project.hasProperty('bintray.apikey') ? project.property('bintray.apikey') : System.getenv('BINTRAY_API_KEY')\\n"
               "-    configurations = ['archives']\\n"
               "-    pkg {\\n"
               "-        repo = 'android'\\n"
               "-        name = projectName\\n"
               "-        userOrg = 'pixplicity'\\n"
               "-        licenses = ['Apache-2.0']\\n"
               "-        vcsUrl = 'https://github.com/Pixplicity/gene-rate'\\n"
               "-        version {\\n"
               "-            name = '1.0'\\n"
               "-            desc = 'Android Gene-rate Library 1.0'\\n"
               "-            released = new Date()\\n"
               "-            vcsTag = '1.0'\\n"
               "-        }\\n"
               "-    }\\n"
               "-}\\n"
               "-apply plugin: 'com.github.dcendents.android-maven'\\n"
               "-apply plugin: 'com.jfrog.bintray'\\n"
	               "+apply from: new File('../bintray_upload.gradle')",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_AND_PULL_UP_MODULE","Output":"EXTRACT_AND_PULL_UP_MODULE: Extract and pull up module logic from 'library/build.gradle' to 'bintray_upload.gradle'"}]},
    "Explanation of the Example": "The common publishing and Bintray configuration is removed from 'library/build.gradle' and added to the new shared script 'bintray_upload.gradle'. The library build file now references that shared script using apply from. This centralizes reusable build logic in a common location, so the change is an extract-and-pull-up module refactoring."
        },

        {
    "RefactoringType": "EXTRACT_AND_PUSH_DOWN_MODULE",
    "Example": "'build.gradle':\\n"
               "```diff\\n"
               "-import java.text.DateFormat\\n"
               "-import java.text.SimpleDateFormat\\n"
               "-\\n"
               " buildscript {\\n"
               "     repositories {\\n"
               "         mavenCentral()\\n"
               "     }\\n"
               " }\\n"
               " \\n"
               "-def String getInputFromConsoleIfMissing(Properties props, String propKey, String propInputMessage) {\\n"
               "-    def propValue = props[propKey]\\n"
               "-    if (propValue == null || propValue == '') {\\n"
               "-        def con = System.console();\\n"
               "-        if (con != null){\\n"
               "-            propValue = con.readLine(\\"\\n\\"+propInputMessage+\\": \\")\\n"
               "-        }\\n"
               "-    }\\n"
               "-    return propValue\\n"
               "-}\\n"
               "-def boolean isEmpty(String value) {\\n"
               "-    if (value == null)\\n"
               "-        return true\\n"
               "-    else if (value == '')\\n"
               "-        return true\\n"
               "-    else\\n"
               "-        return false\\n"
               "-}\\n"
               "-def String getVersionPatchBuild() {\\n"
               "-    DateFormat dateFormat = new SimpleDateFormat(\\"yyyyMMdd\\")\\n"
               "-    Date date = new Date()\\n"
               "-    return dateFormat.format(date)\\n"
               "-}\\n"
               "-version \\"1.2.\\"+getVersionPatchBuild()\\n"
               "+version utils.Utils.buildVersionName(1, 2, true)\\n"
               " group 'net.evendanan'\\n"
               " \\n"
               "+def localproperties.LocalPropertiesFile localExtraProperties = new localproperties.LocalPropertiesFile(new File(projectDir, 'local.extra.properties'))\\n"
               "+def localproperties.LocalPropertiesFile localSigningProperties = new localproperties.LocalPropertiesFile(new File(projectDir, 'local.signing.properties'))\\n"
               "+\\n"
               " android {\\n"
               "     compileSdkVersion 19\\n"
               "     buildToolsVersion \\"19.0.1\\"\\n"
               " \\n"
               "     defaultConfig {\\n"
               "         versionCode 122\\n"
               "-        versionName \\"1.2.\\"+getVersionPatchBuild()\\n"
               "+        versionName project.version\\n"
               " \\n"
               "         minSdkVersion 7\\n"
               "         targetSdkVersion 19\\n"
               "         //additional details\\n"
               "+        String support_email_address = localExtraProperties.getValueWithConsoleFallback('CRASH_REPORT_EMAIL', 'ooops@example.com', 'Support email address')\\n"
               "         println 'crash report email is: '+support_email_address\\n"
               " \\n"
               "         buildConfigField \\"String\\", \\"CRASH_REPORT_EMAIL_ADDRESS\\", '\\"'+support_email_address+'\\"'\\n"
               " }\\n"
	               "```",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_AND_PUSH_DOWN_MODULE","Output":"EXTRACT_AND_PUSH_DOWN_MODULE: Extract and push down module logic from 'build.gradle' to 'buildSrc/src/main/groovy'"}]},
    "Explanation of the Example": "The versioning, local-property, and signing-related build logic is removed from the general 'build.gradle' file and moved into more specialized build-support files used by the relevant subproject. The root build file is left with references to the extracted utilities, so the larger structural change is an extract-and-push-down module refactoring."
        },

        {
    "RefactoringType": "EXTRACT_MODULE",
    "Example": "Code Changes for 'publishing.gradle':\\n```diff\\n+apply plugin: 'maven-publish'\\n+publishing {\\n+  publications { release(MavenPublication) { artifactId = project.name } }\\n+}\\n```\\nCode Changes for 'build.gradle':\\n```diff\\n-apply plugin: 'maven-publish'\\n-publishing {\\n-  publications { release(MavenPublication) { artifactId = project.name } }\\n-}\\n+apply from: 'publishing.gradle'\\n```",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_MODULE","Output":"EXTRACT_MODULE: Extract module logic from 'build.gradle' to 'publishing.gradle'"}]},
    "Explanation of the Example": "The publishing build logic is removed from 'build.gradle' and placed in a newly created peer Gradle script named 'publishing.gradle'. Because the source and target files are at the same hierarchy level, this is an extract module refactoring rather than pull up or push down."
         },

    {
    "RefactoringType": "EXTRACT_METHOD",
    "Example": "'build.gradle':\\n```diff\\n@@ -35,7 +42,18 @@\\n"
               "-\\tdef proc = new ProcessBuilder(\\"git\\", \\"rev-parse\\", \\"HEAD\\").directory(buildDir).start()\\n"
               "-\\tdef exitCode = proc.waitFor()\\n"
               "-\\tif (exitCode != 0) {\\n"
               "-\\t\\tthrow new IllegalStateException(\\"Command exited with status code ${exitCode}:\\\\n\\" + proc.err.readLines().join(\\"\\\\n\\"))\\n"
               "-\\t}\\n"
               "-\\tdef hash = proc.in.readLines()[0]\\n"
               "-\\tf.append(\\"commit=${hash}\\\\n\\")\\n"
               "+def readCurrentGitCommit() {\\n"
               "+\\tdef proc = [\\"git\\", \\"rev-parse\\", \\"HEAD\\"].execute(null, project.projectDir)\\n"
               "+\\tdef exitCode = proc.waitFor()\\n"
               "+\\tif (exitCode != 0) {\\n"
               "+\\t\\tthrow new IllegalStateException(\\"git rev-parse command exited with status code ${exitCode}:\\\\n\\" + proc.err.readLines().join(\\"\\\\n\\"))\\n"
               "+\\t}\\n"
               "+\\treturn proc.in.readLines()[0]\\n"
               "+}\\n"
               "\\n"
               "processResources {\\n"
               "\\tdoFirst {\\n"
               "\\t\\tdef f = file(\\"src/main/resources/de/prob2/ui/build.properties\\")\\n"
               "\\t\\tf.delete()\\n"
               "\\t\\tf.append(\\"version=${project.version}\\\\n\\")\\n"
               "+\\t\\tf.append(\\"commit=${readCurrentGitCommit()}\\\\n\\")\\n"
               "\\t}\\n"
	               "}\\n```\\n",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_METHOD","Output":"EXTRACT_METHOD: Extract method 'build.gradle:readCurrentGitCommit (42)'"}]},
    "Explanation of the Example": "The inline logic for reading the current Git commit is moved into a new method named 'readCurrentGitCommit' in the same build file. The original use site now calls that method, preserving behavior while making the build logic reusable."
},

{
    "RefactoringType": "PULL_UP_METHOD",
    "Example": "'build.gradle':\\n"
        "```diff\\n"
        "@@ -25,0 +28,17 @@\\n"
        "+import org.gradle.internal.jvm.Jvm\\n"
        "+\\n"
        "// Top-level build file where you can add configuration options common to all sub-projects/modules.\\n\\n"
        "buildscript {\\n"
        "...\\n"
        "+def getRuntimeJar() {\\n"
        "+    try {\\n"
        "+        final File javaBase = new File(System.getProperty(\\"java.home\\")).getCanonicalFile();\\n"
        "+        File runtimeJar = new File(javaBase, \\"lib/rt.jar\\");\\n"
        "+        if (runtimeJar.exists()) {\\n"
        "+            return runtimeJar;\\n"
        "+        }\\n"
        "+        runtimeJar = new File(javaBase, \\"jre/lib/rt.jar\\");\\n"
        "+        return runtimeJar.exists() ? runtimeJar : null;\\n"
        "+    } catch (IOException e) {\\n"
        "+        throw new RuntimeException(e);\\n"
        "+    }\\n"
        "+}\\n"
        "...\\n"
        "```\\n"
        "Changes in 'storio-content-resolver-annotations-processor-test/build.gradle' and 'storio-sqlite-annotations-processor-test/build.gradle':\\n"
        "```diff\\n"
        "@@ -18,18 +18,0 @@\\n"
        "-import org.gradle.internal.jvm.Jvm\\n"
        "-\\n"
        "-def getRuntimeJar() {\\n"
        "-    try {\\n"
        "-        final File javaBase = new File(System.getProperty(\\"java.home\\")).getCanonicalFile();\\n"
        "-        File runtimeJar = new File(javaBase, \\"lib/rt.jar\\");\\n"
        "-        if (runtimeJar exists()) {\\n"
        "-            return runtimeJar;\\n"
        "-        }\\n"
        "-        runtimeJar = new File(javaBase, \\"jre/lib/rt.jar\\");\\n"
        "-        return runtimeJar.exists() ? runtimeJar : null;\\n"
        "-    } catch (IOException e) {\\n"
        "-        throw new RuntimeException(e);\\n"
        "-    }\\n"
        "-}\\n"
	        "```\\n",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PULL_UP_METHOD","Output":"PULL_UP_METHOD: Pull up method 'getRuntimeJar' to 'build.gradle:getRuntimeJar (28)'"}]},
    "Explanation of the Example": "The existing method 'getRuntimeJar' is removed from specialized subproject build files and added to the parent 'build.gradle' file with the same behavior. This pulls the shared method up to a common location where multiple subprojects can use it."
},

{
    "RefactoringType": "EXTRACT_TASK",
    "Example": "'build.gradle':\\n"
        "```diff\\n"
        "@@ -28,4 +31,8 @@\\n"
        "-copy {\\n"
        "-  from 'src/docs'\\n"
        "-  into \\"$buildDir/docs\\"\\n"
        "-}\\n"
        "+task prepareDocs {\\n"
        "+  copy {\\n"
        "+    from 'src/docs'\\n"
        "+    into \\"$buildDir/docs\\"\\n"
        "+  }\\n"
        "+}\\n"
        "+build.dependsOn prepareDocs\\n"
	        "```",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_TASK","Output":"EXTRACT_TASK: Extract task 'build.gradle:prepareDocs (31)'"}]},
    "Explanation of the Example": "The inline copy logic is moved into a new Gradle task named 'prepareDocs'. The build now depends on that task, so the original build logic is extracted into a reusable task."
},

{
    "RefactoringType": "PUSH_DOWN_TASK",
    "Example": "'build.gradle':\\n"
        "```diff\\n"
        "@@ -18,4 +18,0 @@\\n"
        "-task publishDocs {\\n"
        "-  from 'docs'\\n"
        "-  into \\"$buildDir/docs\\"\\n"
        "-}\\n"
        "```\\n"
        "'docs/build.gradle':\\n"
        "```diff\\n"
        "@@ -0,0 +4,4 @@\\n"
        "+task publishDocs {\\n"
        "+  from 'docs'\\n"
        "+  into \\"$buildDir/docs\\"\\n"
        "+}\\n"
	        "```",
    "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PUSH_DOWN_TASK","Output":"PUSH_DOWN_TASK: Push down task 'build.gradle:publishDocs (18)' to 'docs/build.gradle:publishDocs (4)'"}]},
    "Explanation of the Example": "The existing Gradle task 'publishDocs' is removed from the parent 'build.gradle' file and added to the child 'docs/build.gradle' file with the same task identity and behavior, so the task is pushed down to a more specialized build file."
},


    {
        "RefactoringType": "DRY",
        "Example": "'examples/HBaseGroovy/build.gradle':\\n```diff\\n@@ -41,26 +41,17 @@\\nProperties props = new Properties()\\nprops.load(new FileInputStream(\\"$projectDir/../../connection.properties\\"))\\n\\n-// task to run the groovy script\\ntask('Connect', type: JavaExec) {\\n+['Connect', 'ManipulateSchemaAndPerformCRUD'].each { taskName ->\\n    task \\"$taskName\\" (type: JavaExec) {\\n    environment 'gateway', props.gateway\\n    environment 'username', props.username\\n    environment 'password', props.password\\n    main = taskName\\n    classpath = sourceSets.main.runtimeClasspath\\n   }\\n}\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"DRY","Output":"DRY: Consolidate duplicated logic into 'examples/HBaseGroovy/build.gradle:each (41)'"}]},
        "Explanation of the Example": "The repeated JavaExec task configuration for multiple tasks is replaced by one generalized loop over task names. The same task behavior is preserved, but the duplicated Gradle logic is consolidated into a shared representation."
    },
    {
        "RefactoringType": "PUSH_DOWN_DEPENDENCY",
        "Example": "'build.gradle':\\n```diff\\n@@ -12,3 +12,0 @@\\n- dependencies {\\n-   implementation 'com.squareup.retrofit2:retrofit:2.9.0'\\n- }\\n```\\n'app/build.gradle':\\n```diff\\n@@ -10,0 +12,3 @@\\n+ dependencies {\\n+   implementation 'com.squareup.retrofit2:retrofit:2.9.0'\\n+ }\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PUSH_DOWN_DEPENDENCY","Output":"PUSH_DOWN_DEPENDENCY: Push down dependency 'app/build.gradle:implementation (12)'"}]},
        "Explanation of the Example": "The same Retrofit dependency declaration is removed from the parent 'build.gradle' file and added to the child 'app/build.gradle' file, so the dependency is pushed down to a more specialized build file."
    },
    {
        "RefactoringType": "PULL_UP_DEPENDENCY",
        "Example": "'app/build.gradle':\\n```diff\\n- dependencies {\\n-   testImplementation 'junit:junit:4.13.2'\\n- }\\n```\\n'build.gradle':\\n```diff\\n+ dependencies {\\n+   testImplementation 'junit:junit:4.13.2'\\n+ }\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PULL_UP_DEPENDENCY","Output":"PULL_UP_DEPENDENCY: Pull up dependency 'junit:junit:4.13.2'"}]},
        "Explanation of the Example": "The same JUnit dependency declaration is removed from the child 'app/build.gradle' file and added to the parent 'build.gradle' file, so the dependency is pulled up to a broader build file."
    },
    {
        "RefactoringType": "MOVE_DEPENDENCY",
        "Example": "'android.gradle':\\n```diff\\n@@ -9,3 +9,0 @@\\n- dependencies {\\n-   implementation 'com.google.guava:guava:32.0.0'\\n- }\\n```\\n'jvm.gradle':\\n```diff\\n@@ -7,0 +9,3 @@\\n+ dependencies {\\n+   implementation 'com.google.guava:guava:32.0.0'\\n+ }\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"MOVE_DEPENDENCY","Output":"MOVE_DEPENDENCY: Move dependency 'jvm.gradle:implementation (9)'"}]},
        "Explanation of the Example": "The same Guava dependency declaration is removed from 'android.gradle' and added to the peer file 'jvm.gradle'. Because the source and target files are at the same hierarchy level, this is a move dependency refactoring rather than pull up or push down."
    },
    {
        "RefactoringType": "EXTERNALIZE_PROPERTIES",
        "Example": "'build.gradle':\\n```diff\\n- version = '1.7.10'\\n+ version = mc_version\\n```\\n'build.properties':\\n```diff\\n+ mc_version=1.7.10\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTERNALIZE_PROPERTIES","Output":"EXTERNALIZE_PROPERTIES: Externalize property 'mc_version'"}]},
        "Explanation of the Example": "The version value is moved from the Gradle build script into 'build.properties', and the build script now reads the single external property 'mc_version'."
    },
    {
        "RefactoringType": "EXTRACT_VARIABLE",
        "Example": "'build.gradle':\\n```diff\\n@@ -12,1 +12,2 @@\\n- implementation 'com.android.support:appcompat-v7:28.0.0'\\n+ def supportLibVersion = '28.0.0'\\n+ implementation \\"com.android.support:appcompat-v7:${supportLibVersion}\\"\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_VARIABLE","Output":"EXTRACT_VARIABLE: Extract variable 'build.gradle:supportLibVersion (12)'"}]},
        "Explanation of the Example": "The literal version '28.0.0' is extracted into the new variable 'supportLibVersion' in the same build file, and the dependency declaration now references that variable."
    },
    
    {
        "RefactoringType": "INLINE_VARIABLE",
        "Example": "'build.gradle':\\n```diff\\n@@ -21,2 +22,1 @@\\n- def junitVersion = '4.13.2'\\n- testImplementation \\"junit:junit:${junitVersion}\\"\\n+ testImplementation 'junit:junit:4.13.2'\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"INLINE_VARIABLE","Output":"INLINE_VARIABLE: Inline variable 'junitVersion' in 'build.gradle:testImplementation (22)'"}]},
        "Explanation of the Example": "The dependency declaration no longer references 'junitVersion'; it now contains the concrete version value '4.13.2' directly. The inline-variable instance is reported at the dependency declaration where the reference was replaced."
    },
    {
        "RefactoringType": "PULL_UP_VARIABLE",
        "Example": "'app/build.gradle':\\n```diff\\n@@ -8,1 +8,0 @@\\n- def minSdkVersion = 23\\n```\\n'build.gradle':\\n```diff\\n@@ -7,0 +8,1 @@\\n+ def minSdkVersion = 23\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PULL_UP_VARIABLE","Output":"PULL_UP_VARIABLE: Pull up variable to 'build.gradle:minSdkVersion (8)'"}]},
        "Explanation of the Example": "The existing variable 'minSdkVersion' is removed from the child 'app/build.gradle' file and added to the parent 'build.gradle' file with the same name and value, so the variable is pulled up rather than newly extracted."
    },
    {
        "RefactoringType": "PUSH_DOWN_VARIABLE",
        "Example": "'build.gradle':\\n```diff\\n@@ -7,1 +7,0 @@\\n- def appCompatVersion = '1.6.1'\\n```\\n'app/build.gradle':\\n```diff\\n@@ -6,0 +7,1 @@\\n+ def appCompatVersion = '1.6.1'\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"PUSH_DOWN_VARIABLE","Output":"PUSH_DOWN_VARIABLE: Push down variable 'build.gradle:appCompatVersion (7)'"}]},
        "Explanation of the Example": "The existing variable 'appCompatVersion' is removed from the parent 'build.gradle' file and added to the child 'app/build.gradle' file with the same name and value, so the variable is pushed down rather than newly extracted."
    },
    {
         "RefactoringType": "MOVE_VARIABLE",
         "Example": "'android.gradle':\\n```diff\\n@@ -6,1 +6,0 @@\\n- def kotlinVersion = '1.9.22'\\n```\\n'jvm.gradle':\\n```diff\\n@@ -5,0 +6,1 @@\\n+ def kotlinVersion = '1.9.22'\\n```\\n",
         "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"MOVE_VARIABLE","Output":"MOVE_VARIABLE: Move variable 'android.gradle:kotlinVersion (6)' to 'jvm.gradle:kotlinVersion (6)'"}]},
         "Explanation of the Example": "The existing variable 'kotlinVersion' is removed from 'android.gradle' and added to the peer file 'jvm.gradle' with the same name and value. Because the source and target files are at the same hierarchy level, this is MOVE_VARIABLE rather than PULL_UP_VARIABLE or PUSH_DOWN_VARIABLE."
    },
    {
        "RefactoringType": "EXTRACT_AND_MOVE_VARIABLE",
        "Example": "'android.gradle':\\n```diff\\n@@ -4,1 +4,1 @@\\n- implementation 'com.example:core:1.0.0'\\n+ implementation \\"com.example:core:${coreVersion}\\"\\n```\\n'jvm.gradle':\\n```diff\\n@@ -3,0 +4,1 @@\\n+ def coreVersion = '1.0.0'\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_AND_MOVE_VARIABLE","Output":"EXTRACT_AND_MOVE_VARIABLE: Extract and move variable 'jvm.gradle:coreVersion (4)'"}]},
        "Explanation of the Example": "The literal version '1.0.0' is extracted from the dependency declaration into a new variable named 'coreVersion'. The new variable is placed in the peer Gradle file 'jvm.gradle', so the change is an extract-and-move variable refactoring rather than a same-file extract or a pull-up/push-down."
    },
    {
        "RefactoringType": "EXTRACT_AND_PULL_UP_VARIABLE",
        "Example": "'app/build.gradle':\\n```diff\\n@@ -5,1 +5,1 @@\\n- compileSdkVersion 34\\n+ compileSdkVersion rootProject.ext.compileSdkVersion\\n```\\n'build.gradle':\\n```diff\\n@@ -4,0 +5,1 @@\\n+ ext.compileSdkVersion = 34\\n```\\n",
        "ExpectedOutput": {"DetectedRefactoringTypes":[{"RefactoringType":"EXTRACT_AND_PULL_UP_VARIABLE","Output":"EXTRACT_AND_PULL_UP_VARIABLE: Extract and pull up variable 'build.gradle:compileSdkVersion (5)'"}]},
        "Explanation of the Example": "The literal value '34' is extracted from the child 'app/build.gradle' file into a new parent-level variable named 'compileSdkVersion' in 'build.gradle'. The child build file now references that shared variable, so this is an extract-and-pull-up variable refactoring."
    }
]
          """;

    private OneShotExamples() {
    }
}
