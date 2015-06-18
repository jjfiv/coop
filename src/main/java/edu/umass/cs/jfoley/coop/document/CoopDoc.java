package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import edu.umass.cs.ciir.waltz.postings.extents.InterleavedSpans;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * My pet peeve are java classes named Document.
 * @author jfoley
 */
public class CoopDoc implements Comparable<CoopDoc> {
  public static final int UNKNOWN_DOCID = -1;

  private String name;
  private Map<String,List<String>> terms;
  private List<Set<String>> termLevelIndicators;
  private Map<String, SpanList> tags;
  private int identifier;
  private Map<String, DocVar> variables;
  private String rawText = null;

  public CoopDoc() {
    this.name = null;
    this.terms = new HashMap<>();
    this.tags = new HashMap<>();
    this.identifier = UNKNOWN_DOCID;
    this.variables = new HashMap<>();
    this.termLevelIndicators = new ArrayList<>();
  }

  public CoopDoc(String name, Map<String,List<String>> terms, int identifier, Map<String, DocVar> variables) {
    this(name, terms);
    this.identifier = identifier;
    this.variables = variables;
  }

  public CoopDoc(String name, Map<String,List<String>> terms) {
    this();
    this.name = name;
    this.terms = terms;
  }

  @Override
  public String toString() {
    return toJSON().toString();
  }

  public Parameters toJSON() {
    return Parameters.parseArray(
        "name", name,
        "identifier", identifier,
        "terms", Parameters.wrap(terms),
        "tags", Parameters.wrap(MapFns.mapValues(tags, (spanList) -> {
          List<List<Integer>> reallyNaiveSpanList = new ArrayList<>();
          for (Span span : spanList) {
            reallyNaiveSpanList.add(Arrays.asList(span.begin, span.end));
          }
          return reallyNaiveSpanList;
        })),
        "variables", getJSONVars()
    );
  }

  public List<CoopToken> tokens() {
    int expectedSize = MapFns.firstValue(terms, Collections.emptyList()).size();

    List<CoopToken> output = new ArrayList<>(expectedSize);
    for (Map.Entry<String, List<String>> kv : terms.entrySet()) {
      List<String> entries = kv.getValue();
      for (int i = 0; i < entries.size(); i++) {
        if(output.size() <= i) {
          output.add(new CoopToken(this.identifier, i));
        }
        CoopToken it = output.get(i);
        it.terms.put(kv.getKey(), entries.get(i));
      }
    }
    for (int i = 0; i < output.size(); i++) {
      if(i < termLevelIndicators.size()) {
        output.get(i).indicators = termLevelIndicators.get(i);
      } else {
        output.get(i).indicators = Collections.singleton("NO-FEATURES-ERR");
      }
    }

    return output;
  }

  public String getName() { return name; }
  public Map<String,List<String>> getTerms() { return terms; }
  public int getIdentifier() { return identifier; }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> DocVar<T> getVariable(DocVarSchema<T> schema) {
    DocVar variable = variables.get(schema.getName());
    if(variable == null) return null;
    if(variable.getSchema().equals(schema)) {
      return (DocVar<T>) variable;
    }
    throw new RuntimeException("Couldn't find a variable for schema, but found one with the same name! schema="+schema.getClass().getName()+" name="+schema.getName()+" found="+variable.getSchema()+" value="+variable.get());
  }

  @Nullable
  public <T> T getVariableValue(DocVarSchema<T> schema) {
    DocVar<T> var = getVariable(schema);
    if(var == null) return null;
    return var.get();
  }

  @Nonnull
  public Parameters getJSONVars() {
    Parameters output = Parameters.create();
    for (String s : variables.keySet()) {
      output.put(s, variables.get(s).get());
    }
    return output;
  }

  @Override
  public int compareTo(@Nonnull CoopDoc o) {
    int cmp = Integer.compare(this.identifier, o.identifier);
    if(cmp != 0) return cmp;
    return name.compareTo(o.name);
  }

  public void setIdentifier(int identifier) {
    this.identifier = identifier;
  }

  public void setRawText(String rawText) {
    this.rawText = rawText;
  }

  public String getRawText() {
    return rawText;
  }

  public void addTag(String tagName, int begin, int end) {
    MapFns.extendCollectionInMap(tags,
        tagName, new Span(begin, end),
        (GenerateFn<SpanList>) InterleavedSpans::new);
  }

  @Override
  public boolean equals(Object o) {
    if(this == o) return true;
    if(o instanceof CoopDoc) {
      CoopDoc other = (CoopDoc) o;
      return identifier == other.identifier &&
          name.equals(other.name) &&
          terms.equals(other.terms) &&
          tags.equals(other.tags) &&
          variables.equals(other.variables) &&
          termLevelIndicators.equals(other.termLevelIndicators) &&
          rawText.equals(other.rawText);
    }
    return false;
  }

  public Collection<DocVar> getVariables() {
    return variables.values();
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTerms(String termSet, List<String> terms) {
    this.terms.put(termSet, terms);
  }

  public Map<String, SpanList> getTags() {
    return tags;
  }

  public void setVariables(Map<String,DocVar> variables) {
    this.variables = variables;
  }

  public List<String> getTerms(String pos) {
    return terms.get(pos);
  }

  public void setTermLevelIndicators(List<Set<String>> termLevelIndicators) {
    this.termLevelIndicators = termLevelIndicators;
  }
}
