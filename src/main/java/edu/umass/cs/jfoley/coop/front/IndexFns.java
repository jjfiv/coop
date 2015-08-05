package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.index.IndexReader;

import java.util.Map;

/**
 * @author jfoley
 */
public class IndexFns {
  public static void setup(IndexReader coopIndex, Map<String, ServerFn> methods) {

    methods.put("indexMeta", (p) -> coopIndex.getMetadata());
    methods.put("findKWIC", new FindKWIC(coopIndex));
    methods.put("rankTermsPMI", new RankTermsPMI(coopIndex));
  }

}
