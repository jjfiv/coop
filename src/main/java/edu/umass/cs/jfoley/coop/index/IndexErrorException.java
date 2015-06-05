package edu.umass.cs.jfoley.coop.index;

/**
 * @author jfoley
 */
public class IndexErrorException extends RuntimeException {
  public IndexErrorException(Exception e) { super(e); }
  public IndexErrorException(String msg) { super(msg); }
}
