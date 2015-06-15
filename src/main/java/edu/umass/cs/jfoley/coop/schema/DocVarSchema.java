package edu.umass.cs.jfoley.coop.schema;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.jfoley.coop.document.DocVar;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.HashMap;

/**
 * @author jfoley
 */
public abstract class DocVarSchema<T> {
  protected final String name;
  public DocVarSchema(String name) {
    this.name = name;
  }
  public String getName() { return name; }
  public abstract Class<T> getInnerClass();
  public abstract DocVar<T> createValue(Object obj);

  public abstract Parameters toJSON();

  @Override
  public String toString() {
    return "DocVarSchema("+name+", "+getInnerClass().getSimpleName();
  }

  public abstract void encounterValue(T value);

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof DocVarSchema)) {
      return false;
    }
    DocVarSchema rhs = (DocVarSchema) other;
    return name.equals(rhs.getName()) && rhs.getClass().equals(getClass());
  }

  /**
   * @return a coder that can encode the value type T.
   */
  public abstract Coder<T> getCoder();

  @Override
  public int hashCode() {
    return name.hashCode() ^ getClass().hashCode();
  }

  public void extract(Parameters json, HashMap<String, DocVar> vars) {
    Object value = json.get(name);
    if(value == null) {
      // TODO: toleratesNull on schema.
      return;
    }

    DocVar<T> val = this.createValue(value);
    if(val == null) {
      throw new RuntimeException("Unacceptable schema value found in document value="+value+" for schema "+toString());
    }
    this.encounterValue(val.get());
    vars.put(name, val);
  }

  @Nonnull
  public static DocVarSchema create(String name, Parameters argp) {
    switch(argp.getString("type")) {
      case "categorical":
        return CategoricalVarSchema.create(name, argp);
      case "numeric:":
      case "number":
        return IntegerVarSchema.create(name, argp);
      default:
        throw new UnsupportedOperationException("DocVarSchema.type="+argp.getString("type"));
    }
  }
}
