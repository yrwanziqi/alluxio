/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package tachyon.client;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import tachyon.TestUtils;
import tachyon.master.LocalTachyonCluster;

/**
 * Unit tests for <code>tachyon.client.LocalBlockInStream</code>.
 */
public class LocalBlockInStreamTest {
  private static final int MIN_LEN = 0;
  private static final int MAX_LEN = 255;
  private static final int DELTA = 33;

  private static LocalTachyonCluster CLUSTER = null;
  private static TachyonFS TFS = null;
  private static final ImmutableSet<WriteType> WRITE_TYPES =
      ImmutableSet.of(WriteType.MUST_CACHE, WriteType.CACHE_THROUGH);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static final void before() throws IOException {
    System.setProperty("tachyon.user.quota.unit.bytes", "1000");
    CLUSTER = new LocalTachyonCluster(10000);
    CLUSTER.start();
    TFS = CLUSTER.getClient();
  }

  @AfterClass
  public static final void after() throws Exception {
    CLUSTER.stop();
    System.clearProperty("tachyon.user.quota.unit.bytes");
  }

  /**
   * Test <code>void read()</code>.
   */
  @Test
  public void readTest1() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        byte[] ret = new byte[k];
        int value = is.read();
        int cnt = 0;
        while (value != -1) {
          Assert.assertTrue(value >= 0);
          Assert.assertTrue(value < 256);
          ret[cnt ++] = (byte) value;
          value = is.read();
        }
        Assert.assertEquals(cnt, k);
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());

        is = file.getInStream(ReadType.CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        ret = new byte[k];
        value = is.read();
        cnt = 0;
        while (value != -1) {
          Assert.assertTrue(value >= 0);
          Assert.assertTrue(value < 256);
          ret[cnt ++] = (byte) value;
          value = is.read();
        }
        Assert.assertEquals(cnt, k);
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());
      }
    }
  }

  /**
   * Test <code>void read(byte[] b)</code>.
   */
  @Test
  public void readTest2() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        byte[] ret = new byte[k];
        Assert.assertEquals(k, is.read(ret));
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());

        is = file.getInStream(ReadType.CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        ret = new byte[k];
        Assert.assertEquals(k, is.read(ret));
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());
      }
    }
  }

  /**
   * Test <code>void read(byte[] b, int off, int len)</code>.
   */
  @Test
  public void readTest3() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        byte[] ret = new byte[k / 2];
        Assert.assertEquals(k / 2, is.read(ret, 0, k / 2));
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k / 2, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());

        is = file.getInStream(ReadType.CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }
        ret = new byte[k];
        Assert.assertEquals(k, is.read(ret, 0, k));
        Assert.assertTrue(TestUtils.equalIncreasingByteArray(k, ret));
        is.close();
        Assert.assertTrue(file.isInMemory());
      }
    }
  }

  /**
   * Test <code>void seek(long pos)</code>. Validate the expected exception for seeking a negative
   * position.
   * 
   * @throws IOException
   */
  @Test
  public void seekExceptionTest1() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }

        try {
          is.seek(-1);
        } catch (IOException e) {
          // This is expected
          continue;
        }
        is.close();
        throw new IOException("Except seek IOException");
      }
    }
  }

  /**
   * Test <code>void seek(long pos)</code>. Validate the expected exception for seeking a position
   * that is past buffer limit.
   *
   * @throws IOException
   */
  @Test
  public void seekExceptionTest2() throws IOException {
    thrown.expect(IOException.class);
    thrown.expectMessage("Seek position is past buffer limit");

    final String path = TestUtils.uniqFile();

    for (int k = MIN_LEN; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }

        is.seek(k + 1);
        is.close();
      }
    }
  }

  /**
   * Test <code>void seek(long pos)</code>.
   * 
   * @throws IOException
   */
  @Test
  public void seekTest() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN + DELTA; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.NO_CACHE);
        if (k == 0) {
          Assert.assertTrue(is instanceof EmptyBlockInStream);
        } else {
          Assert.assertTrue(is instanceof LocalBlockInStream);
        }

        is.seek(k / 3);
        Assert.assertEquals(k / 3, is.read());
        is.seek(k / 2);
        Assert.assertEquals(k / 2, is.read());
        is.seek(k / 4);
        Assert.assertEquals(k / 4, is.read());
        is.close();
      }
    }
  }

  /**
   * Test <code>long skip(long len)</code>.
   */
  @Test
  public void skipTest() throws IOException {
    final String path = TestUtils.uniqFile();
    for (int k = MIN_LEN + DELTA; k <= MAX_LEN; k += DELTA) {
      for (WriteType op : WRITE_TYPES) {
        int fileId = TestUtils.createByteFile(TFS, path + "/root/testFile_" + k + "_" + op, op, k);

        TachyonFile file = TFS.getFile(fileId);
        InStream is = file.getInStream(ReadType.CACHE);
        Assert.assertTrue(is instanceof LocalBlockInStream);
        Assert.assertEquals(k / 2, is.skip(k / 2));
        Assert.assertEquals(k / 2, is.read());
        is.close();
        Assert.assertTrue(file.isInMemory());

        is = file.getInStream(ReadType.CACHE);
        Assert.assertTrue(is instanceof LocalBlockInStream);
        int t = k / 3;
        Assert.assertEquals(t, is.skip(t));
        Assert.assertEquals(t, is.read());
        Assert.assertEquals(t, is.skip(t));
        Assert.assertEquals(2 * t + 1, is.read());
        is.close();
        Assert.assertTrue(file.isInMemory());
      }
    }
  }
}
