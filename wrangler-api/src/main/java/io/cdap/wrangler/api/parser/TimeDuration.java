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

package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.cdap.wrangler.api.annotations.PublicEvolving;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a token that holds a time duration value (e.g., "150ms", "2.5s",
 * "1min").
 */
@PublicEvolving
public class TimeDuration implements Token, Serializable {
  private static final long serialVersionUID = -4312897410982345678L;
  // Regex to capture the numeric part and the unit part (case-insensitive)
  // Supports: ns, us, µs, ms, s, sec, min, hr, day(s)
  private static final Pattern DURATION_PATTERN = Pattern.compile(
      "([0-9]+(?:\\.[0-9]+)?)\\s*(ns|us|µs|ms|s|sec|min|hr|d|day|days)", Pattern.CASE_INSENSITIVE);

  private final String text;
  private final TokenType tokenType;
  private final double value;
  private final String unit;
  private final long nanoseconds;

  public TimeDuration(String text, TokenType type) {
    this.text = text;
    this.tokenType = type;
    Matcher matcher = DURATION_PATTERN.matcher(text.trim());
    if (matcher.matches()) {
      this.value = Double.parseDouble(matcher.group(1));
      String unitStr = matcher.group(2).toLowerCase();
      // Normalize synonyms
      if (unitStr.equals("sec")) {
        unitStr = "s";
      }
      if (unitStr.equals("d") || unitStr.equals("days")) {
        unitStr = "day";
      }
      this.unit = unitStr;
      this.nanoseconds = parseNanoseconds(this.value, this.unit);
    } else {
      throw new IllegalArgumentException("Invalid time duration format: " + text);
    }
  }

  /**
   * Parses the numeric value and unit string into a canonical nanosecond value.
   *
   * @param value Numeric value.
   * @param unit  Unit string (lowercase, normalized: ns, us, µs, ms, s, min, hr,
   *              day).
   * @return Value in nanoseconds.
   */
  private long parseNanoseconds(double value, String unit) {
    long multiplierNanos = 0L;
    switch (unit) {
      case "ns":
        multiplierNanos = 1L;
        break;
      case "us":
      case "µs": // Microsecond symbol
        multiplierNanos = TimeUnit.MICROSECONDS.toNanos(1);
        break;
      case "ms":
        multiplierNanos = TimeUnit.MILLISECONDS.toNanos(1);
        break;
      case "s":
        multiplierNanos = TimeUnit.SECONDS.toNanos(1);
        break;
      case "min":
        multiplierNanos = TimeUnit.MINUTES.toNanos(1);
        break;
      case "hr":
        multiplierNanos = TimeUnit.HOURS.toNanos(1);
        break;
      case "day":
        multiplierNanos = TimeUnit.DAYS.toNanos(1);
        break;
      default:
        // Should not happen due to regex, but good practice
        throw new IllegalArgumentException("Invalid time unit: " + unit);
    }

    // Handle potential overflow and precision loss with large doubles
    if (value > Long.MAX_VALUE / (double) multiplierNanos) {
      throw new ArithmeticException("Time duration calculation exceeds Long.MAX_VALUE for input: " + value + unit);
    }

    return (long) (value * multiplierNanos);
  }

  /**
   * @return The original numeric value extracted from the token text.
   */
  public double getNumericValue() {
    return value;
  }

  /**
   * @return The unit part extracted from the token text (lowercase, normalized).
   */
  public String getUnit() {
    return unit;
  }

  /**
   * @return The canonical value of the token in nanoseconds.
   */
  public long getNanoseconds() {
    return nanoseconds;
  }

  /**
   * Convenience method to get the duration in a specific TimeUnit.
   * Note: This involves division and may lose precision if converting to a
   * coarser unit.
   *
   * @param targetUnit The TimeUnit to convert to.
   * @return The duration value in the specified target unit.
   */
  public long getDuration(TimeUnit targetUnit) {
    return targetUnit.convert(this.nanoseconds, TimeUnit.NANOSECONDS);
  }

  // --- Implementation of Token interface ---

  @Override
  public Object value() {
    // Return the canonical value (nanoseconds) as the primary value
    return this.nanoseconds;
  }

  @Override
  public TokenType type() {
    return this.tokenType;
  }

  @Override
  public JsonElement toJson() {
    // Represent as the original string for now.
    return new JsonPrimitive(this.text);
  }
}
