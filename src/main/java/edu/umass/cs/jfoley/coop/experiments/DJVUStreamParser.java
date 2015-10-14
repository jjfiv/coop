package edu.umass.cs.jfoley.coop.experiments;

import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jfoley
 */
public class DJVUStreamParser extends DocumentStreamParser {
  List<DJVUPageParser.DJVUPage> pages;
  int index;
  public DJVUStreamParser(DocumentSplit split, Parameters p) throws IOException {
    super(split, p);
    index = 0;
    pages = Collections.emptyList();
    try {
      pages = DJVUPageParser.parseDJVU(DocumentStreamParser.getBufferedInputStream(split), DocumentStreamParser.getFileName(split));
    } catch (IOException | OutOfMemoryError e) {
      Logger.getAnonymousLogger().log(Level.WARNING, e.getMessage(), e);
    }
  }

  @Override
  public Document nextDocument() throws IOException {
    if(index < pages.size()) {
      Document doc = new Document();
      DJVUPageParser.DJVUPage page = pages.get(index);
      doc.name = page.archiveId+":"+page.pageNumber;
      StringBuilder sb = new StringBuilder();
      for (DJVUPageParser.DJVUWord word : page.words) {
        for (String term : word.terms) {
          sb.append(' ').append(term);
        }
      }
      doc.text = sb.toString();
      index++;
      return doc;
    }
    return null;
  }

  @Override
  public void close() throws IOException {
    pages = Collections.emptyList();
  }
}
