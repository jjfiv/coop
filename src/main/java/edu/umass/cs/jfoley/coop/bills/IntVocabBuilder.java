package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
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

import java.io.*;
import java.nio.ByteBuffer;
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
      if(termMapping.containsKey(term)) {
        return termMapping.get(term);
      } else {
        int code = termMapping.size();
        termMapping.put(term, code);
        if(writeCallback != null) {
          writeCallback.process(term);
        }
        return code;
      }
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

      // start of this segment:
      long stepOver = corpusWriter.tell();

      // copy corpus:
      for (long i = 0; i < other.numberOfTermOccurrences(); i++) {
        int originalId = other.corpusReader.readInt(i*4);
        int myTermId = other.termTranslationTable.get(originalId);
        corpusWriter.write(FixedSize.ints, myTermId);
      }

      // copy "doc-offsets"
      for (long i = 0; i < other.numberOfDocuments(); i++) {
        long offset = stepOver + other.docOffsetReader.readLong(i*8);
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

    int[] lengths;

    public int numTerms;
    public TIntIntHashMap termTranslationTable = null;

    public IntVocabReader(Directory inputDir) throws IOException {
      corpusReader = new FileChannelSource(inputDir.childPath("intCorpus"));
      docOffsetReader = new FileChannelSource(inputDir.childPath("docOffset"));
      File docNames = inputDir.child("docNames");
      if (docNames.exists()) {
        docNamesReader = IO.openInputStream(docNames);
      }
      File terms = inputDir.child("terms");
      if (terms.exists()) {
        termsReader = IO.openInputStream(terms);
        numTerms = FixedSize.ints.read(termsReader);
      }
      // lengths-array.
      lengths = new int[numberOfDocuments()];
      int ND = numberOfDocuments();
      InputStream stream = docOffsetReader.stream();
      long prev = FixedSize.longs.read(stream);
      for (int i = 1; i < ND; i++) {
        long here = FixedSize.longs.read(stream);
        lengths[i-1] = (int) ((here - prev)/4L);
        prev = here;
      }
      lengths[ND-1] = (int) ((corpusReader.size() - prev)/4L);
    }

    public long numberOfTermOccurrences() throws IOException {
      return corpusReader.size() / 4L;
    }
    public int numberOfDocuments() throws IOException {
      return IntMath.fromLong(docOffsetReader.size() / 8L);
    }
    public int getTerm(long corpusPosition) throws IOException {
      return corpusReader.readInt(corpusPosition * 4);
    }
    public Pair<Long,Long> getDocumentRange(int document) throws IOException {
      if(document >= numberOfDocuments()) throw new RuntimeException("Can't find document above max: "+numberOfDocuments());
      long start = docOffsetReader.readLong(document * 8);
      if((document+1) >= numberOfDocuments()) {
        return Pair.of(start, corpusReader.size());
      }
      return Pair.of(start, docOffsetReader.readLong((document + 1) * 8));
    }
    public int[] getDocument(int document) throws IOException {
      Pair<Long,Long> bounds = getDocumentRange(document);
      int length = IntMath.fromLong(bounds.right - bounds.left);
      if(length == 0) {
        return new int[0];
      }
      if(length <= 0 || bounds.right <= 0) {
        System.err.println(bounds);
        System.err.println(length);
        System.err.println(document);
        System.err.println(docOffsetReader.size());
        System.err.println(docOffsetReader.readLong(document+1)*8);
        System.err.println(docOffsetReader.readLong(document)*8);

      }
      assert(bounds.right >= 0);
      assert(length >= 0);
      int numWords = length/4;
      int[] output = new int[numWords];
      ByteBuffer buf = corpusReader.read(bounds.left, length);
      for (int i = 0; i < numWords; i++) {
        output[i] = buf.getInt(i*4);
      }
      return output;
    }
    public int getTerm(int document, int position) throws IOException {
      long start = docOffsetReader.readLong(document * 8);
      return corpusReader.readInt(start + position * 4);
    }
    public int getLength(int document) throws IOException {
      return lengths[document];
    }
    public IntList getSlice(int document, int position, int width) throws IOException {
      Pair<Long,Long> docRange = getDocumentRange(document);

      int docLength = IntMath.fromLong((docRange.right - docRange.left) / 4L);
      //System.err.println("position:"+position+" width:"+width+" docLength:"+docLength);
      int endPosition = Math.min(position+width, docLength);

      long start = docRange.left + position*4;
      long end = docRange.left + endPosition * 4;

      int length = endPosition - position;
      assert(end <= docRange.right) : "End: "+end+" docRange="+docRange+" length: "+length+" docLength: "+length;
      if(length <= 0) {
        System.err.println("Length[-2]="+getLength(document-2));
        System.err.println("Length[-1]="+getLength(document-1));
        System.err.println("Length[0]="+getLength(document));
        System.err.println("Length[+1]="+getLength(document+1));
        System.err.println("Length[+2]="+getLength(document+2));
        throw new RuntimeException("getSlice("+document+" "+position+" "+width+": dl: "+docLength+" "+length+" "+docRange.left+" "+docRange.right+")");
      }
      int data[] = new int[length];
      ByteBuffer bbuf = corpusReader.read(start, length*4);
      for (int i = 0; i < length; i++) {
        data[i] = bbuf.getInt(i*4);
      }
      return new IntList(data);
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

    public int getNumTerms() {
      return numTerms;
    }

    public void forEachName(SinkFn<Pair<Integer,String>> nameFn) throws IOException {
      for (int i = 0; i < numberOfDocuments(); i++) {
        nameFn.process(Pair.of(i, IntVocabWriter.strCoder.read(docNamesReader)));
      }
      docNamesReader.close();
    }

    public void forEachTerm(SinkFn<Pair<Integer,String>> termFn) throws IOException {
      for (int i = 0; i < numTerms; i++) {
        termFn.process(Pair.of(i, IntVocabWriter.strCoder.read(termsReader)));
      }
      termsReader.close();
    }

    @Override
    public void close() throws IOException {
      termTranslationTable = null;
      corpusReader.close();
      IO.close(docOffsetReader);
      IO.close(docNamesReader);
    }
  }



}
