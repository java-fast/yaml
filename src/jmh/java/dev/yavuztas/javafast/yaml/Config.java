package dev.yavuztas.javafast.yaml;

public record Config(Setting setting1, Setting setting2) {

    public record Setting(String version, String url) {}

}
