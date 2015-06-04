package edu.umass.cs.jfoley.coop.index;

import ciir.jfoley.chai.lang.LazyPtr;
import ciir.jfoley.chai.string.StrUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.util.*;

/**
 * @author jfoley
 */
public interface CoopTokenizer {
  List<String> tokenize(String input);
  CoopDoc createDocument(String name, String text);

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

  /**
   * One of the advantages of this GalagoTokenizer is that it handles XML tags if you like.
   */
  class GalagoTokenizer implements CoopTokenizer {
    private final TagTokenizer tok;

    public GalagoTokenizer() {
      this(Collections.emptyList());
    }
    public GalagoTokenizer(List<String> tagsToKeep) {
      tok = new TagTokenizer();
      tagsToKeep.forEach(tok::addField);
    }

    @Override
    public List<String> tokenize(String input) {
      return tok.tokenize(StrUtil.replaceUnicodeQuotes(input)).terms;
    }

    @Override
    public CoopDoc createDocument(String name, String text) {
      Document doc = tok.tokenize(StrUtil.replaceUnicodeQuotes(text));
      CoopDoc cdoc = new CoopDoc();
      cdoc.setName(name);
      cdoc.setTerms(doc.terms);
      cdoc.setRawText(doc.text);
      for (Tag tag : doc.tags) {
        cdoc.addTag(tag.name, tag.begin, tag.end);
      }
      return cdoc;
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
}
