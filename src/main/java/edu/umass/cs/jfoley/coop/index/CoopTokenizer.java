package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.lang.LazyPtr;
import ciir.jfoley.chai.string.StrUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author jfoley
 */
public interface CoopTokenizer {
  List<String> tokenize(String input);

  static CoopTokenizer create(Parameters argp) {
    if(argp.containsKey("tokenizer")) {
      try {
        return (CoopTokenizer) Class.forName(argp.getString("tokenizer")).newInstance();
      } catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    } else {
      return create();
    }
  }

  static CoopTokenizer create() {
    return new StanfordNLPTokenizer();
  }

  class GalagoTokenizer implements CoopTokenizer {
    private final TagTokenizer tok;

    public GalagoTokenizer() {
      tok = new TagTokenizer();
    }

    @Override
    public List<String> tokenize(String input) {
      return tok.tokenize(StrUtil.replaceUnicodeQuotes(input)).terms;
    }
  }

  class StanfordNLPTokenizer implements CoopTokenizer {
    public static LazyPtr<StanfordCoreNLP> nlp = new LazyPtr<>(() -> {
      //List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit", "pos", "lemma");
      List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit");
      Properties props = new Properties();
      props.put("annotators", StrUtil.join(annotators, ","));
      return new StanfordCoreNLP(props);
    });

    @Override
    public List<String> tokenize(String input) {
      Annotation ann = new Annotation(input);
      nlp.get().annotate(ann);
      List<CoreLabel> coreLabels = ann.get(CoreAnnotations.TokensAnnotation.class);
      List<String> terms = new ArrayList<>(coreLabels.size());
      for (CoreLabel coreLabel : coreLabels) {
        String rawTerm = coreLabel.getString(CoreAnnotations.TextAnnotation.class);
        // HACK: lowercase stanford nlp stuff
        terms.add(rawTerm.toLowerCase());
      }
      return terms;
    }
  }
}
