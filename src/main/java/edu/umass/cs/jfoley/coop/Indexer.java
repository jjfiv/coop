package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.time.Debouncer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jfoley
 */
public class Indexer implements Closeable {
  private final FSDirectory directory;
  private final IndexWriterConfig cfg;
  private final IndexWriter writer;
  private final AtomicLong processed;
  private final Debouncer msg;

  public Indexer(String path, boolean append) throws IOException {
    this.directory = FSDirectory.open(Paths.get(path));
    cfg = new IndexWriterConfig(new WhitespaceAnalyzer());
    cfg.setOpenMode(append ? IndexWriterConfig.OpenMode.CREATE_OR_APPEND : IndexWriterConfig.OpenMode.CREATE);
    writer = new IndexWriter(directory, cfg);
    msg = new Debouncer();
    processed = new AtomicLong(0);
  }

  public Debouncer getMessage() {
    return msg;
  }

  public long numProcessed() {
    return processed.get();
  }

  public void pushDocument(IndexableField... fields) {
    pushDocument(Arrays.asList(fields));
  }

  public void pushDocument(Iterable<IndexableField> doc) {
    try {
      processed.incrementAndGet();
      writer.addDocument(doc);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
    long count = processed.get();
    System.err.println("# Indexing Complete to : " + directory.getDirectory() + ": " + msg.estimate(count, count));
    directory.close();
  }
}
