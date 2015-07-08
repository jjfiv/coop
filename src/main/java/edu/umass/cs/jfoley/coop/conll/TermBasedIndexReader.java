package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.QuickSort;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.data.BufferList;
import edu.umass.cs.ciir.waltz.coders.data.DataChunk;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.streams.SkipInputStream;
import edu.umass.cs.ciir.waltz.coders.streams.StaticStream;
import edu.umass.cs.ciir.waltz.dociter.KeyBlock;
import edu.umass.cs.ciir.waltz.dociter.movement.AMover;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.format.BlockedPostingsCoder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.conll.classifier.ClassifierSystem;
import edu.umass.cs.jfoley.coop.conll.classifier.FeatureVector;
import edu.umass.cs.jfoley.coop.conll.classifier.SparseBooleanFeatures;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class TermBasedIndexReader implements Closeable {
  public final Directory input;
  public final IOMap<Integer, List<Integer>> sentenceToTokens;
  public final IOMap<Integer, Integer> tokenToSentence;
  public final IOMap<Integer, List<CoopToken>> sentenceCorpus;
  public final IOMap<Integer, CoopToken> tokenCorpus;
  public final IOMap<Integer, CoopDoc> documentCorpus;
  /**
   * Inverted index for features
   */
  public final IOMap<String, Mover> featureIndex;
  public final IOMap<NamespacedLabel, Mover> tokensByTerms;
  public final IOMap<String, Mover> tokensByTags;
  public final IOMap<NamespacedLabel, PostingMover<Integer>> sentencesByTerms;
  public final IOMap<NamespacedLabel, PostingMover<Integer>> documentsByTerms;
  public final IdMaps.Reader<String> documentNames;
  public final IdMaps.Reader<String> features;

  public final ClassifierSystem classifiers;

  public static class DeltaIntListMoverCoder extends Coder<Mover> {
    Coder<Integer> itemCoder = VarUInt.instance;
    Coder<Integer> countCoder = VarUInt.instance;

    @Override
    public boolean knowsOwnSize() {
      return true;
    }

    @Nonnull
    @Override
    public DataChunk writeImpl(Mover obj) throws IOException {
      BufferList bl = new BufferList();
      int total = obj.totalKeys();
      bl.add(countCoder, total);
      int prev = 0;

      for(; !obj.isDone(); obj.nextBlock()) {
        BufferList block = new BufferList();
        for(; !obj.isDoneWithBlock(); obj.nextKey()) {
          int x = obj.currentKey();
          int delta = x - prev;
          bl.add(itemCoder, delta);
          prev = x;
        }
        bl.add(block.compact());
      }
      return bl;
    }

    public static class DeltaIntListStreamMover extends AMover {
      final StaticStream streamFn;
      InputStream stream;
      int total = 0;
      int currentIndex = 0;
      int previousValue = 0;
      Coder<Integer> itemCoder = VarUInt.instance;
      Coder<Integer> countCoder = VarUInt.instance;

      public DeltaIntListStreamMover(StaticStream streamFn) {
        this.streamFn = streamFn;
        reset();
      }

      @Override
      public void nextBlock() {
        this.currentBlock = null;
        this.index = 0;

        IntList block = new IntList();
        int end = Math.min(total, currentIndex+128);
        try {
          for (; currentIndex < end; currentIndex++) {
            previousValue += itemCoder.read(stream);
            block.add(previousValue);
          }
          if (block.isEmpty()) {
            return;
          }
          currentBlock = new KeyBlock(block);
        } catch (Exception e) {
          System.out.printf("total: %d, end: %d\n", total, end);
          throw e;
        }
      }

      @Override
      public void reset() {
        stream = streamFn.getNewStream();
        total = countCoder.read(stream);
        currentIndex = 0;
        previousValue = 0;
        nextBlock();
      }

      @Override
      public int totalKeys() {
        return total;
      }
    }

    @Nonnull
    @Override
    public Mover read(StaticStream streamFn) throws IOException {
      return new DeltaIntListStreamMover(streamFn);
    }

    @Nonnull
    @Override
    public Mover readImpl(InputStream inputStream) throws IOException {
      return read(new StaticStream() {
        @Override
        public SkipInputStream getNewStream() {
          return SkipInputStream.wrap(inputStream);
        }

        @Override
        public long length() {
          throw new UnsupportedOperationException();
        }
      });
    }
  }

  public TermBasedIndexReader(Directory input) throws IOException {
    this.input = input;
    sentenceToTokens = GalagoIO.openIOMap(
        VarUInt.instance, new DeltaIntListCoder(),
        input.childPath("sentenceToTokens")
    );
    tokenToSentence = GalagoIO.openIOMap(
        VarUInt.instance, VarUInt.instance,
        input.childPath("tokenToSentence")
    );
    Coder<CoopToken> tokenCoder = new KryoCoder<>(CoopToken.class);
    sentenceCorpus = GalagoIO.openIOMap(
        VarUInt.instance, new ListCoder<>(tokenCoder),
        input.childPath("sentenceCorpus")
    );
    tokenCorpus = GalagoIO.openIOMap(
        VarUInt.instance, tokenCoder,
        input.childPath("tokenCorpus")
    );
    documentCorpus  = GalagoIO.openIOMap(
        VarUInt.instance, new KryoCoder<>(CoopDoc.class),
        input.childPath("documentCorpus")
    );
    featureIndex = GalagoIO.openIOMap(
        CharsetCoders.utf8, new DeltaIntListMoverCoder(),
        input.childPath("featureIndex")
    );
    tokensByTags = GalagoIO.openIOMap(
        CharsetCoders.utf8, new DeltaIntListMoverCoder(),
        input.childPath("tokensByTags")
    );
    tokensByTerms = GalagoIO.openIOMap(
        NamespacedLabel.coder, new DeltaIntListMoverCoder(),
        input.childPath("tokensByTerms")
    );
    documentsByTerms = GalagoIO.openIOMap(
        NamespacedLabel.coder,
        new BlockedPostingsCoder<>(VarUInt.instance),
        input.childPath("documentsByTerms")
    );
    sentencesByTerms = GalagoIO.openIOMap(
        NamespacedLabel.coder,
        new BlockedPostingsCoder<>(VarUInt.instance),
        input.childPath("sentencesByTerms")
    );
    documentNames = GalagoIO.openIdMapsReader(input.childPath("documentNames"), VarUInt.instance, CharsetCoders.utf8);

    if(!input.child("features.fwd").exists()) {
      System.err.println("Creating features file: N="+featureIndex.keyCount());
      List<String> featuresAboveThreshold = new ArrayList<>();
      for (List<String> keyBatch : IterableFns.batches(featureIndex.keys(), 1000)) {
        for (Pair<String, Mover> kv : featureIndex.getInBulk(keyBatch)) {
          if (kv.getValue().totalKeys() > 5) {
            featuresAboveThreshold.add(kv.getKey());
          }
        }
      }
      QuickSort.sort(featuresAboveThreshold);
      System.err.println("Creating features file: N*="+featuresAboveThreshold.size());

      try (IdMaps.Writer<String> featuresWriter = GalagoIO.openIdMapsWriter(input.childPath("features"), VarUInt.instance, CharsetCoders.utf8)) {
        for (int i = 0; i < featuresAboveThreshold.size(); i++) {
          featuresWriter.put(i, featuresAboveThreshold.get(i));
        }
      }
    }

    this.features = GalagoIO.openIdMapsReader(input.childPath("features"), VarUInt.instance, CharsetCoders.utf8);

    this.classifiers = new ClassifierSystem(this);
  }

  public int getSentenceCount() {
    return IntMath.fromLong(sentenceCorpus.keyCount());
  }

  public List<Pair<String,FeatureVector>> pullLabeledFeatures(String tokenSet, List<Integer> tokenIds) throws IOException {
    List<Pair<String, FeatureVector>> posF = new ArrayList<>(tokenIds.size());
    List<String> relevantFeatures = classifiers.featuresAboveThreshold;

    for (List<Integer> tokenBatch : IterableFns.batches(tokenIds, 1000)) {
      for (Pair<Integer, CoopToken> kv : tokenCorpus.getInBulk(tokenBatch)) {
        String label = kv.getValue().getTerms().get(tokenSet);

        IntList features = new IntList(kv.getValue().getIndicators().size());
        for (String indicator : kv.getValue().getIndicators()) {
          int pos = Collections.binarySearch(relevantFeatures, indicator);
          if(pos < 0) continue;
          features.add(pos);
        }
        posF.add(Pair.of(label, new SparseBooleanFeatures(features)));
      }
    }

    return posF;
  }


  public List<Pair<CoopToken, FeatureVector>> TokenFeatures(List<Integer> tokenIds) throws IOException {
    List<Pair<CoopToken, FeatureVector>> posF = new ArrayList<>(tokenIds.size());
    List<String> relevantFeatures = classifiers.featuresAboveThreshold;

    for (List<Integer> tokenBatch : IterableFns.batches(tokenIds, 1000)) {
      for (Pair<Integer, CoopToken> kv : tokenCorpus.getInBulk(tokenBatch)) {
        IntList features = new IntList(kv.getValue().getIndicators().size());
        for (String indicator : kv.getValue().getIndicators()) {
          int pos = Collections.binarySearch(relevantFeatures, indicator);
          if(pos < 0) continue;
          features.add(pos);
        }
        posF.add(Pair.of(kv.getValue(), new SparseBooleanFeatures(features)));
      }
    }

    return posF;
  }

  public List<FeatureVector> pullFeatures(List<Integer> tokenIds) throws IOException {
    List<FeatureVector> posF = new ArrayList<>(tokenIds.size());
    List<String> relevantFeatures = classifiers.featuresAboveThreshold;

    for (List<Integer> tokenBatch : IterableFns.batches(tokenIds, 1000)) {
      for (Pair<Integer, CoopToken> kv : tokenCorpus.getInBulk(tokenBatch)) {
        IntList features = new IntList(kv.getValue().getIndicators().size());
        for (String indicator : kv.getValue().getIndicators()) {
          int pos = Collections.binarySearch(relevantFeatures, indicator);
          if(pos < 0) continue;
          features.add(pos);
        }
        posF.add(new SparseBooleanFeatures(features));
      }
    }

    return posF;
  }

  @Override
  public void close() throws IOException {
    sentenceToTokens.close();
    tokenToSentence.close();
    sentenceCorpus.close();
    tokenCorpus.close();
    featureIndex.close();
    tokensByTerms.close();
    sentencesByTerms.close();
    classifiers.close();
  }

  public List<List<CoopToken>> pullSentences(List<Integer> ids) throws IOException {
    ArrayList<List<CoopToken>> data = new ArrayList<>();
    for (Pair<Integer, List<CoopToken>> kv : sentenceCorpus.getInBulk(ids)) {
      data.add(kv.getValue());
    }
    return data;
  }

  public int numFeatures() {
    return classifiers.featuresAboveThreshold.size();
  }
}
