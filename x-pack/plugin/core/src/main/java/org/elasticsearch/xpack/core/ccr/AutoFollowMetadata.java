/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ccr;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.AbstractNamedDiffable;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.core.XPackPlugin;
import org.elasticsearch.xpack.core.security.xcontent.XContentUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Custom metadata that contains auto follow patterns and what leader indices an auto follow pattern has already followed.
 */
public class AutoFollowMetadata extends AbstractNamedDiffable<MetaData.Custom> implements XPackPlugin.XPackMetaDataCustom {

    public static final String TYPE = "ccr_auto_follow";

    private static final ParseField PATTERNS_FIELD = new ParseField("patterns");
    private static final ParseField FOLLOWED_LEADER_INDICES_FIELD = new ParseField("followed_leader_indices");

    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<AutoFollowMetadata, Void> PARSER = new ConstructingObjectParser<>("auto_follow",
        args -> new AutoFollowMetadata((Map<String, AutoFollowPattern>) args[0], (Map<String, List<String>>) args[1]));

    static {
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> {
            Map<String, AutoFollowPattern> patterns = new HashMap<>();
            String fieldName = null;
            for (XContentParser.Token token = p.nextToken(); token != XContentParser.Token.END_OBJECT; token = p.nextToken()) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    fieldName = p.currentName();
                } else if (token == XContentParser.Token.START_OBJECT) {
                    patterns.put(fieldName, AutoFollowPattern.PARSER.parse(p, c));
                } else {
                    throw new ElasticsearchParseException("unexpected token [" + token + "]");
                }
            }
            return patterns;
        }, PATTERNS_FIELD);
        PARSER.declareObject(ConstructingObjectParser.constructorArg(), (p, c) -> {
            Map<String, List<String>> alreadyFollowedIndexUUIDS = new HashMap<>();
            String fieldName = null;
            for (XContentParser.Token token = p.nextToken(); token != XContentParser.Token.END_OBJECT; token = p.nextToken()) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    fieldName = p.currentName();
                } else if (token == XContentParser.Token.START_ARRAY) {
                    alreadyFollowedIndexUUIDS.put(fieldName, Arrays.asList(XContentUtils.readStringArray(p, false)));
                } else {
                    throw new ElasticsearchParseException("unexpected token [" + token + "]");
                }
            }
            return alreadyFollowedIndexUUIDS;
        }, FOLLOWED_LEADER_INDICES_FIELD);
    }

    public static AutoFollowMetadata fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    private final Map<String, AutoFollowPattern> patterns;
    private final Map<String, List<String>> followedLeaderIndexUUIDs;

    public AutoFollowMetadata(Map<String, AutoFollowPattern> patterns, Map<String, List<String>> followedLeaderIndexUUIDs) {
        this.patterns = patterns;
        this.followedLeaderIndexUUIDs = followedLeaderIndexUUIDs;
    }

    public AutoFollowMetadata(StreamInput in) throws IOException {
        patterns = in.readMap(StreamInput::readString, AutoFollowPattern::new);
        followedLeaderIndexUUIDs = in.readMapOfLists(StreamInput::readString, StreamInput::readString);
    }

    public Map<String, AutoFollowPattern> getPatterns() {
        return patterns;
    }

    public Map<String, List<String>> getFollowedLeaderIndexUUIDs() {
        return followedLeaderIndexUUIDs;
    }

    @Override
    public EnumSet<MetaData.XContentContext> context() {
        // TODO: When a snapshot is restored do we want to restore this?
        // (Otherwise we would start following indices automatically immediately)
        return MetaData.ALL_CONTEXTS;
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_6_5_0.minimumCompatibilityVersion();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(patterns, StreamOutput::writeString, (out1, value) -> value.writeTo(out1));
        out.writeMapOfLists(followedLeaderIndexUUIDs, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(PATTERNS_FIELD.getPreferredName());
        for (Map.Entry<String, AutoFollowPattern> entry : patterns.entrySet()) {
            builder.startObject(entry.getKey());
            builder.value(entry.getValue());
            builder.endObject();
        }
        builder.endObject();

        builder.startObject(FOLLOWED_LEADER_INDICES_FIELD.getPreferredName());
        for (Map.Entry<String, List<String>> entry : followedLeaderIndexUUIDs.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
        return builder;
    }

    @Override
    public boolean isFragment() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoFollowMetadata that = (AutoFollowMetadata) o;
        return Objects.equals(patterns, that.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patterns);
    }

    public static class AutoFollowPattern implements Writeable, ToXContentObject {

        private static final ParseField LEADER_PATTERNS_FIELD = new ParseField("leader_patterns");
        private static final ParseField FOLLOW_PATTERN_FIELD = new ParseField("follow_pattern");
        public static final ParseField MAX_BATCH_OPERATION_COUNT = new ParseField("max_batch_operation_count");
        public static final ParseField MAX_CONCURRENT_READ_BATCHES = new ParseField("max_concurrent_read_batches");
        public static final ParseField MAX_BATCH_SIZE_IN_BYTES = new ParseField("max_batch_size_in_bytes");
        public static final ParseField MAX_CONCURRENT_WRITE_BATCHES = new ParseField("max_concurrent_write_batches");
        public static final ParseField MAX_WRITE_BUFFER_SIZE = new ParseField("max_write_buffer_size");
        public static final ParseField RETRY_TIMEOUT = new ParseField("retry_timeout");
        public static final ParseField IDLE_SHARD_RETRY_DELAY = new ParseField("idle_shard_retry_delay");

        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<AutoFollowPattern, Void> PARSER =
            new ConstructingObjectParser<>("auto_follow_pattern",
                args -> new AutoFollowPattern((List<String>) args[0], (String) args[1], (Integer) args[2], (Integer) args[3],
                    (Long) args[4], (Integer) args[5], (Integer) args[6], (TimeValue) args[7], (TimeValue) args[8]));

        static {
            PARSER.declareStringArray(ConstructingObjectParser.constructorArg(), LEADER_PATTERNS_FIELD);
            PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), FOLLOW_PATTERN_FIELD);
            PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), MAX_BATCH_OPERATION_COUNT);
            PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), MAX_CONCURRENT_READ_BATCHES);
            PARSER.declareLong(ConstructingObjectParser.optionalConstructorArg(), MAX_BATCH_SIZE_IN_BYTES);
            PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), MAX_CONCURRENT_WRITE_BATCHES);
            PARSER.declareInt(ConstructingObjectParser.optionalConstructorArg(), MAX_WRITE_BUFFER_SIZE);
            PARSER.declareField(ConstructingObjectParser.optionalConstructorArg(),
                (p, c) -> TimeValue.parseTimeValue(p.text(), RETRY_TIMEOUT.getPreferredName()),
                RETRY_TIMEOUT, ObjectParser.ValueType.STRING);
            PARSER.declareField(ConstructingObjectParser.optionalConstructorArg(),
                (p, c) -> TimeValue.parseTimeValue(p.text(), IDLE_SHARD_RETRY_DELAY.getPreferredName()),
                IDLE_SHARD_RETRY_DELAY, ObjectParser.ValueType.STRING);
        }

        private final List<String> leaderIndexPatterns;
        private final String followIndexPattern;
        private final Integer maxBatchOperationCount;
        private final Integer maxConcurrentReadBatches;
        private final Long maxOperationSizeInBytes;
        private final Integer maxConcurrentWriteBatches;
        private final Integer maxWriteBufferSize;
        private final TimeValue retryTimeout;
        private final TimeValue idleShardRetryDelay;

        public AutoFollowPattern(List<String> leaderIndexPatterns, String followIndexPattern, Integer maxBatchOperationCount,
                                 Integer maxConcurrentReadBatches, Long maxOperationSizeInBytes, Integer maxConcurrentWriteBatches,
                                 Integer maxWriteBufferSize, TimeValue retryTimeout, TimeValue idleShardRetryDelay) {
            this.leaderIndexPatterns = leaderIndexPatterns;
            this.followIndexPattern = followIndexPattern;
            this.maxBatchOperationCount = maxBatchOperationCount;
            this.maxConcurrentReadBatches = maxConcurrentReadBatches;
            this.maxOperationSizeInBytes = maxOperationSizeInBytes;
            this.maxConcurrentWriteBatches = maxConcurrentWriteBatches;
            this.maxWriteBufferSize = maxWriteBufferSize;
            this.retryTimeout = retryTimeout;
            this.idleShardRetryDelay = idleShardRetryDelay;
        }

        AutoFollowPattern(StreamInput in) throws IOException {
            leaderIndexPatterns = in.readList(StreamInput::readString);
            followIndexPattern = in.readOptionalString();
            maxBatchOperationCount = in.readOptionalVInt();
            maxConcurrentReadBatches = in.readOptionalVInt();
            maxOperationSizeInBytes = in.readOptionalLong();
            maxConcurrentWriteBatches = in.readOptionalVInt();
            maxWriteBufferSize = in.readOptionalVInt();
            retryTimeout = in.readOptionalTimeValue();
            idleShardRetryDelay = in.readOptionalTimeValue();
        }

        public boolean match(String indexName) {
            return match(leaderIndexPatterns, indexName);
        }

        public static boolean match(List<String> leaderIndexPatterns, String indexName) {
            return Regex.simpleMatch(leaderIndexPatterns, indexName);
        }

        public List<String> getLeaderIndexPatterns() {
            return leaderIndexPatterns;
        }

        public String getFollowIndexPattern() {
            return followIndexPattern;
        }

        public Integer getMaxBatchOperationCount() {
            return maxBatchOperationCount;
        }

        public Integer getMaxConcurrentReadBatches() {
            return maxConcurrentReadBatches;
        }

        public Long getMaxOperationSizeInBytes() {
            return maxOperationSizeInBytes;
        }

        public Integer getMaxConcurrentWriteBatches() {
            return maxConcurrentWriteBatches;
        }

        public Integer getMaxWriteBufferSize() {
            return maxWriteBufferSize;
        }

        public TimeValue getRetryTimeout() {
            return retryTimeout;
        }

        public TimeValue getIdleShardRetryDelay() {
            return idleShardRetryDelay;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeStringList(leaderIndexPatterns);
            out.writeOptionalString(followIndexPattern);
            out.writeOptionalVInt(maxBatchOperationCount);
            out.writeOptionalVInt(maxConcurrentReadBatches);
            out.writeOptionalLong(maxOperationSizeInBytes);
            out.writeOptionalVInt(maxConcurrentWriteBatches);
            out.writeOptionalVInt(maxWriteBufferSize);
            out.writeOptionalTimeValue(retryTimeout);
            out.writeOptionalTimeValue(idleShardRetryDelay);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.array(LEADER_PATTERNS_FIELD.getPreferredName(), leaderIndexPatterns.toArray(new String[0]));
            if (followIndexPattern != null) {
                builder.field(FOLLOW_PATTERN_FIELD.getPreferredName(), followIndexPattern);
            }
            if (maxBatchOperationCount != null) {
                builder.field(MAX_BATCH_OPERATION_COUNT.getPreferredName(), maxBatchOperationCount);
            }
            if (maxConcurrentReadBatches != null) {
                builder.field(MAX_CONCURRENT_READ_BATCHES.getPreferredName(), maxConcurrentReadBatches);
            }
            if (maxOperationSizeInBytes != null) {
                builder.field(MAX_BATCH_SIZE_IN_BYTES.getPreferredName(), maxOperationSizeInBytes);
            }
            if (maxConcurrentWriteBatches != null) {
                builder.field(MAX_CONCURRENT_WRITE_BATCHES.getPreferredName(), maxConcurrentWriteBatches);
            }
            if (maxWriteBufferSize != null){
                builder.field(MAX_WRITE_BUFFER_SIZE.getPreferredName(), maxWriteBufferSize);
            }
            if (retryTimeout != null) {
                builder.field(RETRY_TIMEOUT.getPreferredName(), retryTimeout);
            }
            if (idleShardRetryDelay != null) {
                builder.field(IDLE_SHARD_RETRY_DELAY.getPreferredName(), idleShardRetryDelay);
            }
            return builder;
        }

        @Override
        public boolean isFragment() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AutoFollowPattern that = (AutoFollowPattern) o;
            return Objects.equals(leaderIndexPatterns, that.leaderIndexPatterns) &&
                Objects.equals(followIndexPattern, that.followIndexPattern) &&
                Objects.equals(maxBatchOperationCount, that.maxBatchOperationCount) &&
                Objects.equals(maxConcurrentReadBatches, that.maxConcurrentReadBatches) &&
                Objects.equals(maxOperationSizeInBytes, that.maxOperationSizeInBytes) &&
                Objects.equals(maxConcurrentWriteBatches, that.maxConcurrentWriteBatches) &&
                Objects.equals(maxWriteBufferSize, that.maxWriteBufferSize) &&
                Objects.equals(retryTimeout, that.retryTimeout) &&
                Objects.equals(idleShardRetryDelay, that.idleShardRetryDelay);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                leaderIndexPatterns,
                followIndexPattern,
                maxBatchOperationCount,
                maxConcurrentReadBatches,
                maxOperationSizeInBytes,
                maxConcurrentWriteBatches,
                maxWriteBufferSize,
                retryTimeout,
                idleShardRetryDelay
            );
        }
    }

}
