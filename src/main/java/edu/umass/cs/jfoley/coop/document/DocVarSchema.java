package edu.umass.cs.jfoley.coop.document;

import edu.umass.cs.ciir.waltz.coders.Coder;

/**
 * @author jfoley
 */
public abstract class DocVarSchema<T> {
  protected final String name;
  public DocVarSchema(String name) {
    this.name = name;
  }
  public String getName() { return name; }
  public abstract Coder<T> getCoder();

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof DocVarSchema)) {
      return false;
    }
    DocVarSchema rhs = (DocVarSchema) other;
    return name.equals(rhs.getName()) && rhs.getClass().equals(getClass());
  }

  @Override
  public int hashCode() {
    return name.hashCode() ^ getClass().hashCode();
  }
}
