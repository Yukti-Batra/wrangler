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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a token that holds a byte size value (e.g., "10KB", "1.5MB").
 */
@PublicEvolving
public class ByteSize implements Token, Serializable {
  private static final long serialVersionUID = -8159848841403544691L;
  // Regex to capture the numeric part and the unit part (case-insensitive)
  private static final Pattern BYTE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([kKmMgGtTpP])([bB]?)",
      Pattern.CASE_INSENSITIVE);

  private final String text;
  private final TokenType tokenType;
  private final double value;
  private final String unit;
  private final long bytes;

  public ByteSize(String text, TokenType type) {
    this.text = text;
    this.tokenType = type;
    Matcher matcher = BYTE_PATTERN.matcher(text.trim());
    if (matcher.matches()) {
      this.value = Double.parseDouble(matcher.group(1));
      this.unit = matcher.group(2).toUpperCase();
      this.bytes = parseBytes(this.value, this.unit);
    } else {
      throw new IllegalArgumentException("Invalid byte size format: " + text);
    }
  }

  /**
   * Parses the numeric value and unit string into a canonical byte value.
   * Uses 1024 as the base for Kilo, Mega, etc.
   *
   * @param value Numeric value.
   * @param unit  Unit character (K, M, G, T, P).
   * @return Value in bytes.
   */
  private long parseBytes(double value, String unit) {
    long multiplier = 1L;
    switch (unit) {
      case "P":
        multiplier *= 1024L;
        // fall through
      case "T":
        multiplier *= 1024L;
        // fall through
      case "G":
        multiplier *= 1024L;
        // fall through
      case "M":
        multiplier *= 1024L;
        // fall through
      case "K":
        multiplier *= 1024L;
        break;
      default:
        throw new IllegalArgumentException("Invalid byte unit: " + unit);
    }
    // Handle potential overflow and precision loss with large doubles
    if (value > Long.MAX_VALUE / (double) multiplier) {
      // Potentially clamp to MAX_VALUE or throw, depending on desired behavior.
      // Throwing for now as it indicates an unreasonably large input.
      throw new ArithmeticException("Byte size calculation exceeds Long.MAX_VALUE for input: " + value + unit);
    }
    return (long) (value * multiplier);
  }

  /**
   * @return The original numeric value extracted from the token text.
   */
  public double getNumericValue() {
    return value;
  }

  /**
   * @return The unit part extracted from the token text (uppercase, e.g., "K",
   *         "M").
   */
  public String getUnit() {
    return unit;
  }

  /**
   * @return The canonical value of the token in bytes.
   */
  public long getBytes() {
    return bytes;
  }

  // --- Implementation of Token interface ---

  @Override
  public Object value() {
    // Return the canonical value (bytes) as the primary value
    return this.bytes;
  }

  @Override
  public TokenType type() {
    return this.tokenType;
  }

  @Override
  public JsonElement toJson() {
    // Represent as the original string or the byte value?
    // Let's use the original string for now, as it preserves the input format.
    return new JsonPrimitive(this.text);
  }
}
