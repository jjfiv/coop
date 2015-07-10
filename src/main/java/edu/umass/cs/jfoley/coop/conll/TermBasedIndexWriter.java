package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.*;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.streaming.StreamingPostingBuilder;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import edu.umass.cs.ciir.waltz.postings.docset.DocumentSetWriter;
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
  private int documentIndex;
  private int sentenceIndex;
  private int tokenIndex;

  final IOMapWriter<Integer, List<Integer>> sentenceToTokens;
  final IOMapWriter<Integer, Integer> tokenToSentence;
  final IOMapWriter<Integer, CoopDoc> documentCorpus;
  final IOMapWriter<Integer, List<CoopToken>> sentenceCorpus;
  final IOMapWriter<Integer, CoopToken> tokenCorpus;
  final IdMaps.Writer<String> documentNames;
  /**
   * Inverted index for features
   */
  final DocumentSetWriter<String> featureIndex;
  final DocumentSetWriter<NamespacedLabel> tokensByTerms;
  final DocumentSetWriter<String> tokensByTags;
  final StreamingPostingBuilder<NamespacedLabel, Integer> sentencesByTerms;
  final StreamingPostingBuilder<NamespacedLabel, Integer> documentsByTerms;

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
    documentCorpus = GalagoIO.getIOMapWriter(
        VarUInt.instance, new LZFCoder<>(new KryoCoder<>(CoopDoc.class)),
        output.childPath("documentCorpus")
    );
    Coder<CoopToken> tokenCoder = new KryoCoder<>(CoopToken.class);
    sentenceCorpus = GalagoIO.getIOMapWriter(
        VarUInt.instance, new LZFCoder<>(new ListCoder<>(tokenCoder)),
        output.childPath("sentenceCorpus")
    );
    tokenCorpus = GalagoIO.getIOMapWriter(
        VarUInt.instance, new LZFCoder<>(tokenCoder),
        output.childPath("tokenCorpus")
    );
    featureIndex = new DocumentSetWriter<>(
        CharsetCoders.utf8,
        output, "featureIndex"
    );
    tokensByTerms = new DocumentSetWriter<>(
        NamespacedLabel.coder,
        output, "tokensByTerms"
    );
    tokensByTags = new DocumentSetWriter<>(
        CharsetCoders.utf8,
        output, "tokensByTags"
    );
    sentencesByTerms = new StreamingPostingBuilder<>(
        NamespacedLabel.coder, VarUInt.instance,
        GalagoIO.getRawIOMapWriter(output.childPath("sentencesByTerms"))
    );
    documentsByTerms = new StreamingPostingBuilder<>(
        NamespacedLabel.coder, VarUInt.instance,
        GalagoIO.getRawIOMapWriter(output.childPath("documentsByTerms"))
    );

    documentNames = GalagoIO.openIdMapsWriter(output.childPath("documentNames"), VarUInt.instance, CharsetCoders.utf8);
  }

  private void addSentence(List<CoopToken> sentence, TObjectIntHashMap<NamespacedLabel> docfreqs) throws IOException {

    List<CoopToken> tokens = new ArrayList<>();

    int sentenceId = sentenceIndex++;
    IntList tokenIds = new IntList();
    TObjectIntHashMap<NamespacedLabel> ttf = new TObjectIntHashMap<>();
    for (CoopToken token : sentence) {
      token.setSentence(sentenceId);
      int tokenId = tokenIndex++;

      tokens.add(token);
      tokenIds.add(tokenId);
      tokenCorpus.put(tokenId, token);

      processFeatures(token, tokenId);
      processTerms(ttf, token, tokenId);
      processTags(token, tokenId);

      tokenToSentence.put(tokenId, sentenceId);
    }

    ttf.forEachEntry((k, count) -> {
      sentencesByTerms.add(k, sentenceId, count);
      docfreqs.adjustOrPutValue(k, count, count);
      return true;
    });

    sentenceToTokens.put(sentenceId, tokenIds);
    sentenceCorpus.put(sentenceId, tokens);
  }

  private void processTags(CoopToken token, int tokenId) throws IOException {
    for (String tag : token.getTags()) {
      tokensByTags.process(tag, tokenId);
    }
  }

  private void processFeatures(CoopToken token, int tokenId) throws IOException {
    for (String feature : token.getIndicators()) {
      featureIndex.process(feature, tokenId);
    }
  }

  private void processTerms(TObjectIntHashMap<NamespacedLabel> ttf, CoopToken token, int tokenId) throws IOException {
    for (Map.Entry<String, String> kv : token.getTerms().entrySet()) {
      NamespacedLabel termTypeAndTerm = new NamespacedLabel(kv.getKey(), kv.getValue());
      tokensByTerms.process(termTypeAndTerm, tokenId);
      ttf.adjustOrPutValue(termTypeAndTerm, 1, 1);
    }
  }

  public void addDocument(CoopDoc doc) throws IOException {
    int docId = documentIndex++;
    doc.setIdentifier(docId);
    documentNames.put(docId, doc.getName());
    documentCorpus.put(docId, doc);
    TObjectIntHashMap<NamespacedLabel> docfreqs = new TObjectIntHashMap<>();
    for (List<CoopToken> coopTokens : doc.getSentences()) {
      addSentence(coopTokens, docfreqs);
    }

    docfreqs.forEachEntry((k, count) -> {
      documentsByTerms.add(k, docId, count);
      return true;
    });
  }

  @Override
  public void close() throws IOException {
    documentCorpus.close();
    sentenceToTokens.close();
    tokenToSentence.close();
    sentenceCorpus.close();
    tokenCorpus.close();
    featureIndex.close();
    tokensByTerms.close();
    tokensByTags.close();
    sentencesByTerms.close();
    documentsByTerms.close();
    documentNames.close();

    output.ls(System.out);
  }

  public int documentCount() {
    return documentIndex;
  }
}
