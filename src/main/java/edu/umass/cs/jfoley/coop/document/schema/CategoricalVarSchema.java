package edu.umass.cs.jfoley.coop.document.schema;

import ciir.jfoley.chai.collections.ListBasedOrderedSet;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import org.lemurproject.galago.utility.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class CategoricalVarSchema extends DocVarSchema<String> {
  private final ListBasedOrderedSet<String> values;
  private final boolean definedInAdvance;

  /** Reflection-only constructor */
  private CategoricalVarSchema() {
    super(null);
    this.values = null;
    this.definedInAdvance = false;
  }

  public CategoricalVarSchema(String name, List<String> values, boolean definedInAdvance) {
    super(name);
    this.values = new ListBasedOrderedSet<>(values);
    this.definedInAdvance = definedInAdvance;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Class<String> getInnerClass() {
    return String.class;
  }

  @Override
  public DocVar<String> createValue(Object obj) {
    if(obj instanceof String) {
      return new DocVar<>(this, (String) obj);
    }
    return null;
  }

  @Override
  public Parameters toJSON() {
    return Parameters.parseArray(
        "type", "categorical",
        "values", values.toList()
    );
  }

  @Override
  public void encounterValue(String value) {
    if(definedInAdvance) {
      if(!values.contains(value)) {
        throw new RuntimeException("Schema error, found categorical value for "+name+" in corpus of: ``"+value+"'' that was not defined in the schema yet!");
      }
    } else {
      values.add(value);
    }
  }

  @Override
  public String toString() {
    return "CategoricalVarSchema."+name+"("+ StrUtil.join(values.toList(), ",")+")";
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if(this == o) return true;
    if (!(o instanceof CategoricalVarSchema)) {
      return false;
    }
    CategoricalVarSchema other = (CategoricalVarSchema) o;
    return name.equals(other.name) && values.equals(other.values);
  }

  public static DocVarSchema create(String name, Parameters args) {
    if (args.isList("values")) {
      return new CategoricalVarSchema(name, args.getAsList("values", String.class), true);
    }
    return new CategoricalVarSchema(name, new ArrayList<>(), false);
  }
}
