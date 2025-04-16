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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link TimeDuration}
 */
public class TimeDurationTest {

  @Test
  public void testValidTimeDurations() {
    // Nanoseconds
    Assert.assertEquals(150L, new TimeDuration("150ns", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(150L, new TimeDuration("150 ns", TokenType.TIME_DURATION).getNanoseconds()); // With space

    // Microseconds
    Assert.assertEquals(50 * 1000L, new TimeDuration("50us", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(50 * 1000L, new TimeDuration("50µs", TokenType.TIME_DURATION).getNanoseconds()); // Unicode
                                                                                                         // symbol
    Assert.assertEquals(50 * 1000L, new TimeDuration("50 Us", TokenType.TIME_DURATION).getNanoseconds()); // Mixed case

    // Milliseconds
    Assert.assertEquals(250 * 1000_000L, new TimeDuration("250ms", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(123 * 1000_000L, new TimeDuration("123 MS", TokenType.TIME_DURATION).getNanoseconds());

    // Seconds
    Assert.assertEquals(10 * 1000_000_000L, new TimeDuration("10s", TokenType.TIME_DURATION).getNanoseconds());
    long expectedSecNanos = 10 * 1000_000_000L;
    TimeDuration secDuration = new TimeDuration("10sec", TokenType.TIME_DURATION);
    Assert.assertEquals(expectedSecNanos, secDuration.getNanoseconds()); // Synonym
    Assert.assertEquals(10 * 1000_000_000L, new TimeDuration("10 sec", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals((long) (2.5 * 1000_000_000L),
        new TimeDuration("2.5s", TokenType.TIME_DURATION).getNanoseconds());

    // Minutes
    Assert.assertEquals(5 * 60 * 1000_000_000L, new TimeDuration("5min", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(5 * 60 * 1000_000_000L, new TimeDuration("5 min", TokenType.TIME_DURATION).getNanoseconds());

    // Hours
    Assert.assertEquals(2 * 60 * 60 * 1000_000_000L, new TimeDuration("2hr", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(2 * 60 * 60 * 1000_000_000L,
        new TimeDuration("2 hr", TokenType.TIME_DURATION).getNanoseconds());

    // Days
    Assert.assertEquals(3 * 24 * 60 * 60 * 1000_000_000L,
        new TimeDuration("3d", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(3 * 24 * 60 * 60 * 1000_000_000L,
        new TimeDuration("3day", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(3 * 24 * 60 * 60 * 1000_000_000L,
        new TimeDuration("3days", TokenType.TIME_DURATION).getNanoseconds());
    Assert.assertEquals(3 * 24 * 60 * 60 * 1000_000_000L,
        new TimeDuration("3 Days", TokenType.TIME_DURATION).getNanoseconds());

    // Check numeric value and unit extraction (and normalization)
    TimeDuration dur = new TimeDuration(" 45.5 Sec ", TokenType.TIME_DURATION);
    Assert.assertEquals(45.5, dur.getNumericValue(), 0.001);
    Assert.assertEquals("s", dur.getUnit());
    Assert.assertEquals((long) (45.5 * 1_000_000_000L), dur.getNanoseconds());

    // Test convenience method
    Assert.assertEquals(2L, new TimeDuration("2000ms", TokenType.TIME_DURATION).getDuration(TimeUnit.SECONDS));
    Assert.assertEquals(120L, new TimeDuration("2min", TokenType.TIME_DURATION).getDuration(TimeUnit.SECONDS));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatMissingUnit() {
    new TimeDuration("300", TokenType.TIME_DURATION);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatWrongUnit() {
    new TimeDuration("100 parsecs", TokenType.TIME_DURATION);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatTextUnit() {
    new TimeDuration("five ms", TokenType.TIME_DURATION);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatLeadingUnit() {
    new TimeDuration("ms 500", TokenType.TIME_DURATION);
  }

  // Test for potential overflow with large double values (might throw
  // ArithmeticException)
  @Test(expected = ArithmeticException.class)
  public void testOverflow() {
    // Value slightly larger than Long.MAX_VALUE / (nanos per day)
    double largeValue = (double) Long.MAX_VALUE / TimeUnit.DAYS.toNanos(1) + 1.0;
    new TimeDuration(largeValue + "days", TokenType.TIME_DURATION);
  }
}
