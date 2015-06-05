package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.fn.GenerateFn;

/**
 * @author jfoley
 */
public class DocVar<T extends Comparable<T>> implements GenerateFn<T> {
  private final DocVarSchema<T> schema;
  private final T value;

  /** No-args constructor for reflection. */
  private DocVar() {
    this.schema = null;
    this.value = null;
  }
  public DocVar(DocVarSchema<T> schema, T value) {
    this.schema = schema;
    this.value = value;
  }

  public String getName() {
    return schema.getName();
  }

  public DocVarSchema<T> getSchema() {
    return schema;
  }

  @Override
  public boolean equals(Object o) {
    if(this == o) return true;
    if(o instanceof DocVar) {
      DocVar other = (DocVar) o;
      return this.schema.equals(other.schema) && this.value.equals(other.value);
    }
    return false;
  }

  @Override
  public T get() {
    return value;
  }
}
