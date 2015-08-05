package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.index.IndexReader;

/**
 * @author jfoley
 */
public abstract class CoopIndexServerFn implements ServerFn {
  protected final IndexReader index;

  protected CoopIndexServerFn(IndexReader index) {
    this.index = index;
  }
}
