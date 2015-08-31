package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.fn.SinkFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.files.FileChannelSource;
import edu.umass.cs.ciir.waltz.coders.files.FileSink;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.utility.StreamUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author jfoley
 */
public class IntVocabBuilder {

  public static class MemoryVocab {
    public TObjectIntHashMap<String> termMapping;
    SinkFn<String> writeCallback = null;

    public MemoryVocab() {
      termMapping = new TObjectIntHashMap<>();
    }

    public int lookupOrCreate(String term) {
      int code = termMapping.get(term);
      if(code == termMapping.getNoEntryValue()) {
        code = termMapping.size();
        termMapping.put(term, code);
        if(writeCallback != null) {
          writeCallback.process(term);
        }
      }
      return code;
    }

    public int size() {
      return termMapping.size();
    }

    public void setWriteCallback(SinkFn<String> writeCallback) {
      this.writeCallback = writeCallback;
    }
  }


  public static class IntVocabWriter implements Closeable {
    public FileSink corpusWriter;
    public FileSink docOffsetWriter;
    public FileSink docNamesWriter;
    public FileSink termsWriter;
    MemoryVocab vocabulary;

    public static Coder<String> strCoder = CharsetCoders.utf8.lengthSafe();

    public IntVocabWriter(Directory outputDir) throws IOException {
      corpusWriter = new FileSink(outputDir.child("intCorpus"));
      docOffsetWriter = new FileSink(outputDir.child("docOffset"));
      docNamesWriter = new FileSink(outputDir.child("docNames"));
      termsWriter = new FileSink(outputDir.child("terms"));

      termsWriter.write(FixedSize.ints, 0);
      vocabulary = new MemoryVocab();
      vocabulary.setWriteCallback(this::onNewTerm);
    }

    private void onNewTerm(String term) {
      try {
        termsWriter.write(strCoder, term);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public void process(String docName, List<String> terms) throws IOException {
      docNamesWriter.write(strCoder, docName);

      long documentStart = corpusWriter.tell();
      docOffsetWriter.write(FixedSize.longs, documentStart);

      for (String term : terms) {
        int tid = vocabulary.lookupOrCreate(term);
        corpusWriter.write(FixedSize.ints, tid);
      }
    }

    public void put(IntVocabReader other) throws IOException {
      // copy term ids
      other.setTermTranslationTable(this.vocabulary);

      // copy corpus:
      for (long i = 0; i < other.numberOfTermOccurrences(); i++) {
        int originalId = other.corpusReader.readInt(i*4);
        int myTermId = other.termTranslationTable.get(originalId);
        corpusWriter.write(FixedSize.ints, myTermId);
      }

      // copy "doc-offsets"
      for (long i = 0; i < other.numberOfDocuments(); i++) {
        long offset = other.docOffsetReader.readLong(i*8);
        docOffsetWriter.write(FixedSize.longs, offset);
      }

      // copy "doc-names"
      OutputStream namesOut = docNamesWriter.getOutputStream();
      StreamUtil.copyStream(other.docNamesReader, namesOut);

      other.close();
    }

    @Override
    public void close() throws IOException {
      termsWriter.write(0, FixedSize.ints, vocabulary.size());
      corpusWriter.close();
      docOffsetWriter.close();
      docNamesWriter.close();
      termsWriter.close();
    }
  }

  public static class IntVocabReader implements Closeable {
    public FileChannelSource corpusReader;
    public FileChannelSource docOffsetReader;
    public InputStream docNamesReader;
    public InputStream termsReader;

    public int numTerms;
    public TIntIntHashMap termTranslationTable = null;

    public IntVocabReader(Directory inputDir) throws IOException {
      corpusReader = new FileChannelSource(inputDir.childPath("intCorpus"));
      docOffsetReader = new FileChannelSource(inputDir.childPath("docOffset"));
      docNamesReader = IO.openInputStream(inputDir.child("docNames"));
      termsReader = IO.openInputStream(inputDir.child("terms"));
      numTerms = FixedSize.ints.read(termsReader);
    }

    public long numberOfTermOccurrences() throws IOException {
      return corpusReader.size() / 4;
    }
    public long numberOfDocuments() throws IOException {
      return docOffsetReader.size() / 8;
    }

    public void setTermTranslationTable(MemoryVocab targetVocabulary) throws IOException {
      assert(this.termTranslationTable == null);
      TIntIntHashMap result = new TIntIntHashMap(numTerms);
      for (int i = 0; i < numTerms; i++) {
        String term = IntVocabWriter.strCoder.read(termsReader);
        result.put(i, targetVocabulary.lookupOrCreate(term));
      }
      termsReader.close();
      termsReader = null;
      this.termTranslationTable = result;
    }

    @Override
    public void close() throws IOException {
      termTranslationTable = null;
      corpusReader.close();
      docOffsetReader.close();
      docNamesReader.close();
    }
  }



}
