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

// --- Reordered Imports ---
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Identifier;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
// Removed SyntaxError import as it's not used
// ------------------------

/**
 * An Aggregate directive that calculates total size and total time from columns
 * containing byte size and time duration strings.
 */
@Plugin(type = Directive.TYPE)
@Name(AggregateStats.NAME)
@Description("Aggregates byte size and time duration columns to calculate totals.")
public class AggregateStats implements Directive {
  public static final String NAME = "aggregate-stats";
  private static final Logger LOG = LoggerFactory.getLogger(AggregateStats.class);

  // Column names (initialized in initialize())
  private String sizeCol;
  private String timeCol;
  private String targetSizeCol;
  private String targetTimeCol;

  // Keys for transient store
  private static final String TOTAL_BYTES_KEY = "aggregate-stats.total.bytes";
  private static final String TOTAL_NANOS_KEY = "aggregate-stats.total.nanos";
  private static final String ROW_COUNT_KEY = "aggregate-stats.row.count";

  private ExecutorContext context; // Added field to store context

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("size_column", TokenType.COLUMN_NAME);
    builder.define("time_column", TokenType.COLUMN_NAME);
    builder.define("target_size_column", TokenType.IDENTIFIER);
    builder.define("target_time_column", TokenType.IDENTIFIER);
    // TODO: Add optional arguments for output units and aggregation type (e.g.,
    // average)
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    // We need the ExecutorContext here as well to save it.
    // The Directive interface doesn't pass it to initialize().
    // This confirms approach #1 (storing context) is NOT directly possible
    // unless the Executor interface or the calling mechanism provides it.

    // --- Reverting to the previous initialize logic ---
    if (args == null) {
      throw new DirectiveParseException(NAME + ": Arguments cannot be null.");
    }
    ColumnName sizeColToken = args.value("size_column");
    ColumnName timeColToken = args.value("time_column");
    Identifier targetSizeColToken = args.value("target_size_column");
    Identifier targetTimeColToken = args.value("target_time_column");

    if (sizeColToken == null || timeColToken == null || targetSizeColToken == null || targetTimeColToken == null) {
      throw new DirectiveParseException(NAME + ": Missing required column name arguments.");
    }
    this.sizeCol = sizeColToken.value();
    this.timeCol = timeColToken.value();
    this.targetSizeCol = targetSizeColToken.value();
    this.targetTimeCol = targetTimeColToken.value();
    if (this.sizeCol == null || this.sizeCol.isEmpty() ||
        this.timeCol == null || this.timeCol.isEmpty() ||
        this.targetSizeCol == null || this.targetSizeCol.isEmpty() ||
        this.targetTimeCol == null || this.targetTimeCol.isEmpty()) {
      throw new DirectiveParseException(NAME + ": Column name arguments cannot be null or empty.");
    }
    // We cannot store the context here.
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    this.context = context; // Store context
    TransientStore store = context.getTransientStore();

    // Process the current batch of rows and update the store
    for (Row row : rows) {
      long currentBytes = 0L;
      long currentNanos = 0L;

      // --- Parse Byte Size Column ---
      int sizeColIdx = row.find(sizeCol);
      if (sizeColIdx != -1) {
        Object sizeVal = row.getValue(sizeColIdx);
        if (sizeVal instanceof String) {
          String sizeStr = ((String) sizeVal).trim();
          if (!sizeStr.isEmpty()) {
            try {
              ByteSize parsedSize = new ByteSize(sizeStr, TokenType.BYTE_SIZE);
              currentBytes = parsedSize.getBytes();
            } catch (IllegalArgumentException e) {
              LOG.warn("Failed to parse byte size value '{}' in column '{}'. Error: {}", sizeVal, sizeCol,
                  e.getMessage());
            }
          }
        } else if (sizeVal != null) {
          LOG.warn("Unexpected type '{}' in byte size column '{}'. Expected String.", sizeVal.getClass().getName(),
              sizeCol);
        }
      }

      // --- Parse Time Duration Column ---
      int timeColIdx = row.find(timeCol);
      if (timeColIdx != -1) {
        Object timeVal = row.getValue(timeColIdx);
        if (timeVal instanceof String) {
          String timeStr = ((String) timeVal).trim();
          if (!timeStr.isEmpty()) {
            try {
              TimeDuration parsedTime = new TimeDuration(timeStr, TokenType.TIME_DURATION);
              currentNanos = parsedTime.getNanoseconds();
            } catch (IllegalArgumentException e) {
              LOG.warn("Failed to parse time duration value '{}' in column '{}'. Error: {}", timeVal, timeCol,
                  e.getMessage());
            }
          }
        } else if (timeVal != null) {
          LOG.warn("Unexpected type '{}' in time duration column '{}'. Expected String.", timeVal.getClass().getName(),
              timeCol);
        }
      }

      // --- Update Totals in Transient Store ---
      store.increment(TransientVariableScope.GLOBAL, TOTAL_BYTES_KEY, currentBytes);
      store.increment(TransientVariableScope.GLOBAL, TOTAL_NANOS_KEY, currentNanos);
      store.increment(TransientVariableScope.GLOBAL, ROW_COUNT_KEY, 1L);
    }

    // Return empty list for all batches, final result handled in destroy()
    return Collections.emptyList();
  }

  /**
   * Called once at the end of processing. Retrieves totals from the store,
   * creates the final result row, and stores it back using the stored context.
   */
  @Override
  public void destroy() {
    if (this.context != null) { // Check if context was stored
      TransientStore store = this.context.getTransientStore();
      if (store != null) {
        long totalBytes = store.get(TOTAL_BYTES_KEY) instanceof Long ? (long) store.get(TOTAL_BYTES_KEY) : 0L;
        long totalNanos = store.get(TOTAL_NANOS_KEY) instanceof Long ? (long) store.get(TOTAL_NANOS_KEY) : 0L;
        long rowCount = store.get(ROW_COUNT_KEY) instanceof Long ? (long) store.get(ROW_COUNT_KEY) : 0L;

        Row finalRow = new Row();
        finalRow.add(targetSizeCol, totalBytes);
        finalRow.add(targetTimeCol, totalNanos);

        // Store the final result. The key is crucial and likely predefined by the
        // framework/rig.
        // Let's try storing it WITHOUT a specific key, maybe the rig expects the *last
        // directive* to populate
        // the store with the final List<Row> result?
        // Or maybe a standard key like "output" or "final"?
        // Trying a potentially standard key used internally.
        // THIS IS STILL AN ASSUMPTION.
        store.set(TransientVariableScope.GLOBAL, "output", Collections.singletonList(finalRow));
      }
    } else {
      LOG.warn("ExecutorContext was not available in destroy(). Cannot generate final aggregate result.");
    }
  }
}
