package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.QuickSort;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.format.BlockedPostingsCoder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.conll.classifier.ClassifierSystem;
import edu.umass.cs.jfoley.coop.conll.classifier.SparseBooleanFeatures;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;

import java.io.Closeable;
import java.io.IOException;
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
  public final IOMap<Integer, List<SentenceIndexedToken>> sentenceCorpus;
  public final IOMap<Integer, SentenceIndexedToken> tokenCorpus;
  /**
   * Inverted index for features
   */
  public final IOMap<String, List<Integer>> featureIndex;
  public final IOMap<NamespacedLabel, List<Integer>> tokensByTerms;
  public final IOMap<NamespacedLabel, PostingMover<Integer>> sentencesByTerms;
  public final IdMaps.Reader<String> features;

  public final ClassifierSystem classifiers;

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
    Coder<SentenceIndexedToken> tokenCoder = new KryoCoder<>(SentenceIndexedToken.class);
    sentenceCorpus = GalagoIO.openIOMap(
        VarUInt.instance, new ListCoder<>(tokenCoder),
        input.childPath("sentenceCorpus")
    );
    tokenCorpus = GalagoIO.openIOMap(
        VarUInt.instance, tokenCoder,
        input.childPath("tokenCorpus")
    );
    featureIndex = GalagoIO.openIOMap(
        CharsetCoders.utf8, new DeltaIntListCoder(),
        input.childPath("featureIndex")
    );
    tokensByTerms = GalagoIO.openIOMap(
        NamespacedLabel.coder, new DeltaIntListCoder(),
        input.childPath("tokensByTerms")
    );
    sentencesByTerms = GalagoIO.openIOMap(
        NamespacedLabel.coder,
        new BlockedPostingsCoder<Integer>(VarUInt.instance),
        input.childPath("sentencesByTerms")
    );

    if(!input.child("features.fwd").exists()) {
      List<String> featuresAboveThreshold = new ArrayList<>();
      for (List<String> keyBatch : IterableFns.batches(featureIndex.keys(), 1000)) {
        for (Pair<String, List<Integer>> kv : featureIndex.getInBulk(keyBatch)) {
          if (kv.getValue().size() > 5) {
            featuresAboveThreshold.add(kv.getKey());
          }
        }
      }
      QuickSort.sort(featuresAboveThreshold);

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

  public List<Pair<String,SparseBooleanFeatures>> pullLabeledFeatures(String tokenSet, List<Integer> tokenIds) throws IOException {
    List<Pair<String, SparseBooleanFeatures>> posF = new ArrayList<>(tokenIds.size());
    List<String> relevantFeatures = classifiers.featuresAboveThreshold;

    for (List<Integer> tokenBatch : IterableFns.batches(tokenIds, 1000)) {
      for (Pair<Integer, SentenceIndexedToken> kv : tokenCorpus.getInBulk(tokenBatch)) {
        String label = kv.getValue().getTerms().get(tokenSet);

        IntList features = new IntList(kv.getValue().indicators.size());
        for (String indicator : kv.getValue().indicators) {
          int pos = Collections.binarySearch(relevantFeatures, indicator);
          if(pos < 0) continue;
          features.add(pos);
        }
        posF.add(Pair.of(label, new SparseBooleanFeatures(features)));
      }
    }

    return posF;
  }

  public List<SparseBooleanFeatures> pullFeatures(List<Integer> tokenIds) throws IOException {
    List<SparseBooleanFeatures> posF = new ArrayList<>(tokenIds.size());
    List<String> relevantFeatures = classifiers.featuresAboveThreshold;

    for (List<Integer> tokenBatch : IterableFns.batches(tokenIds, 1000)) {
      for (Pair<Integer, SentenceIndexedToken> kv : tokenCorpus.getInBulk(tokenBatch)) {
        IntList features = new IntList(kv.getValue().indicators.size());
        for (String indicator : kv.getValue().indicators) {
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
  }

  public List<List<SentenceIndexedToken>> pullSentences(List<Integer> ids) throws IOException {
    ArrayList<List<SentenceIndexedToken>> data = new ArrayList<>();
    for (Pair<Integer, List<SentenceIndexedToken>> kv : sentenceCorpus.getInBulk(ids)) {
      data.add(kv.getValue());
    }
    return data;
  }
}
