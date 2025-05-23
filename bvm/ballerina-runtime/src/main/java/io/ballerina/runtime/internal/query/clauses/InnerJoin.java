/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.runtime.internal.query.clauses;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BFunctionPointer;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.internal.query.pipeline.StreamPipeline;
import io.ballerina.runtime.internal.query.utils.QueryException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.ballerina.runtime.api.constants.RuntimeConstants.BALLERINA_QUERY_PKG_ID;

/**
 * Represents an Inner Join Clause in the query pipeline.
 *
 * @since 2201.13.0
 */
public class InnerJoin implements QueryClause {
    private final StreamPipeline pipelineToJoin;
    private final BFunctionPointer lhsKeyFunction;
    private final BFunctionPointer rhsKeyFunction;
    private final Map<String, List<BMap<BString, Object>>> rhsFramesMap = new HashMap<>();
    private BError failureAtJoin = null;
    private final Environment env;

    /**
     * Constructor for the InnerJoin.
     *
     * @param env The runtime environment.
     * @param pipelineToJoin The pipeline representing the right-hand side of the join.
     * @param lhsKeyFunction The function to extract the join key from the left-hand side.
     * @param rhsKeyFunction The function to extract the join key from the right-hand side.
     */
    private InnerJoin(Environment env, StreamPipeline pipelineToJoin,
                      BFunctionPointer lhsKeyFunction, BFunctionPointer rhsKeyFunction) {
        this.pipelineToJoin = pipelineToJoin;
        this.lhsKeyFunction = lhsKeyFunction;
        this.rhsKeyFunction = rhsKeyFunction;
        this.env = env;
        initializeRhsFrames();
    }

    /**
     * Initializes the inner join clause.
     *
     * @param env The runtime environment.
     * @param pipelineToJoin The pipeline representing the right-hand side of the join.
     * @param lhsKeyFunction The function to extract the join key from the left-hand side.
     * @param rhsKeyFunction The function to extract the join key from the right-hand side.
     * @return The initialized InnerJoin.
     */
    public static InnerJoin initInnerJoinClause(Environment env,
                                                StreamPipeline pipelineToJoin,
                                                BFunctionPointer lhsKeyFunction,
                                                BFunctionPointer rhsKeyFunction) {
        return new InnerJoin(env, pipelineToJoin, lhsKeyFunction, rhsKeyFunction);
    }

    /**
     * Initializes the right-hand side (RHS) frames by processing the pipeline.
     */
    private void initializeRhsFrames() {
        try {
            Stream<BMap<BString, Object>> strm = ((StreamPipeline) StreamPipeline
                            .getStreamFromPipeline(pipelineToJoin)).getStream();
            strm.forEach(frame -> {
                Object key = rhsKeyFunction.call(env.getRuntime(), frame);
                if (key instanceof BError error) {
                    failureAtJoin = error;
                    return;
                }
                rhsFramesMap.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(frame);
            });
        } catch (QueryException e) {
            failureAtJoin = e.getError();
        }
    }

    /**
     * Executes the inner join by processing the left-hand side (LHS) frames.
     *
     * @param inputStream The input stream of frames (LHS).
     * @return A joined stream of frames.
     */
    @Override
    public Stream<BMap<BString, Object>> process(Stream<BMap<BString, Object>> inputStream) {
        return inputStream.flatMap(lhsFrame -> {
            try {
                if (failureAtJoin != null) {
                    throw new QueryException(failureAtJoin);
                }
                Object lhsKey = lhsKeyFunction.call(env.getRuntime(), lhsFrame);
                if (lhsKey instanceof BError error) {
                    throw new QueryException(error);
                }
                List<BMap<BString, Object>> rhsCandidates = rhsFramesMap
                        .getOrDefault(lhsKey.toString(), Collections.emptyList());
                return rhsCandidates.stream()
                        .map(rhsFrame -> mergeFrames(lhsFrame, rhsFrame));

            } catch (BError e) {
                throw new QueryException(e);
            }
        });
    }

    /**
     * Merges two frames into a single frame.
     *
     * @param lhs The left-hand frame.
     * @param rhs The right-hand frame.
     * @return A merged frame.
     */
    private BMap<BString, Object> mergeFrames(BMap<BString, Object> lhs, BMap<BString, Object> rhs) {
        BMap<BString, Object> result = ValueCreator.createRecordValue(BALLERINA_QUERY_PKG_ID, "_Frame");
        lhs.entrySet().forEach(entry ->
                result.put(entry.getKey(), entry.getValue())
        );
        rhs.entrySet().forEach(entry ->
                result.put(entry.getKey(), entry.getValue())
        );
        return result;
    }
}
