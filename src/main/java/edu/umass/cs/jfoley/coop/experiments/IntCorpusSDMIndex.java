package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.IntRange;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.StreamFns;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.ByteBufferDataChunk;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.counts.CountMetadata;
import edu.umass.cs.ciir.waltz.sys.positions.PIndexWriter;
import edu.umass.cs.jfoley.coop.bills.IntVocabBuilder;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.Arguments;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jfoley
 */
public class IntCorpusSDMIndex {

  public static class Bigram {
    public final int first;
    public final int second;

    public Bigram(int first, int second) {
      this.first = first;
      this.second = second;
    }

    public long asLong() {
      return toLong(first, second);
    }

    public int hashCode() {
      return Integer.hashCode(first) ^ Integer.hashCode(second);
    }
    public boolean equals(Object other) {
      if(other instanceof Bigram) {
        Bigram rhs = (Bigram) other;
        return this.first == rhs.first && this.second == rhs.second;
      }
      return false;
    }

    public static long toLong(int first, int second) {
      return (((long) first) << 32) + second;
    }
    public static Bigram fromLong(long data) {
      return new Bigram((int) (data >> 32), (int) data);
    }
  }

  public static class BigramCoder extends Coder<Bigram> {
    @Nonnull
    @Override
    public Class<?> getTargetClass() {
      return Bigram.class;
    }

    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(Bigram obj) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(8);
      buf.putInt(obj.first);
      buf.putInt(obj.second);
      return ByteBufferDataChunk.of(buf);
    }

    @Nonnull
    @Override
    public Bigram readImpl(InputStream inputStream) throws IOException {
      ByteBuffer buf = ByteBuffer.wrap(StreamFns.readBytes(inputStream, 8));
      int first = buf.getInt();
      int second = buf.getInt();
      return new Bigram(first, second);
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "robust.ints"));
    Directory output = new Directory(argp.get("output", "robust.ints"));

    long startTime = System.currentTimeMillis();

    PostingsConfig<Integer, Integer> countIndexCfg = new PostingsConfig<>(
        FixedSize.ints, VarUInt.instance, Comparing.defaultComparator(), new CountMetadata()
    );
    PostingsConfig<Long, Integer> bigramIndexCfg = new PostingsConfig<>(
        FixedSize.longs, VarUInt.instance, Comparing.defaultComparator(), new CountMetadata()
    );


    IntVocabBuilder.IntVocabReader reader = new IntVocabBuilder.IntVocabReader(input);
    int numDocuments = reader.numberOfDocuments();
    AtomicLong globalPosition = new AtomicLong();
    long numTerms = reader.numberOfTermOccurrences();

    Debouncer msg_u = new Debouncer(5000);
    try (PIndexWriter<Integer, Integer> unigramWriter = countIndexCfg.getWriter(output, "unigram")) {

      for (final int docId : IntRange.exclusive(0, numDocuments)) {
        int[] terms = reader.getDocument(docId);
        TIntIntHashMap unigrams = new TIntIntHashMap(terms.length);

        for (int i = 0; i < terms.length; i++) {
          int term_i = terms[i];
          unigrams.adjustOrPutValue(term_i, 1, 1);
        }

        unigrams.forEachEntry((id, count) -> {
          unigramWriter.add(id, docId, count);
          return true;
        });

        globalPosition.addAndGet(terms.length);

        if (msg_u.ready()) {
          long pos = globalPosition.get();
          System.out.println("#u Progress: " + pos + " / " + numTerms);
          System.out.println(msg_u.estimate(pos, numTerms));
          System.out.println();
        }

      }

      Debouncer msg_od1 = new Debouncer(5000);
      globalPosition.set(0);
      try (PIndexWriter<Long, Integer> bigramWriter = bigramIndexCfg.getWriter(output, "bigram")) {
        for (final int docId : IntRange.exclusive(0, numDocuments)) {
          int[] terms = reader.getDocument(docId);
          TLongIntHashMap bigrams = new TLongIntHashMap(terms.length * 2);

          for (int i = 0; i < terms.length - 1; i++) {
            bigrams.adjustOrPutValue(Bigram.toLong(terms[i], terms[i + 1]), 1, 1);
          }

          bigrams.forEachEntry((big, count) -> {
            bigramWriter.add(big, docId, count);
            return true;
          });

          globalPosition.addAndGet(terms.length);

          if (msg_od1.ready()) {
            long pos = globalPosition.get();
            System.out.println("#od1 Progress: " + pos + " / " + numTerms);
            System.out.println(msg_od1.estimate(pos, numTerms));
            System.out.println();
          }
        }
      }

      Debouncer msg_uw8 = new Debouncer(5000);
      globalPosition.set(0);
      try (PIndexWriter<Long, Integer> ubigramWriter = bigramIndexCfg.getWriter(output, "ubigram")
      ) {
        for (final int docId : IntRange.exclusive(0, numDocuments)) {
          int[] terms = reader.getDocument(docId);
          TLongIntHashMap ubigrams = new TLongIntHashMap(terms.length * 20);

          final int width = 8;
          for (int i = 0; i < terms.length; i++) {
            int term_i = terms[i];
            // ubigrams:
            int windowSize = Math.min(width, terms.length - i);
            for (int j = 1; j < windowSize; j++) {
              int term_j = terms[i + j];
              if (term_i == term_j) {
              } else if (term_i < term_j) {
                ubigrams.adjustOrPutValue(Bigram.toLong(term_i, term_j), 1, 1);
              } else {
                ubigrams.adjustOrPutValue(Bigram.toLong(term_j, term_i), 1, 1);
              }
            }
          }

          ubigrams.forEachEntry((ubig, count) -> {
            ubigramWriter.add(ubig, docId, count);
            return true;
          });

          globalPosition.addAndGet(terms.length);

          if (msg_uw8.ready()) {
            long pos = globalPosition.get();
            System.out.println("#uw8 Progress: " + pos + " / " + numTerms);
            System.out.println(msg_uw8.estimate(pos, numTerms));
            System.out.println();
          }
        }
      }
      long endTime = System.currentTimeMillis();

      System.out.println("Total Time: " + (endTime - startTime));
      // Progress: 249670890 / 252359881
      // 1,414,862.5 items/s  1.9 seconds left, 98.9% complete.
      // Total Time: 242472

      // bills
      // Progress: 324483348 / 334913031
      // 2,179,993.7 items/s  4.8 seconds left, 96.9% complete.
      // Total Time: 181479

      // bills.count index:
      // Progress: 275946653 / 334913031
      // 26,903,251.7 items/s  2.2 seconds left, 82.4% complete.
      // Total Time: 23503

      // bills.unigram+bigram index: (long trick)
      // 3.6m items/s
      // Total Time: 164742

    }
  }
}
