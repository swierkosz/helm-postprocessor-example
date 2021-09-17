package com.example.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.join;

public class Application {

    public static void main(String... args) throws Exception {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper om = new ObjectMapper(new YAMLFactory());

        YAMLParser yamlParser = yamlFactory.createParser(System.in);
        List<ObjectNode> documents = om.readValues(yamlParser, new TypeReference<ObjectNode>() {
                })
                .readAll();

        Map<String, String> configMapChecksums = prepareConfigMapChecksums(documents);

        updateDeployments(documents, configMapChecksums);

        for (ObjectNode doc : documents) {
            System.out.println(om.writeValueAsString(doc));
        }
    }

    private static Map<String, String> prepareConfigMapChecksums(List<ObjectNode> documents) throws NoSuchAlgorithmException {
        Map<String, String> configMaps = new HashMap<>();
        for (ObjectNode document : documents) {
            String kind = textValue(path(document, "kind"));
            String name = textValue(path(document, "metadata", "name"));
            JsonNode data = path(document, "data");

            if ("ConfigMap".equals(kind) && name != null && data != null) {
                configMaps.put(name, md5sum(data.toString()));
            }
        }
        return configMaps;
    }

    private static void updateDeployments(List<ObjectNode> documents, Map<String, String> configMapChecksums) {
        for (ObjectNode document : documents) {
            String kind = textValue(path(document, "kind"));
            ObjectNode labelsNode = (ObjectNode) path(document, "spec", "template", "metadata", "labels");
            JsonNode checksumNode = path(labelsNode, "config-checksum");

            if (!"Deployment".equals(kind) || checksumNode == null) {
                continue;
            }
            JsonNode volumesNode = path(document, "spec", "template", "spec", "volumes");
            if (volumesNode == null || !volumesNode.isArray()) {
                continue;
            }
            List<String> checksums = new ArrayList<>();
            for (JsonNode volumeNode : volumesNode) {
                String configMapName = textValue(path(volumeNode, "configMap", "name"));
                if (configMapName != null) {
                    checksums.add(configMapChecksums.get(configMapName));
                }
            }
            labelsNode.replace("config-checksum", new TextNode(join(",", checksums)));
        }
    }

    private static JsonNode path(JsonNode node, String... path) {
        JsonNode currentNode = node;
        for (String pathElement : path) {
            if (currentNode == null) {
                return null;
            }
            currentNode = currentNode.get(pathElement);
        }
        return currentNode;
    }

    private static String textValue(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        } else {
            return jsonNode.textValue();
        }
    }

    private static String md5sum(String data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data.getBytes());
        return Base64.getEncoder().encodeToString(digest);
    }

}
