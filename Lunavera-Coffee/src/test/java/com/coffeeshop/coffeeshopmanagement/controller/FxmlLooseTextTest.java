package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * A plain XML parse accepts an FXML file whose opening {@code <Button} was deleted: the rest of the tag is then just text
 * inside its parent, and {@code FXMLLoader} stops on it when the screen is opened ({@link FxmlControllerCheckTest} reads the
 * files as text and never saw it). This test parses every FXML file and fails when an element that holds other elements
 * also holds loose text.
 */
public class FxmlLooseTextTest {

    private static final Path FXML_DIR = Path.of("src", "main", "resources", "fxml");

    @Test
    public void noFxmlContainerHoldsLooseText() throws Exception {
        List<Path> files;
        try (Stream<Path> list = Files.list(FXML_DIR)) {
            files = list.filter(p -> p.toString().endsWith(".fxml")).sorted().collect(Collectors.toList());
        }
        assertTrue("expected the FXML files, found " + files.size(), files.size() >= 9);
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            Document doc;
            try (InputStream in = Files.newInputStream(file)) {
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            }
            collect(file.getFileName().toString(), doc.getDocumentElement(), problems);
        }
        assertTrue("loose text inside a container element: " + problems, problems.isEmpty());
    }

    private static void collect(String file, Element element, List<String> problems) {
        boolean hasChildElements = false;
        StringBuilder text = new StringBuilder();
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) hasChildElements = true;
            else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) text.append(n.getNodeValue());
        }
        String loose = text.toString().strip();
        if (hasChildElements && !loose.isEmpty()) {
            String shown = loose.length() > 60 ? loose.substring(0, 60) + "..." : loose;
            problems.add(file + " <" + element.getTagName() + ">: " + shown.replaceAll("\\s+", " "));
        }
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) collect(file, (Element) n, problems);
        }
    }
}
