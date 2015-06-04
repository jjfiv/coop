package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.lang.LazyPtr;
import ciir.jfoley.chai.string.StrUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.umass.cs.jfoley.coop.document.CoopDoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author jfoley
 */
public class StanfordNLPTokenizer implements CoopTokenizer {
  public static LazyPtr<StanfordCoreNLP> nlp = new LazyPtr<>(() -> {
    //List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit", "pos", "lemma");
    List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit");
    Properties props = new Properties();
    props.put("annotators", StrUtil.join(annotators, ","));
    return new StanfordCoreNLP(props);
  });

  public static void collectSentenceTags(Annotation ann, CoopDoc doc) {
    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    // tokenIndex
    int tokenIndex = 0;
    for (CoreMap sentence : sentences) {
      int sentenceLength = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
      doc.addTag("sentence", tokenIndex, tokenIndex + sentenceLength);
      tokenIndex += sentenceLength;
    }
  }

  public static List<String> collectTerms(Annotation ann) {
    List<CoreLabel> coreLabels = ann.get(CoreAnnotations.TokensAnnotation.class);
    List<String> terms = new ArrayList<>(coreLabels.size());
    for (CoreLabel coreLabel : coreLabels) {
      String rawTerm = coreLabel.getString(CoreAnnotations.TextAnnotation.class);
      // HACK: lowercase stanford nlp stuff
      terms.add(rawTerm.toLowerCase());
    }
    return terms;
  }

  @Override
  public CoopDoc createDocument(String name, String text) {
    CoopDoc cdoc = new CoopDoc();
    cdoc.setName(name);
    cdoc.setRawText(text);

    Annotation ann = new Annotation(text);
    nlp.get().annotate(ann);

    // Grab terms no matter what.
    cdoc.setTerms(collectTerms(ann));

    // sentence tag.
    collectSentenceTags(ann, cdoc);

    return cdoc;
  }
}
