package dev.yavuztas.javafast.yaml;

import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class YamlParseBenchmark {

    byte[] data;
    byte[] dataSnake;
    Yaml yaml;
    org.yaml.snakeyaml.Yaml snake;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        this.data = Files.readAllBytes(Path.of("src/jmh/resources/test.yaml"));
        this.dataSnake = Files.readAllBytes(Path.of("src/jmh/resources/test-snake.yaml"));
        this.yaml = Yaml.of();
        this.snake = new org.yaml.snakeyaml.Yaml();
    }

    @Benchmark
    public void parseCustom() {
        this.yaml.data(this.data).parse(Content.class);
    }

    @Benchmark
    public void parseWithSnakeYaml() {
        this.snake.load(new ByteArrayInputStream(this.dataSnake));
    }

}
