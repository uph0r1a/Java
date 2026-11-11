package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 6.15: the pom's {@code modular-image} profile (moditect gives the MariaDB driver a module descriptor, then links the program folder)
 * could never be run where it was written, so what can be checked without Maven is checked here: the pom is well-formed XML (an XML
 * comment may not contain two hyphens, which broke the first draft), the profile is not switched on by default, the driver version is the same
 * as the dependency's, the module name is the one {@code module-info.java} requires, and the launcher, the folder and the zip carry the same
 * names as the {@code javafx:jlink} settings. It does not run Maven or jlink.
 */
public class ModularImageProfileTest {

    private static final String LAUNCHER_MODULE = "com.coffeeshop.coffeeshopmanagement/com.coffeeshop.coffeeshopmanagement.LunaveraApplication";

    private static Document pom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    }

    private static String xp(Document pom, String expression) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        return ((String) xpath.evaluate("normalize-space(" + expression + ")", pom, XPathConstants.STRING));
    }

    private static final String PROFILE = "//*[local-name()='profile'][*[local-name()='id']='modular-image']";

    @Test
    public void thePomIsWellFormedXml() throws Exception {
        assertNotNull(pom().getDocumentElement());
    }

    @Test
    public void noCommentInThePomHasTwoHyphensInside() throws IOException {
        String text = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?s)<!--(.*?)-->").matcher(text);
        while (m.find()) assertFalse("an XML comment may not contain two hyphens: " + m.group(1).strip().substring(0, 40), m.group(1).contains("--"));
    }

    @Test
    public void theProfileExistsAndIsNotSwitchedOnByDefault() throws Exception {
        Document pom = pom();
        assertEquals("modular-image", xp(pom, PROFILE + "/*[local-name()='id']"));
        assertEquals("no activation section at all", "", xp(pom, PROFILE + "/*[local-name()='activation']"));
    }

    @Test
    public void theDriverGetsItsDescriptorAtTheSameVersionAsTheDependency() throws Exception {
        Document pom = pom();
        String dependency = xp(pom, "/*/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='mariadb-java-client']/*[local-name()='version']");
        String processed = xp(pom, PROFILE + "//*[local-name()='module']/*[local-name()='artifact']/*[local-name()='version']");
        assertFalse(dependency.isEmpty());
        assertEquals("moditect must work on the jar the program is built with", dependency, processed);
        assertEquals("mariadb-java-client", xp(pom, PROFILE + "//*[local-name()='module']/*[local-name()='artifact']/*[local-name()='artifactId']"));
    }

    @Test
    public void theModuleNameIsTheOneModuleInfoRequires() throws Exception {
        String name = xp(pom(), PROFILE + "//*[local-name()='moduleInfo']/*[local-name()='name']");
        String moduleInfo = Files.readString(Path.of("src", "main", "java", "module-info.java"), StandardCharsets.UTF_8);
        assertTrue(name + " must be required by module-info.java", moduleInfo.contains("requires " + name + ";"));
        assertEquals("org.mariadb.jdbc", name);
    }

    @Test
    public void thePlainDriverJarIsNotCopiedBesideTheDescribedOne() throws Exception {
        Document pom = pom();
        assertEquals("mariadb-java-client", xp(pom, PROFILE + "//*[local-name()='excludeArtifactIds']"));
        assertEquals("runtime", xp(pom, PROFILE + "//*[local-name()='includeScope']"));
        assertEquals("only the program's own jar is added by moditect", "APP", xp(pom, PROFILE + "//*[local-name()='jarInclusionPolicy']"));
        String modulesFolder = xp(pom, PROFILE + "//*[local-name()='goal'][.='copy-dependencies']/../../*[local-name()='configuration']/*[local-name()='outputDirectory']");
        String describedFolder = xp(pom, PROFILE + "//*[local-name()='goal'][.='add-module-info']/../../*[local-name()='configuration']/*[local-name()='outputDirectory']");
        assertEquals("both write to the same folder", modulesFolder, describedFolder);
        assertEquals(modulesFolder, xp(pom, PROFILE + "//*[local-name()='modulePath']/*[local-name()='path']"));
    }

    @Test
    public void theLauncherTheFolderAndTheZipKeepTheNamesOfTheJlinkSettings() throws Exception {
        Document pom = pom();
        String jlinkLauncher = xp(pom, "//*[local-name()='plugin'][*[local-name()='artifactId']='javafx-maven-plugin']//*[local-name()='launcher']");
        String jlinkMain = xp(pom, "//*[local-name()='plugin'][*[local-name()='artifactId']='javafx-maven-plugin']//*[local-name()='mainClass']");
        assertEquals("lunavera", jlinkLauncher);
        assertEquals("lunavera", xp(pom, PROFILE + "//*[local-name()='launcher']/*[local-name()='name']"));
        assertEquals(LAUNCHER_MODULE, jlinkMain);
        assertEquals("the same module and main class as javafx:run and javafx:jlink", jlinkMain, xp(pom, PROFILE + "//*[local-name()='launcher']/*[local-name()='module']"));
        assertEquals("${project.build.directory}/lunavera", xp(pom, PROFILE + "//*[local-name()='create-runtime-image' or local-name()='id'][.='create-runtime-image']/../*[local-name()='configuration']/*[local-name()='outputDirectory']"));
        String antrun = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue("target/lunavera.zip as before", antrun.contains("${project.build.directory}/lunavera.zip"));
        assertEquals("the program module is what jlink starts from", "com.coffeeshop.coffeeshopmanagement",
                xp(pom, PROFILE + "//*[local-name()='modules']/*[local-name()='module'][.='com.coffeeshop.coffeeshopmanagement']"));
    }

    @Test
    public void theReleaseWorkflowBuildsWithTheProfileAndChecksTheFolder() throws IOException {
        String release = Files.readString(Path.of(".github", "workflows", "release.yml"), StandardCharsets.UTF_8);
        assertTrue("the build uses the profile", release.contains("-Pmodular-image"));
        assertFalse("the build command must not be javafx:jlink, which stops on the automatic MariaDB module", release.contains("package javafx:jlink"));
        assertTrue(release.contains("java tools/ModuleCheck.java target/modules"));
        assertTrue(release.contains("java tools/ImageCheck.java target/lunavera"));
        assertTrue("the checks come before the installer is built", release.indexOf("tools/ImageCheck.java") < release.indexOf("jpackage --type msi"));
    }
}
