package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;

import java.util.Collections;
import java.util.List;

/**
 * One of the advantages of this GalagoTokenizer is that it handles XML tags if you like.
 */
public class GalagoTokenizer implements CoopTokenizer {
  private final TagTokenizer tok;

  public GalagoTokenizer() {
    this(Collections.emptyList());
  }
  public GalagoTokenizer(List<String> tagsToKeep) {
    tok = new TagTokenizer();
    tagsToKeep.forEach(tok::addField);
  }

  @Override
  public List<String> tokenize(String input) {
    return tok.tokenize(StrUtil.replaceUnicodeQuotes(input)).terms;
  }

  @Override
  public CoopDoc createDocument(String name, String text) {
    Document doc = tok.tokenize(StrUtil.replaceUnicodeQuotes(text));
    CoopDoc cdoc = new CoopDoc();
    cdoc.setName(name);
    cdoc.setTerms(doc.terms);
    cdoc.setRawText(doc.text);
    for (Tag tag : doc.tags) {
      cdoc.addTag(tag.name, tag.begin, tag.end);
    }
    return cdoc;
  }
}
