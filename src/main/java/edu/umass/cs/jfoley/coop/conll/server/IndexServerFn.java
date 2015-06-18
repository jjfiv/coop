package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;

/**
 * @author jfoley
 */
public abstract class IndexServerFn implements ServerFn {
  protected final TermBasedIndexReader index;

  public IndexServerFn(TermBasedIndexReader index) {
    this.index = index;
  }
}
