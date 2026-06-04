/*
 * Copyright 2026 Taskeren
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
package org.glavo.nbt.io;

import org.glavo.nbt.TestResources;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.IntTag;
import org.glavo.nbt.tag.ListTag;
import org.glavo.nbt.tag.TagType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public final class SNBTCodecTest {

    @Test
    void testNewLineSeparatedSNBT() throws IOException {
        Path resource = TestResources.getResource("/assets/nbt/newline_separated.snbt");
        // expected failure
        // the vanilla-flavored SNBT should not split compounds and lists with '\n'.
        Assertions.assertThrows(
            IOException.class, () -> {
                try (var reader = Files.newBufferedReader(resource, StandardCharsets.UTF_8)) {
                    SNBTCodec.of().readTag(reader);
                }
            });

        try (var reader = Files.newBufferedReader(resource, StandardCharsets.UTF_8)) {
            var tag = SNBTCodec.of().withAllowNewLineAsSeparator(true).readTag(reader);
            var compound = assertInstanceOf(CompoundTag.class, tag);
            assertEquals("Foo", compound.getString("name"));
            // check list
            var list = assertInstanceOf(ListTag.class, compound.get("list"));
            assert list != null : "ensured by assertInstanceOf";
            assertEquals(TagType.INT, list.getElementType());
            assertEquals(new ListTag<>(TagType.INT).setName("list")
                    .addTag(new IntTag(1))
                    .addTag(new IntTag(2))
                    .addTag(new IntTag(3))
                    .addTag(new IntTag(4)), list);
            // check compound
            var strDict = assertInstanceOf(CompoundTag.class, compound.get("str_dict"));
            assertEquals(new CompoundTag()
                    .setName("str_dict")
                    .setString("a", "foo")
                    .setString("b", "bar")
                    .setString("c", "baz")
                    .setString("d", "Hello\nWorld")
                    .setIntArray("e", new int[] {-886784530, 1488270925, -1459824571, -372109282})
                    .setBoolean("f", true), strDict);
        }
    }

}
