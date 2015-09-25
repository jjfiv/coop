package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.AChaiList;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.xml.ChaiXML;
import ciir.jfoley.chai.xml.XNode;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jfoley
 */
public class DJVUPageParser {
  public static final Logger log = Logger.getLogger(DJVUPageParser.class.getName());

  public static class DJVUWord {
    final List<String> terms;
    short[] coords;
    public DJVUWord(List<String> terms, short[] coords) {
      this.terms = terms;
      assert(coords.length == 4);
      this.coords = coords;
    }

    @Override
    public String toString() {
      return "<w coords="+ Arrays.toString(coords)+">"+StrUtil.join(terms, " ")+"</w>";
    }

    public static DJVUWord fromWORDTag(XNode tag, TagTokenizer tokenizer) {
      try {
        String[] coords = tag.attr("coords").split(",");
        if (coords.length < 4) return null;
        short[] nc = new short[4];
        for (int i = 0; i < nc.length; i++) {
          nc[i] = Short.parseShort(coords[i]);
        }
        List<String> terms = tokenizer.tokenize(tag.getText()).terms;
        if (terms.isEmpty()) return null;
        return new DJVUWord(terms, nc);
      } catch (Exception e) {
        log.log(Level.WARNING, e.getMessage(), e);
        return null;
      }
    }
  }
  public static class DJVUPage extends AChaiList<DJVUWord> {
    final String archiveId;
    final int pageNumber;
    final ArrayList<DJVUWord> words;

    public DJVUPage(String archiveId, int pageNumber) {
      this.archiveId = archiveId;
      this.pageNumber = pageNumber;
      this.words = new ArrayList<>();
    }

    public void pushWord(XNode tag, TagTokenizer tokenizer) {
      DJVUWord maybeWord = DJVUWord.fromWORDTag(tag, tokenizer);
      if(maybeWord != null) {
        words.add(maybeWord);
      }
    }

    public int size() {
      return words.size();
    }

    @Override
    public String toString() {
      return "{DJVUPage book:"+archiveId+" page:"+pageNumber+" words:"+words+"}";
    }

    @Override
    public DJVUWord get(int index) {
      return words.get(index);
    }
  }

  public static List<DJVUPage> parseDJVU(InputStream is, String name) throws IOException {
    TagTokenizer tokenizer = new TagTokenizer();

    assert(name.endsWith(".xml"));
    String archiveId = StrUtil.takeBeforeLast(name, ".xml"); // drop any extensions.
    ArrayList<DJVUPage> output = new ArrayList<>();
    try {
      XNode book = ChaiXML.fromStream(is);
      List<XNode> xmlPages = book.selectByTag("OBJECT");
      for (int i = 0; i < xmlPages.size(); i++) {
        XNode xmlPage = xmlPages.get(i);
        DJVUPage page = new DJVUPage(archiveId, i);
        for (XNode word : xmlPage.selectByTag("WORD")) {
          page.pushWord(word, tokenizer);
        }

        if(page.isEmpty()) continue;
        output.add(page);
      }
      return output;
    } catch (SAXException | ParserConfigurationException e) {
      throw new IOException(e);
    }
  }

  public static void main(String[] args) throws IOException {
    StringPooler.disable();
    List<DJVUPage> pages = parseDJVU(IO.openInputStream("Romeo_and_Juliet_djvu.xml"), "Romeo_and_Juliet_djvu.xml");
    System.out.println("parsed "+pages.size()+" pages.");
  }

}
