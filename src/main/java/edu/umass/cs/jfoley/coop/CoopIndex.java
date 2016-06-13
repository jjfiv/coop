package edu.umass.cs.jfoley.coop;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class CoopIndex implements Closeable {

  public final DirectoryReader reader;
  public final IndexSearcher searcher;
  private final FSDirectory dir;
  private final WhitespaceAnalyzer analyzer;

  public CoopIndex(String path) throws IOException {
    this.dir = FSDirectory.open(Paths.get(path));
    this.reader = DirectoryReader.open(dir);
    this.searcher = new IndexSearcher(reader);
    this.analyzer = new WhitespaceAnalyzer();
  }

  @Override
  public void close() throws IOException {
    reader.close();
    dir.close();
  }

  @Nullable
  public Document doc(int num) {
    try {
      return searcher.doc(num);
    } catch (IllegalArgumentException iae) {
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int documentByName(String name) {
    return documentByName(name, "id");
  }
  public int documentByName(String name, String field) {
    BooleanQuery q = new BooleanQuery.Builder().add(new TermQuery(new Term(field, name)), BooleanClause.Occur.MUST).build();
    try {
      ScoreDoc[] docs = searcher.search(q, 100).scoreDocs;
      if(docs.length == 0) {
        return -1;
      }
      return docs[0].doc;
    } catch (IOException e) {
      // TODO log
      return -1;
    }
  }

  public IndexableField getRawField(int doc, String fieldName) {
    try {
      return searcher.doc(doc, Collections.singleton(fieldName)).getField(fieldName);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public String getField(int doc, String fieldName) {
    return getRawField(doc, fieldName).stringValue();
  }

  public List<String> pullTerms(int docId, String field) throws IOException {
    return Arrays.asList(getField(docId, field).split("\\s+"));
  }
  public List<String> pullTerms(int docId) throws IOException {
    return pullTerms(docId, "body");
  }
  public int getTotalDocuments() {
    return this.reader.numDocs();
  }

  public CountStats getTermStatistics(String field, String term) {
    try {
      CollectionStatistics stats = searcher.collectionStatistics(field);
      Term lterm = new Term(field, term);
      TermContext ctx = TermContext.build(searcher.getTopReaderContext(), lterm);
      TermStatistics termStats = searcher.termStatistics(lterm, ctx);

      return new CountStats(stats, termStats);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public CountStats getTermStatistics(String term) {
    return getTermStatistics("body", term);
  }
}
