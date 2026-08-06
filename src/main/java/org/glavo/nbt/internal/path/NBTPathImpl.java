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
package org.glavo.nbt.internal.path;

import org.glavo.nbt.NBTElement;
import org.glavo.nbt.NBTParent;
import org.glavo.nbt.NBTPath;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.internal.snbt.SNBTWriter;
import org.glavo.nbt.io.SNBTCodec;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.ParentTag;
import org.glavo.nbt.tag.Tag;
import org.glavo.nbt.tag.TagType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public final class NBTPathImpl<T extends Tag> implements NBTPath<T> {

    @SuppressWarnings({"ReassignedVariable", "unchecked"})
    public static <T extends Tag> Stream<T> select(NBTParent<?> parent, NBTPath<? extends T> path) {
        Stream<? extends Tag> tags;
        if (parent instanceof CompoundTag compoundTag) {
            tags = Stream.of(compoundTag);
        } else if (parent instanceof ChunkRegion chunkRegion) {
            tags = chunkRegion.stream()
                    .flatMap(chunk -> Stream.<Tag>ofNullable(chunk.getRootTag()));
        } else if (parent instanceof Chunk chunk) {
            tags = chunk.getRootTag() != null ? Stream.of(chunk.getRootTag()) : Stream.empty();
        } else {
            tags = Stream.empty();
        }

        for (NBTPathNode node : ((NBTPathImpl<T>) path).nodes) {
            tags = node.operate(tags);
        }

        if (path.getTagType() != null) {
            tags = tags.filter(tag -> path.getTagType().tagClass().isInstance(tag));
        }

        return (Stream<T>) tags;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable <T extends Tag> NBTPath<T> of(T tag, @Nullable Tag expectedRoot) throws IllegalArgumentException, IllegalStateException {
        List<NBTPathNode> paths = new ArrayList<>();

        NBTPathNode indicator = NBTPathImpl.getIndicator(tag);
        NBTParent<?> next = tag.getParent();
        while (next != null && indicator != null) {
            paths.add(indicator);
            indicator = NBTPathImpl.getIndicator(next);
            if (next == expectedRoot) break;
            else next = next.getParent();
        }

        if (expectedRoot != null && expectedRoot != next) {
            throw new IllegalStateException("Unexpected root tag " + expectedRoot + ", expected " + next + ".");
        } else if (paths.isEmpty()) {
            return null;
        }
        Collections.reverse(paths);
        return new NBTPathImpl<>(paths.toArray(NBTPathNode[]::new), (TagType<T>) tag.getType());
    }

    /// Get the indicator of the given tag, depends on its parent tag.
    ///
    /// @return the name node if parent is [CompoundTag], the index node if parent is other [ParentTag], or `null` if parent is null.
    public static @Nullable NBTPathNode getIndicator(@Nullable NBTElement tag) {
        if (!(tag instanceof Tag currentTag)) return null;
        NBTParent<?> parentTag = tag.getParent();
        if (parentTag instanceof ParentTag<?>) {
            return parentTag instanceof CompoundTag ? new NBTPathNode.NamedSubTag(currentTag.getName()) : new NBTPathNode.Index(currentTag.getIndex());
        } else { // Chunk, ChunkRegion or null
            return null;
        }
    }

    private final NBTPathNode @Unmodifiable [] nodes;
    private final @Nullable TagType<T> tagType;

    private @Nullable String cachedString;

    public NBTPathImpl(NBTPathNode @Unmodifiable [] nodes, @Nullable TagType<T> tagType) {
        assert nodes.length > 0;
        this.nodes = nodes;
        this.tagType = tagType;
    }

    public NBTPathNode @Unmodifiable [] getNodes() {
        return nodes;
    }

    @Override
    public @Nullable TagType<T> getTagType() {
        return tagType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T2 extends Tag> NBTPath<T2> withTagType(TagType<T2> tagType) {
        if (tagType == this.tagType) {
            return (NBTPath<T2>) this;
        }

        TagType<?> expectedTagType = nodes[nodes.length - 1].getTagType();
        if (expectedTagType == null || expectedTagType.equals(tagType)) {
            return new NBTPathImpl<>(nodes, tagType);
        } else {
            throw new IllegalStateException("Path does not match the given tag type. Expected: " + expectedTagType + ", Actual: " + tagType);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof NBTPathImpl<?> that
                && Arrays.equals(nodes, that.nodes)
                && Objects.equals(tagType, that.tagType);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nodes) ^ Objects.hashCode(tagType);
    }

    @Override
    public String toPathString(boolean omitDots) {
        StringBuilder builder = new StringBuilder();

        SNBTWriter<StringBuilder> writer = new SNBTWriter<>(SNBTCodec.ofCompact(), builder);
        for (int i = 0; i < nodes.length; i++) {
            NBTPathNode node = nodes[i];
            try {
                node.appendTo(writer);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            if (i + 1 < nodes.length && (!omitDots || nodes[i + 1].needDot())) {
                writer.getAppendable().append('.');
            }
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        if (cachedString == null) {
            String pathString = toPathString();
            if (tagType != null) pathString = "<" + tagType + ">" + " " + pathString;
            cachedString = pathString;
        }

        return cachedString;
    }
}


