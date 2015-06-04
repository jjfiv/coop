package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.io.IO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jfoley
 */
public class Gettysburg {
  public static List<String> getParagraphs() throws IOException {
    return new ArrayList<>(Arrays.asList(IO.resource("/gettysburg_address.txt").split("\n+")));
  }
}
