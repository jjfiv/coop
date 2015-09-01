package edu.umass.cs.jfoley.coop.front;

import edu.umass.cs.jfoley.coop.conll.server.ServerFn;

/**
 * @author jfoley
 */
public abstract class CoopIndexServerFn implements ServerFn {
  protected final CoopIndex index;

  protected CoopIndexServerFn(CoopIndex index) {
    this.index = index;
  }
}
