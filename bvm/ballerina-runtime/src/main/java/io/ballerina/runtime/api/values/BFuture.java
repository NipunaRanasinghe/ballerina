/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.runtime.api.values;

/**
  * <p>
  * Represent a Ballerina future in Java.
  * </p>
  *
  * @since 1.1.0
  */
  public interface BFuture extends BValue {

    /**
     * Abort execution of the Ballerina strand that the future is attached.
     * The abortion occurs only after the next yield point.
     */
    void cancel();

     /**
      * Returns the result value of the future.
      *
      * @return result value
      */
     Object get();

    /**
     * Returns completion status of the Ballerina strand that the future is attached.
     *
     * @return true if future is completed
     */
    boolean isDone();

     /**
      * Returns whether the future is completed with panic.
      *
      * @return true if future is completed with panic otherwise false
      */
     boolean isPanic();
 }
