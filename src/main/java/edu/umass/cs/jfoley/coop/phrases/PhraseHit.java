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

  @Override
  public String toString() {
    return "PhraseHit("+start+","+size+","+id+")";
  }

  @Override
  public boolean equals(Object other) {
    if(!(other instanceof PhraseHit)) return false;
    PhraseHit rhs = (PhraseHit) other;
    return rhs.id == id && rhs.start == start && rhs.size == size;
  }
}
