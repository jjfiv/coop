package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.fn.GenerateFn;

/**
 * @author jfoley
 */
public class DocVar<T> implements GenerateFn<T> {
  private final DocVarSchema<T> schema;
  private final T value;

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
  public T get() {
    return value;
  }
}
