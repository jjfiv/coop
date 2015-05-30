package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.TemporaryDirectory;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jfoley.
 */
public class CoIndexTest {


  @Test
  public void testCoIndex() throws IOException {
    List<String> docs = new ArrayList<>(Arrays.asList(IO.resource("/gettysburg_address.txt").split("\n+")));

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (CoIndex.Builder builder = new CoIndex.Builder(tmpdir)) {
        for (int i = 0; i < docs.size(); i++) {
          String doc = docs.get(i);
          builder.addDocument(String.format("ga.p%d", i), doc);
        }
      }


    }

  }



}