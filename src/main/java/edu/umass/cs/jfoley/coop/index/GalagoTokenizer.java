package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.krovetz.KStem;

import java.util.*;

/**
 * One of the advantages of this GalagoTokenizer is that it handles XML tags if you like.
 */
public class GalagoTokenizer implements CoopTokenizer {
  private final TagTokenizer tok;
  private final KStem krovetzStemmer;

  public GalagoTokenizer() {
    this(Collections.emptyList());
  }
  public GalagoTokenizer(List<String> tagsToKeep) {
    tok = new TagTokenizer();
    krovetzStemmer = new KStem();
    tagsToKeep.forEach(tok::addField);
  }

  @Override
  public CoopDoc createDocument(String name, String text) {
    Document doc = tok.tokenize(StrUtil.replaceUnicodeQuotes(text));
    CoopDoc cdoc = new CoopDoc();
    cdoc.setName(name);
    cdoc.setTerms("terms", doc.terms);
    List<String> stemmed = new ArrayList<>();
    for (String term : doc.terms) {
      stemmed.add(krovetzStemmer.stemTerm(term));
    }
    cdoc.setTerms("krovetz", stemmed);
    cdoc.setRawText(doc.text);
    for (Tag tag : doc.tags) {
      cdoc.addTag(tag.name, tag.begin, tag.end);
    }
    return cdoc;
  }

  @Override
  public Set<String> getTermSets() {
    return new HashSet<>(Arrays.asList("terms", "krovetz"));
  }

  @Override
  public String getDefaultTermSet() {
    return "terms";
  }
}
