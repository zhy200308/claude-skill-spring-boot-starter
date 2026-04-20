package com.claude.skill;

import com.claude.skill.core.skill.SkillParser;
import com.claude.skill.model.SkillPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkillParserTest {

    private final SkillParser parser = new SkillParser();

    @Test
    void testParseBasicSkill() throws IOException {
        String skillMd = "---\nname: test-skill\ndescription: A test skill\n---\n\n# Instructions\nDo something.";
        byte[] zip = buildZip("test-skill/SKILL.md", skillMd);

        SkillPackage pkg = parser.parse(new ByteArrayInputStream(zip), "test-skill.skill");

        assertEquals("test-skill", pkg.getName());
        assertEquals("A test skill", pkg.getDescription());
        assertTrue(pkg.getSystemPrompt().contains("# Instructions"));
    }

    @Test
    void testParseWithReferences() throws IOException {
        String skillMd    = "---\nname: full-skill\ndescription: Full skill\n---\n\n# Main";
        String refContent = "# Reference Content\nSome reference.";

        byte[] zip = buildZip(
            new String[]{"full-skill/SKILL.md", "full-skill/references/ref1.md"},
            new String[]{skillMd, refContent}
        );

        SkillPackage pkg = parser.parse(new ByteArrayInputStream(zip), "full-skill.skill");

        assertEquals("full-skill", pkg.getName());
        assertTrue(pkg.getSystemPrompt().contains("# Main"));
        assertTrue(pkg.getSystemPrompt().contains("# Reference Content"));
        assertTrue(pkg.getSystemPrompt().contains("Reference: references/ref1.md"));
    }

    @Test
    void testMultilineDescription() throws IOException {
        String skillMd = "---\nname: multi\ndescription: >\n  Line one\n  line two\n---\n# Body";
        byte[] zip = buildZip("multi/SKILL.md", skillMd);

        SkillPackage pkg = parser.parse(new ByteArrayInputStream(zip), "multi.skill");
        assertTrue(pkg.getDescription().contains("Line one"));
    }

    @Test
    void testMissingSkillMdThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            byte[] zip = buildZip("other/NOTES.md", "Some notes");
            parser.parse(new ByteArrayInputStream(zip), "bad.skill");
        });
    }

    @Test
    void testToToolDefinition() throws IOException {
        String skillMd = "---\nname: tool-skill\ndescription: Tool desc\n---\n# Instructions";
        byte[] zip = buildZip("tool-skill/SKILL.md", skillMd);

        SkillPackage pkg = parser.parse(new ByteArrayInputStream(zip), "tool-skill.skill");
        var tool = pkg.toToolDefinition();

        assertEquals("tool-skill", tool.get("name"));
        assertEquals("Tool desc", tool.get("description"));
        assertNotNull(tool.get("input_schema"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private byte[] buildZip(String entryName, String content) throws IOException {
        return buildZip(new String[]{entryName}, new String[]{content});
    }

    private byte[] buildZip(String[] names, String[] contents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < names.length; i++) {
                zos.putNextEntry(new ZipEntry(names[i]));
                zos.write(contents[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
