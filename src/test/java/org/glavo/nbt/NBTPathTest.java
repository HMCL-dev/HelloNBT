/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.nbt;

import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NBTPathTest {

    private static CompoundTag createSampleRoot() {
        return new CompoundTag()
                .addTag("player", new CompoundTag()
                        .setString("name", "Alex")
                        .setInt("score", 42))
                .addTag("players", new ListTag<>(TagType.COMPOUND)
                        .addTag(new CompoundTag()
                                .setString("name", "Alex")
                                .setInt("score", 10))
                        .addTag(new CompoundTag()
                                .setString("name", "Steve")
                                .setInt("score", 20)))
                .addTag("profiles", new ListTag<>(TagType.COMPOUND)
                        .addTag(new CompoundTag())
                        .addTag(new CompoundTag().setString("name", "Alex"))
                        .addTag(new CompoundTag()
                                .setString("name", "Alex")
                                .setInt("score", 10)))
                .addTag("empty", new CompoundTag())
                .addTag("metadata", new CompoundTag()
                        .setString("display name", "Alex The Great")
                        .setString("quote\"key", "Escaped"))
                .setString("player.name", "literal")
                .setIntArray("numbers", new int[]{3, 5, 8})
                .setLongArray("longs", new long[]{13L, 21L});
    }

    @Test
    void testLevelDat() throws Exception {
        CompoundTag levelDat = NBTCodec.of().readTag(
                TestResources.getResource("/assets/nbt/level.dat"),
                TagType.COMPOUND
        );

        var path = NBTPath.of("Data.Version.Name").withTagType(TagType.STRING);
        assertEquals("1.21.11", levelDat.getFirstString(path));
    }

    @Test
    void testListSelection() {
        CompoundTag root = createSampleRoot();

        assertEquals("Alex", root.getFirstString(NBTPath.of("players[0].name").withTagType(TagType.STRING)));
        assertEquals("Steve", root.getFirstString(NBTPath.of("players[-1].name").withTagType(TagType.STRING)));
        assertEquals("Steve", root.getFirstString(NBTPath.of(" players [ -1 ] . name ").withTagType(TagType.STRING)));
        assertNull(root.getFirstStringOrNull(NBTPath.of("players[2].name").withTagType(TagType.STRING)));

        assertIterableEquals(List.of("Alex", "Steve"),
                root.getAllTags(NBTPath.of("players[].name").withTagType(TagType.STRING))
                        .map(StringTag::getValue)
                        .toList());
    }

    @Test
    void testArraySelection() {
        CompoundTag root = createSampleRoot();

        assertEquals(5, root.getFirstInt(NBTPath.of("numbers[1]").withTagType(TagType.INT)));
        assertEquals(8, root.getFirstInt(NBTPath.of("numbers[-1]").withTagType(TagType.INT)));
        assertEquals(13L, root.getFirstLong(NBTPath.of("longs[-2]").withTagType(TagType.LONG)));

        assertIterableEquals(List.of(3, 5, 8),
                root.getAllTags(NBTPath.of("numbers[]").withTagType(TagType.INT))
                        .map(IntTag::getValue)
                        .toList());
        assertIterableEquals(List.of(13L, 21L),
                root.getAllTags(NBTPath.of("longs[]").withTagType(TagType.LONG))
                        .map(LongTag::getValue)
                        .toList());
    }

    @Test
    void testQuotedKeysAndEscapes() {
        CompoundTag root = createSampleRoot();

        assertEquals("literal", root.getFirstString(NBTPath.of("\"player.name\"").withTagType(TagType.STRING)));
        assertEquals("Alex The Great", root.getFirstString(NBTPath.of("metadata.\"display name\"").withTagType(TagType.STRING)));
        assertEquals("Escaped", root.getFirstString(NBTPath.of("metadata.\"quote\\\"key\"").withTagType(TagType.STRING)));
    }

    @Test
    void testCompoundMatchSelection() {
        CompoundTag root = createSampleRoot();
        CompoundTag emptyRoot = new CompoundTag();

        assertSame(emptyRoot, emptyRoot.getFirstTag(NBTPath.of("{}").withTagType(TagType.COMPOUND)));

        assertSame(root.get("empty"), root.getFirstTag(NBTPath.of("empty{}").withTagType(TagType.COMPOUND)));
        assertSame(root.get("player"), root.getFirstTag(NBTPath.of("player{}").withTagType(TagType.COMPOUND)));

        assertSame(root.get("player"), root.getFirstTag(NBTPath.of("player{name:\"Alex\",score:42}").withTagType(TagType.COMPOUND)));
        assertSame(root.get("player"), root.getFirstTag(NBTPath.of("player{name:\"Alex\"}").withTagType(TagType.COMPOUND)));
        assertNull(root.getFirstTagOrNull(NBTPath.of("player{name:\"Glavo\"}").withTagType(TagType.COMPOUND)));

        assertEquals(3L, root.getAllTags(NBTPath.of("profiles[{}]").withTagType(TagType.COMPOUND)).count());
        assertEquals("Alex", root.getFirstString(NBTPath.of("profiles[{name:\"Alex\"}].name").withTagType(TagType.STRING)));
        assertEquals(10, root.getFirstInt(NBTPath.of("profiles[{name:\"Alex\",score:10}].score").withTagType(TagType.INT)));
        assertEquals(10, root.getFirstInt(NBTPath.of("profiles[{name:\"Alex\"}].score").withTagType(TagType.INT)));
        assertNull(root.getFirstTagOrNull(NBTPath.of("players[{name:\"Glavo\"}]").withTagType(TagType.COMPOUND)));
    }

    @Test
    void testTraversalBoundariesAndMissingMatches() {
        CompoundTag root = createSampleRoot();

        assertNull(root.getFirstIntOrNull(NBTPath.of("numbers[3]").withTagType(TagType.INT)));
        assertNull(root.getFirstIntOrNull(NBTPath.of("numbers[-4]").withTagType(TagType.INT)));
        assertNull(root.getFirstStringOrNull(NBTPath.of("player[].name").withTagType(TagType.STRING)));
        assertNull(root.getFirstStringOrNull(NBTPath.of("numbers.name").withTagType(TagType.STRING)));
        assertEquals(0L, root.getAllTags(NBTPath.of("players[].score").withTagType(TagType.STRING)).count());
    }

    @Test
    void testWithTagTypeBehavior() {
        CompoundTag root = createSampleRoot();

        NBTPath<?> untypedPath = NBTPath.of("player.name");
        assertNull(untypedPath.getTagType());

        var stringPath = untypedPath.withTagType(TagType.STRING);
        assertEquals(TagType.STRING, stringPath.getTagType());
        assertSame(stringPath, stringPath.withTagType(TagType.STRING));
        assertEquals("Alex", root.getFirstString(stringPath));
        assertNull(root.getFirstTagOrNull(untypedPath.withTagType(TagType.INT)));

        NBTPath<?> fixedCompoundPath = NBTPath.of("profiles[{}]");
        assertEquals(TagType.COMPOUND, fixedCompoundPath.getTagType());
        assertSame(fixedCompoundPath, fixedCompoundPath.withTagType(TagType.COMPOUND));

        assertThrows(IllegalStateException.class, () -> NBTPath.of("{}").withTagType(TagType.STRING));
        assertThrows(IllegalStateException.class, () -> NBTPath.of("profiles[{}]").withTagType(TagType.STRING));
    }

    @Test
    void testPathEqualityAndHashCode() {
        var path1 = NBTPath.of("players[-1].name").withTagType(TagType.STRING);
        var path2 = NBTPath.of("players[-1].name").withTagType(TagType.STRING);
        var path3 = NBTPath.of("players[-1].name");
        var path4 = NBTPath.of("players[0].name").withTagType(TagType.STRING);

        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());
        assertNotEquals(path1, path3);
        assertNotEquals(path1, path4);
    }

    @Test
    void testInvalidPathSyntax() {
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of(""));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("player."));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("players..name"));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("player{}name"));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("numbers[abc]"));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("numbers[1"));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("numbers[1]."));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> NBTPath.of("[2147483648]"));
    }

    @Test
    void testPathString() {
        assertEquals("{}", NBTPath.of("{}").toPathString());
        assertEquals("{Invisible:1B}", NBTPath.of("{Invisible:1b}").toPathString());
        assertEquals("\"A Very Cool Name[]\"", NBTPath.of("\"A Very Cool Name[]\"").toPathString());
        assertEquals("\"A Very Cool Name[]\"{}", NBTPath.of("\"A Very Cool Name[]\"{}").toPathString());
        assertEquals("\"A Very Cool Name[]\"[]", NBTPath.of("\"A Very Cool Name[]\"[]").toPathString());
        assertEquals("\"A Very Cool Name[]\"[{}]", NBTPath.of("\"A Very Cool Name[]\"[{}]").toPathString());
        assertEquals("\"A Very Cool Name[]\"[{Count:25B}]", NBTPath.of("\"A Very Cool Name[]\"[{Count:25b}]").toPathString());
        assertEquals("\"A Very Cool Name[]\"[][][]", NBTPath.of("\"A Very Cool Name[]\"[][][]").toPathString());
        assertEquals("foo.bar", NBTPath.of("foo.bar").toPathString());
        assertEquals("foo.bar[]", NBTPath.of("foo.bar.[]").toPathString());
        assertEquals("foo.bar[{}]", NBTPath.of("foo.bar.[{}]").toPathString());
        assertEquals("foo.bar[0]", NBTPath.of("foo.bar.[0]").toPathString());
        assertEquals("foo.bar[-1]", NBTPath.of("foo.bar.[-1]").toPathString());
        assertEquals("foo.bar.\"0123\"", NBTPath.of("foo.bar.\"0123\"").toPathString());
    }

    @Test
    void testPathStringKeepDots() {
        assertEquals("{}", NBTPath.of("{}").toPathString(false));
        assertEquals("{Invisible:1B}", NBTPath.of("{Invisible:1b}").toPathString(false));
        assertEquals("\"A Very Cool Name[]\"", NBTPath.of("\"A Very Cool Name[]\"").toPathString(false));
        assertEquals("\"A Very Cool Name[]\"{}", NBTPath.of("\"A Very Cool Name[]\"{}").toPathString(false));
        assertEquals("\"A Very Cool Name[]\".[]", NBTPath.of("\"A Very Cool Name[]\"[]").toPathString(false));
        assertEquals("\"A Very Cool Name[]\".[{}]", NBTPath.of("\"A Very Cool Name[]\"[{}]").toPathString(false));
        assertEquals("\"A Very Cool Name[]\".[{Count:25B}]", NBTPath.of("\"A Very Cool Name[]\"[{Count:25b}]").toPathString(false));
        assertEquals("\"A Very Cool Name[]\".[].[].[]", NBTPath.of("\"A Very Cool Name[]\"[][][]").toPathString(false));
        assertEquals("foo.bar", NBTPath.of("foo.bar").toPathString(false));
        assertEquals("foo.bar.[]", NBTPath.of("foo.bar.[]").toPathString(false));
        assertEquals("foo.bar.[{}]", NBTPath.of("foo.bar.[{}]").toPathString(false));
        assertEquals("foo.bar.[0]", NBTPath.of("foo.bar.[0]").toPathString(false));
        assertEquals("foo.bar.[-1]", NBTPath.of("foo.bar.[-1]").toPathString(false));
        assertEquals("foo.bar.\"0123\"", NBTPath.of("foo.bar.\"0123\"").toPathString(false));
    }

    @Test
    void testOfPath() {
        CompoundTag root = new CompoundTag().setName("root");
        IntTag tag;

        root.addTag("foo", new CompoundTag()
                .addTag("bar", new ListTag<IntTag>()
                        .addTag(new IntTag(0))
                        .addTag(new IntTag(1))
                        .addTag(tag = new IntTag(2))
                ));

        NBTPath<IntTag> pathTo2 = NBTPath.of(tag, root);
        assertNotNull(pathTo2);
        assertEquals(2, root.getFirstInt(pathTo2));
        assertEquals(NBTPath.of("foo.bar[2]").withTagType(TagType.INT), pathTo2);
    }

    @Test
    void testOfPath2() {
        CompoundTag root = new CompoundTag().setName("root");
        CompoundTag expectedRoot;
        IntTag tag;

        root.addTag("foo", expectedRoot = new CompoundTag()
                .addTag("bar", new CompoundTag()
                        .addTag("baz", new ListTag<IntTag>()
                                .addTag(new IntTag(0))
                                .addTag(new IntTag(1))
                                .addTag(tag = new IntTag(2))
                        )));

        NBTPath<IntTag> pathTo2 = NBTPath.of(tag, expectedRoot);
        assertNotNull(pathTo2);
        assertEquals(2, expectedRoot.getFirstInt(pathTo2));
        assertEquals(NBTPath.of("bar.baz[2]").withTagType(TagType.INT), pathTo2);
    }

    @Test
    void testOfPath3() {
        CompoundTag root = new CompoundTag().setName("root");
        StringTag tag;

        root.addTag("Very Cool Name", new CompoundTag()
                .addTag("bar", new CompoundTag()
                        .addTag("baz", tag = new StringTag(":D"))));

        NBTPath<StringTag> pathToSmile = NBTPath.of(tag, root);
        NBTPath<StringTag> pathToRoot = NBTPath.of(tag);
        assertNotNull(pathToSmile);
        assertEquals(":D", root.getFirstString(pathToSmile));
        assertEquals(pathToSmile, pathToRoot);
        assertEquals(NBTPath.of("\"Very Cool Name\".bar.baz").withTagType(TagType.STRING), pathToSmile);
    }

    @Test
    void testOfPath4() {
        ChunkRegion chunkRegion = new ChunkRegion();
        Chunk chunk = chunkRegion.getChunk(0, 0);
        CompoundTag rootTag;
        chunk.setRootTag(rootTag = new CompoundTag());
        StringTag testTag;
        rootTag.addTag("test", testTag = new StringTag("TEST"));

        NBTPath<StringTag> pathToTest = NBTPath.of(testTag);
        assertNotNull(pathToTest);
        assertEquals("test", pathToTest.toPathString());
        assertEquals("TEST", chunkRegion.getFirstString(pathToTest));
        assertEquals("TEST", chunk.getFirstString(pathToTest));
    }

    @Test
    void testOfPath5() {
        CompoundTag rootTag;
        Chunk chunk = new Chunk(rootTag = new CompoundTag());
        StringTag testTag;
        rootTag.addTag("test", testTag = new StringTag("TEST"));

        NBTPath<StringTag> path = NBTPath.of(testTag);
        assertNotNull(path);
        assertEquals("TEST", chunk.getFirstString(path));
        assertEquals("test", path.toPathString());
    }
}
