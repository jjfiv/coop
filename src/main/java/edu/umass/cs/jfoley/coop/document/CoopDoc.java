package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
import edu.umass.cs.ciir.waltz.postings.extents.InterleavedSpans;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
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
  private List<String> terms;
  private Map<String, SpanList> tags;
  private int identifier;
  private Map<String, DocVar> variables;
  private String rawText = null;

  public CoopDoc() {
    this.name = null;
    this.terms = new ArrayList<>();
    this.tags = new HashMap<>();
    this.identifier = UNKNOWN_DOCID;
    this.variables = new HashMap<>();
  }
  public CoopDoc(String name, List<String> terms, int identifier, Map<String, DocVar> variables) {
    this.name = name;
    this.terms = terms;
    this.tags = new HashMap<>();
    this.identifier = identifier;
    this.variables = variables;
  }

  public CoopDoc(String name, List<String> terms) {
    this.name = name;
    this.terms = terms;
    this.tags = new HashMap<>();
    this.identifier = UNKNOWN_DOCID;
    this.variables = new HashMap<>();
  }

  public String getName() { return name; }
  public List<String> getTerms() { return terms; }
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

  @Nonnull
  public Parameters getJSONVars() {
    Parameters output = Parameters.create();
    for (String s : variables.keySet()) {
      output.put(s, variables.get(s).get());
    }
    return output;
  }

  @Override
  public int compareTo(CoopDoc o) {
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
    if(rawText == null) {
      return StrUtil.join(terms, " ");
    }
    return rawText;
  }

  public void addTag(String tagName, int begin, int end) {
    MapFns.extendCollectionInMap(tags,
        tagName, new Span(begin, end),
        (GenerateFn<SpanList>) InterleavedSpans::new);
  }

  @Nonnull
  public static CoopDoc createMTE(CoopTokenizer tok, Parameters document, Map<String, DocVarSchema> varSchemas) {
    String text = document.getString("text");
    String name = document.getString("docid");
    List<String> terms = tok.tokenize(text);

    HashMap<String,DocVar> vars = new HashMap<>();
    for (DocVarSchema docVarSchema : varSchemas.values()) {
      docVarSchema.extract(document, vars);
    }

    return new CoopDoc(name, terms, UNKNOWN_DOCID, vars);
  }

  public Collection<DocVar> getVariables() {
    return variables.values();
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTerms(List<String> terms) {
    this.terms = terms;
  }

  public Map<String, SpanList> getTags() {
    return tags;
  }
}
