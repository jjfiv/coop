package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.io.Directory;
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
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
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
  }

  public int getSentenceCount() {
    return IntMath.fromLong(sentenceCorpus.keyCount());
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
