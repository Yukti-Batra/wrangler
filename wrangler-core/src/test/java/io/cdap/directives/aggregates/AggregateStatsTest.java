/*
 * Copyright © 2023 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.directives.aggregates;

import io.cdap.wrangler.TestingPipelineContext;
import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link AggregateStats}
 */
public class AggregateStatsTest {

  // Define keys here for test usage
  private static final String TOTAL_BYTES_KEY = "aggregate-stats.total.bytes";
  private static final String TOTAL_NANOS_KEY = "aggregate-stats.total.nanos";
  private static final String ROW_COUNT_KEY = "aggregate-stats.row.count";

  @Test
  public void testBasicAggregation() throws Exception {
    String[] recipe = new String[] {
        "aggregate-stats :size_col :time_col total_bytes total_nanos"
    };

    List<Row> rows = Arrays.asList(
        new Row("size_col", "1KB").add("time_col", "100ms"),
        new Row("size_col", "2.5KB").add("time_col", "250ms"),
        new Row("size_col", "512B").add("time_col", "50ms") // Note: 512B is not a standard unit we parse, should be 0
    );

    // Expected values:
    // Size: 1KB + 2.5KB + 0B = 1024 + 2560 + 0 = 3584 bytes
    // Time: 100ms + 250ms + 50ms = 400ms = 400,000,000 nanos
    long expectedBytes = 1024L + (long) (2.5 * 1024L);
    long expectedNanos = (100L + 250L + 50L) * 1_000_000L;

    // Create context and execute
    ExecutorContext context = new TestingPipelineContext();
    List<Row> results = TestingRig.execute(recipe, rows, context);

    // Assert execute returns empty list
    Assert.assertTrue("Execute should return an empty list for aggregate directive", results.isEmpty());

    // Get store and assert values directly
    TransientStore store = context.getTransientStore();
    Assert.assertEquals(expectedBytes, (long) store.get(TOTAL_BYTES_KEY));
    Assert.assertEquals(expectedNanos, (long) store.get(TOTAL_NANOS_KEY));
    Assert.assertEquals(3L, (long) store.get(ROW_COUNT_KEY)); // Check row count
  }

  @Test
  public void testAggregationWithDifferentUnits() throws Exception {
    String[] recipe = new String[] {
        "aggregate-stats :data_size :duration total_size_agg total_time_agg"
    };

    List<Row> rows = Arrays.asList(
        new Row("data_size", "1MB").add("duration", "1s"),
        new Row("data_size", "512KB").add("duration", "500ms"),
        new Row("data_size", "1.5GB").add("duration", "0.1min") // 0.1 min = 6 seconds
    );

    // Expected values:
    // Size: 1MB + 512KB + 1.5GB = (1024*1024) + (512*1024) + (1.5*1024*1024*1024)
    long expectedBytes = (1L * 1024 * 1024) + (512L * 1024) + (long) (1.5 * 1024 * 1024 * 1024);
    // Time: 1s + 500ms + 6s = 1,000,000,000 + 500,000,000 + 6,000,000,000 =
    // 7,500,000,000 nanos
    long expectedNanos = TimeUnit.SECONDS.toNanos(1) + TimeUnit.MILLISECONDS.toNanos(500) + TimeUnit.SECONDS.toNanos(6);

    ExecutorContext context = new TestingPipelineContext();
    List<Row> results = TestingRig.execute(recipe, rows, context);
    Assert.assertTrue(results.isEmpty());

    TransientStore store = context.getTransientStore();
    Assert.assertEquals(expectedBytes, (long) store.get(TOTAL_BYTES_KEY));
    Assert.assertEquals(expectedNanos, (long) store.get(TOTAL_NANOS_KEY));
  }

  @Test
  public void testAggregationWithInvalidData() throws Exception {
    String[] recipe = new String[] {
        "aggregate-stats :size :time total_size total_time"
    };

    List<Row> rows = Arrays.asList(
        new Row("size", "1KB").add("time", "100ms"),
        new Row("size", "INVALID").add("time", "50ms"), // Invalid size
        new Row("size", "2KB").add("time", "not_a_time"), // Invalid time
        new Row("size", "").add("time", "") // Empty strings
    );

    // Expected values (invalid/empty values should be ignored/treated as 0):
    // Size: 1KB + 0 + 2KB + 0 = 1024 + 2048 = 3072 bytes
    // Time: 100ms + 50ms + 0 + 0 = 150ms = 150,000,000 nanos
    long expectedBytes = 1024L + 2048L;
    long expectedNanos = (100L + 50L) * 1_000_000L;

    ExecutorContext context = new TestingPipelineContext();
    List<Row> results = TestingRig.execute(recipe, rows, context);
    Assert.assertTrue(results.isEmpty());

    TransientStore store = context.getTransientStore();
    Assert.assertEquals(expectedBytes, (long) store.get(TOTAL_BYTES_KEY));
    Assert.assertEquals(expectedNanos, (long) store.get(TOTAL_NANOS_KEY));
    Assert.assertEquals(4L, (long) store.get(ROW_COUNT_KEY));
  }

  @Test
  public void testAggregationWithMissingColumns() throws Exception {
    String[] recipe = new String[] {
        "aggregate-stats :size :time total_s total_t"
    };

    List<Row> rows = Arrays.asList(
        new Row("size", "1KB").add("time", "100ms"),
        new Row("size", "2KB"), // Missing time column
        new Row("time", "50ms") // Missing size column
    );

    // Expected values (missing values should be treated as 0):
    // Size: 1KB + 2KB + 0 = 3072 bytes
    // Time: 100ms + 0 + 50ms = 150,000,000 nanos
    long expectedBytes = 1024L + 2048L;
    long expectedNanos = (100L + 50L) * 1_000_000L;

    ExecutorContext context = new TestingPipelineContext();
    List<Row> results = TestingRig.execute(recipe, rows, context);
    Assert.assertTrue(results.isEmpty());

    TransientStore store = context.getTransientStore();
    Assert.assertEquals(expectedBytes, (long) store.get(TOTAL_BYTES_KEY));
    Assert.assertEquals(expectedNanos, (long) store.get(TOTAL_NANOS_KEY));
    Assert.assertEquals(3L, (long) store.get(ROW_COUNT_KEY));
  }

  @Test
  public void testAggregationWithZeroRows() throws Exception {
    String[] recipe = new String[] {
        "aggregate-stats :size :time total_s total_t"
    };

    List<Row> rows = Arrays.asList(); // Empty input list

    // Expected values:
    long expectedBytes = 0L;
    long expectedNanos = 0L;

    ExecutorContext context = new TestingPipelineContext();
    List<Row> results = TestingRig.execute(recipe, rows, context);
    // Execute might still return empty list even with 0 rows input
    Assert.assertTrue(results.isEmpty());

    // Check the store state - should be initial state (0 or null depending on
    // implementation)
    TransientStore store = context.getTransientStore();
    Object bytesVal = store.get(TOTAL_BYTES_KEY);
    Object nanosVal = store.get(TOTAL_NANOS_KEY);
    Object countVal = store.get(ROW_COUNT_KEY);

    Assert.assertEquals(expectedBytes, bytesVal == null ? 0L : (long) bytesVal);
    Assert.assertEquals(expectedNanos, nanosVal == null ? 0L : (long) nanosVal);
    Assert.assertEquals(0L, countVal == null ? 0L : (long) countVal); // Expect 0 count
  }

  // TODO: Add tests for optional arguments if implemented (e.g., output units,
  // average)

}
