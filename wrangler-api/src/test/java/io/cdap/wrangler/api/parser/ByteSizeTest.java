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

/**
 * Tests for {@link ByteSize}
 */
public class ByteSizeTest {

  @Test
  public void testValidByteSizes() {
    // Kilobytes
    Assert.assertEquals(10 * 1024L, new ByteSize("10k", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(10 * 1024L, new ByteSize("10K", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(10 * 1024L, new ByteSize("10kb", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(10 * 1024L, new ByteSize("10KB", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(1536L, new ByteSize("1.5k", TokenType.BYTE_SIZE).getBytes()); // 1.5 * 1024
    Assert.assertEquals(1536L, new ByteSize("1.5 K", TokenType.BYTE_SIZE).getBytes()); // With space

    // Megabytes
    Assert.assertEquals(5 * 1024L * 1024L, new ByteSize("5m", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(5 * 1024L * 1024L, new ByteSize("5MB", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(2621440L, new ByteSize("2.5 M", TokenType.BYTE_SIZE).getBytes()); // 2.5 * 1024 * 1024

    // Gigabytes
    Assert.assertEquals(1 * 1024L * 1024L * 1024L, new ByteSize("1g", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(1 * 1024L * 1024L * 1024L, new ByteSize("1GB", TokenType.BYTE_SIZE).getBytes());

    // Terabytes
    Assert.assertEquals(2 * 1024L * 1024L * 1024L * 1024L, new ByteSize("2t", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(2 * 1024L * 1024L * 1024L * 1024L, new ByteSize("2TB", TokenType.BYTE_SIZE).getBytes());

    // Petabytes (Check potential overflow, though unlikely with reasonable inputs)
    Assert.assertEquals(1 * 1024L * 1024L * 1024L * 1024L * 1024L, new ByteSize("1p", TokenType.BYTE_SIZE).getBytes());
    Assert.assertEquals(1 * 1024L * 1024L * 1024L * 1024L * 1024L, new ByteSize("1PB", TokenType.BYTE_SIZE).getBytes());

    // Check numeric value and unit extraction
    ByteSize mbSize = new ByteSize(" 15.7 Mb ", TokenType.BYTE_SIZE);
    Assert.assertEquals(15.7, mbSize.getNumericValue(), 0.001);
    Assert.assertEquals("M", mbSize.getUnit());
    Assert.assertEquals((long) (15.7 * 1024 * 1024), mbSize.getBytes());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatMissingUnit() {
    new ByteSize("100", TokenType.BYTE_SIZE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatWrongUnit() {
    new ByteSize("100xx", TokenType.BYTE_SIZE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatTextUnit() {
    new ByteSize("ten MB", TokenType.BYTE_SIZE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidFormatLeadingUnit() {
    new ByteSize("KB10", TokenType.BYTE_SIZE);
  }

  // Test for potential overflow with large double values (might throw
  // ArithmeticException)
  @Test(expected = ArithmeticException.class)
  public void testOverflow() {
    // Value slightly larger than Long.MAX_VALUE / (1024^5)
    double largeValue = (double) Long.MAX_VALUE / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0) + 1.0;
    new ByteSize(largeValue + "PB", TokenType.BYTE_SIZE);
  }
}
