package edu.umass.cs.jfoley.coop.experiments;


import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import java.io.IOException;

/**
 * @author jfoley
 */
public class DumpMappingToPlainText {
  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    String input = argp.get("input", "robust.ints/names.rev");

    if(input.endsWith(".fwd") || input.endsWith(".rev")) {
      input = StrUtil.takeBefore(StrUtil.takeBefore(input, ".fwd"), ".rev");
    }

    IdMaps.Reader<String> reader = GalagoIO.openIdMapsReader(input, FixedSize.ints, CharsetCoders.utf8);

    for (Pair<Integer, String> kv : reader.forwardReader.items()) {
      System.out.println(kv.right+"\t"+kv.left);
    }
  }
}
