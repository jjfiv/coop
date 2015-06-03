package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.fn.GenerateFn;
import edu.umass.cs.ciir.waltz.coders.Coder;

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

  public Coder<T> getCoder() {
    return schema.getCoder();
  }

  public DocVarSchema<T> getSchema() {
    return schema;
  }

  @Override
  public T get() {
    return value;
  }
}
