package edu.umass.cs.jfoley.coop.phrases;

/**
 * @author jfoley
 */
public class PhraseHit {
  final int start;
  final int size;
  final int id;

  public PhraseHit(int start, int size, int id) {
    this.start = start;
    this.size = size;
    this.id = id;
  }
}
