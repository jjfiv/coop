package edu.umass.cs.jfoley.coop.document;

import edu.umass.cs.ciir.waltz.coders.Coder;
import org.lemurproject.galago.utility.Parameters;

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
  public abstract Coder<T> getCoder();
  public abstract Class<T> getInnerClass();

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

  @Override
  public int hashCode() {
    return name.hashCode() ^ getClass().hashCode();
  }

  @SuppressWarnings("unchecked")
  public void extract(Parameters json, HashMap<String, DocVar> vars) {
    Object value = json.get(name);
    if(value == null) {
      // TODO: toleratesNull on schema.
      return;
    }

    // This if statement checks to see if the value is of type T or not:
    if(getInnerClass().isAssignableFrom(value.getClass())) {
      this.encounterValue((T) value);
      vars.put(name, new DocVar<>(this, (T) value));
    } else {
      throw new RuntimeException("Bad class for schema value found in document value="+value+" for schema "+toString());
    }
  }
}
