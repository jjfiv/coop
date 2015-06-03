package edu.umass.cs.jfoley.coop.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * My pet peeve are java classes named Document.
 * @author jfoley
 */
public class CoopDoc implements Comparable<CoopDoc> {
  private String name;
  private List<String> terms;
  private int identifier;
  private Map<String, DocVar> variables;

  public CoopDoc(String name) {
    this.name = name;
    this.terms = new ArrayList<>();
    this.identifier = -1;
    this.variables = new HashMap<>();
  }
  public CoopDoc(String name, List<String> terms, int identifier, Map<String, DocVar> variables) {
    this.name = name;
    this.terms = terms;
    this.identifier = identifier;
    this.variables = variables;
  }

  public String getName() { return name; }
  public List<String> getTerms() { return terms; }
  public int getIdentifier() { return identifier; }

  @SuppressWarnings("unchecked")
  public <T> DocVar<T> getVariable(DocVarSchema<T> schema) {
    DocVar variable = variables.get(schema.getName());
    if(variable == null) return null;
    if(variable.getSchema().equals(schema)) {
      return (DocVar<T>) variable;
    }
    throw new RuntimeException("Couldn't find a variable for schema, but found one with the same name! schema="+schema.getClass().getName()+" name="+schema.getName()+" found="+variable.getSchema()+" value="+variable.get());
  }


  @Override
  public int compareTo(CoopDoc o) {
    int cmp = Integer.compare(this.identifier, o.identifier);
    if(cmp != 0) return cmp;
    return name.compareTo(o.name);
  }
}
