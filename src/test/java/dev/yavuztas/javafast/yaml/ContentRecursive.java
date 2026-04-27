package dev.yavuztas.javafast.yaml;

public record ContentRecursive(String title, ContentRecursive content, ContentRecursive[] contents) {
}
