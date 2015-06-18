package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import edu.umass.cs.jfoley.coop.index.general.DocumentSetWriter;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class TermBasedIndexWriter implements Closeable {
  public final Directory output;
  private int sentenceIndex;
  private int tokenIndex;

  final IOMapWriter<Integer, List<Integer>> sentenceToTokens;
  final IOMapWriter<Integer, Integer> tokenToSentence;
  final IOMapWriter<Integer, List<TermBasedIndex.SentenceIndexedToken>> sentenceCorpus;
  final IOMapWriter<Integer, TermBasedIndex.SentenceIndexedToken> tokenCorpus;
  /**
   * Inverted index for features
   */
  final DocumentSetWriter<String> featureIndex;
  final DocumentSetWriter<NamespacedLabel> tokensByTerms;
  final StreamingPostingBuilder<NamespacedLabel, Integer> sentencesByTerms;

  public TermBasedIndexWriter(Directory output) throws IOException {
    this.output = output;
    this.sentenceIndex = 0;
    this.tokenIndex = 0;

    sentenceToTokens = GalagoIO.getIOMapWriter(
        VarUInt.instance, new DeltaIntListCoder(),
        output.childPath("sentenceToTokens")
    );
    tokenToSentence = GalagoIO.getIOMapWriter(
        VarUInt.instance, VarUInt.instance,
        output.childPath("tokenToSentence")
    );
    Coder<TermBasedIndex.SentenceIndexedToken> tokenCoder = new KryoCoder<>(TermBasedIndex.SentenceIndexedToken.class);
    sentenceCorpus = GalagoIO.getIOMapWriter(
        VarUInt.instance, new ListCoder<>(tokenCoder),
        output.childPath("sentenceCorpus")
    );
    tokenCorpus = GalagoIO.getIOMapWriter(
        VarUInt.instance, tokenCoder,
        output.childPath("tokenCorpus")
    );
    featureIndex = new DocumentSetWriter<>(
        GalagoIO.getIOMapWriter(
            CharsetCoders.utf8, new DeltaIntListCoder(),
            output.childPath("featureIndex")
        )
    );
    tokensByTerms = new DocumentSetWriter<>(
        GalagoIO.getIOMapWriter(
            NamespacedLabel.coder, new DeltaIntListCoder(),
            output.childPath("tokensByTerms")
        )
    );
    sentencesByTerms = new StreamingPostingBuilder<>(
        NamespacedLabel.coder, VarUInt.instance,
        GalagoIO.getRawIOMapWriter(output.childPath("sentencesByTerms"))
    );
  }

  public void addSentence(List<CoopToken> sentence) throws IOException {

    List<TermBasedIndex.SentenceIndexedToken> tokens = new ArrayList<>();

    int sentenceId = sentenceIndex++;
    IntList tokenIds = new IntList();
    TObjectIntHashMap<NamespacedLabel> ttf = new TObjectIntHashMap<>();
    for (CoopToken coopToken : sentence) {
      int tokenId = tokenIndex++;
      TermBasedIndex.SentenceIndexedToken token = new TermBasedIndex.SentenceIndexedToken(sentenceId, tokenId);
      tokens.add(token);
      tokenIds.add(tokenId);
      tokenCorpus.put(tokenId, token);

      for (String feature : coopToken.getIndicators()) {
        featureIndex.process(feature, tokenId);
      }
      for (Map.Entry<String, String> kv : coopToken.getTerms().entrySet()) {
        NamespacedLabel termTypeAndTerm = new NamespacedLabel(kv.getKey(), kv.getValue());
        tokensByTerms.process(termTypeAndTerm, tokenId);
        ttf.adjustOrPutValue(termTypeAndTerm, 1, 1);
      }

      tokenToSentence.put(tokenId, sentenceIndex);
    }

    ttf.forEachEntry((k, count) -> {
      sentencesByTerms.add(k, sentenceId, count);
      return true;
    });

    sentenceToTokens.put(sentenceId, tokenIds);
    sentenceCorpus.put(sentenceId, tokens);
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
}
