package dev.yavuztas.javafast.yaml;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class YamlParserTests {

    @Test
    void testYamlParse() {
        System.out.println("Content1 ------------------------------------------------------------");
        final Path path1 = Path.of("src/test/resources/test1.yaml");
        final Yaml yaml = Yaml.of().data(path1);
        final Content content = yaml.parse(Content.class);
        System.out.println(content);

        assertEquals("String Concatenation Test 1", content.title());
        assertEquals("String Concatenation (+=)", content.classicApproach());
        assertEquals("StringBuilder", content.optimizedApproach());
        assertEquals("~=800x", content.howFast());
        assertEquals("summary...", content.summary());
        assertEquals("""
            Records: automatically generate the constructor, accessors (x(), y()),\s
            equals(), hashCode(), and toString().\\" They are immutable by design and ideal for\s
            DTOs, value objects, and pattern matching.""", content.explanation());

        System.out.println(Arrays.toString(content.whenNotToUse()));

        assertEquals(2, content.whenNotToUse().length);
        assertEquals("If multiple threads access the same StringBuilder instance concurrently, do not use it.", content.whenNotToUse()[0]);
        assertEquals("Consider StringBuffer instead.", content.whenNotToUse()[1]);

        assertNotNull(content.config());
        assertEquals("1.0", content.config().setting1().version());
        assertEquals("text", content.config().setting1().url());
        assertEquals("2.0", content.config().setting2().version());
        assertEquals("", content.config().setting2().url());

        assertEquals("booo", content.test());

        System.out.println(Arrays.toString(content.whyOptimizedWins()));

        assertEquals(2, content.whyOptimizedWins().length);
        assertEquals("icon1", content.whyOptimizedWins()[0].icon());
        assertEquals("my-title", content.whyOptimizedWins()[0].title());
        assertEquals("description...", content.whyOptimizedWins()[0].desc());
        assertEquals("icon2", content.whyOptimizedWins()[1].icon());
        assertEquals("my-title2", content.whyOptimizedWins()[1].title());
        assertEquals("description...", content.whyOptimizedWins()[1].desc());

        // check internal cursors
        assertEquals(17, yaml.propIndex);
        assertEquals(22, yaml.propCursor);
        assertEquals(19, yaml.tokenCount);

        System.out.println("Content2 ------------------------------------------------------------");
        final Path path2 = Path.of("src/test/resources/test2.yaml");
        final Content content2 = Yaml.of().data(path2).parse(Content.class);
        System.out.println(content2);

        assertEquals(2, content2.whenNotToUse().length);
        assertEquals("If multiple threads access the same StringBuilder instance concurrently, do not use it.", content.whenNotToUse()[0]);
        assertEquals("Consider StringBuffer instead.", content2.whenNotToUse()[1]);

        assertNull(content2.config());
        assertNull(content2.test());
        assertNull(content2.whyOptimizedWins());
    }

    @Test
    void testRecursiveObjects() {
        final Path path = Path.of("src/test/resources/content-recursive.yaml");
        final ContentRecursive recursive = Yaml.of().data(path).parse(ContentRecursive.class);
        System.out.println(recursive);

        assertEquals("Recursive Content Test 1", recursive.title());
        assertNotNull(recursive.content());
        assertEquals("Recursive Content 1", recursive.content().title());
        assertNull(recursive.content().content());
        assertNotNull(recursive.content().contents());
        assertEquals(0, recursive.content().contents().length);

        assertEquals(2, recursive.contents().length);
        assertEquals("Array Recursive Content 1", recursive.contents()[0].title());
        assertNull(recursive.contents()[0].content());
        assertEquals(0, recursive.contents()[0].contents().length);
        assertEquals("Array Recursive Content 2", recursive.contents()[1].title());
        assertNull(recursive.contents()[1].content());
        assertEquals(0, recursive.contents()[1].contents().length);
    }

}
