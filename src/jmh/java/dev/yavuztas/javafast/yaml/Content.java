package dev.yavuztas.javafast.yaml;

public record Content(
    String title,
    String classicApproach,
    String optimizedApproach,
    String howFast,
    String summary,
    String explanation,
    String[] whenNotToUse,
    Config config,
    String test,
    WhyOptimizedWins[] whyOptimizedWins
) {
}
