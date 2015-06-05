package edu.umass.cs.jfoley.coop.document;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import edu.umass.cs.ciir.waltz.postings.extents.InterleavedSpans;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.ciir.waltz.postings.extents.SpanList;
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
  @MapSerializer.BindMap(keysCanBeNull = false)
  private Map<String,List<String>> terms;
  @MapSerializer.BindMap(keysCanBeNull = false)
  private Map<String, SpanList> tags;
  private int identifier;
  @MapSerializer.BindMap(keysCanBeNull = false)
  private Map<String, DocVar> variables;
  private String rawText = null;

  public CoopDoc() {
    this.name = null;
    this.terms = new HashMap<>();
    this.tags = new HashMap<>();
    this.identifier = UNKNOWN_DOCID;
    this.variables = new HashMap<>();
  }


  public CoopDoc(String name, Map<String,List<String>> terms, int identifier, Map<String, DocVar> variables) {
    this.name = name;
    this.terms = terms;
    this.tags = new HashMap<>();
    this.identifier = identifier;
    this.variables = variables;
  }

  public CoopDoc(String name, Map<String,List<String>> terms) {
    this.name = name;
    this.terms = terms;
    this.tags = new HashMap<>();
    this.identifier = UNKNOWN_DOCID;
    this.variables = new HashMap<>();
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
        "variables", Parameters.wrap(MapFns.mapValues(variables, (var) -> var.get()))
    );
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

  @Nonnull
  public static CoopDoc createMTE(CoopTokenizer tok, Parameters document, Map<String, DocVarSchema> varSchemas) {
    String text = document.getString("text");
    String name = document.getString("docid");
    CoopDoc doc = tok.createDocument(name, text);

    HashMap<String, DocVar> vars = new HashMap<>();
    for (DocVarSchema docVarSchema : varSchemas.values()) {
      docVarSchema.extract(document, vars);
    }

    doc.setVariables(vars);
    // MTE the raw is the input JSON.
    doc.setRawText(document.toString());

    return doc;
  }

  @Override
  public boolean equals(Object o) {
    if(this == o) return true;
    if(o instanceof CoopDoc) {
      CoopDoc other = (CoopDoc) o;
      if(identifier != other.identifier) return false;
      if(!name.equals(other.name)) return false;
      if(!terms.equals(other.terms)) return false;
      if(!tags.equals(other.tags)) return false;
      if(!variables.equals(other.variables)) return false;
      if(!rawText.equals(other.rawText)) return false;
      return true;
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

  public void setVariables(HashMap<String,DocVar> variables) {
    this.variables = variables;
  }

  public List<String> getTerms(String pos) {
    return terms.get(pos);
  }
}
