package edu.umass.cs.jfoley.coop.document;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author jfoley
 */
@DefaultSerializer(TaggedFieldSerializer.class)
public class CoopToken implements Comparable<CoopToken> {
  @TaggedFieldSerializer.Tag(1)
  int index;
  @TaggedFieldSerializer.Tag(2)
  int sentence;
  @TaggedFieldSerializer.Tag(3)
  int document;
  @TaggedFieldSerializer.Tag(4)
  Map<String,String> terms;
  @TaggedFieldSerializer.Tag(5)
  Set<String> indicators;
  @TaggedFieldSerializer.Tag(6)
  Set<String> enclosingTags;

  public CoopToken() {
    document = -1;
    sentence = -1;
    index = -1;
    terms = new HashMap<>();
    indicators = new HashSet<>();
  }

  public CoopToken(int document, int index) {
    this();
    this.document = document;
    this.index = index;
  }

  @Override
  public int compareTo(@Nonnull CoopToken o) {
    int cmp = Integer.compare(sentence, o.sentence);
    if(cmp != 0) return cmp;
    return Integer.compare(index, o.index);
  }

  public Set<String> getIndicators() {
    return indicators;
  }

  public Map<String, String> getTerms() {
    return terms;
  }

  public Parameters toJSON() {
    assert(terms instanceof Map);
    Parameters p = Parameters.create();
    p.put("documentId", document);
    p.put("sentenceId", sentence);
    p.put("tokenId", index);
    p.put("terms", Parameters.wrap(terms));
    //p.put("indicators", new ArrayList<>(indicators));
    if(!enclosingTags.isEmpty()) {
      p.put("tags", new ArrayList<>(enclosingTags));
    }
    return p;
  }

  public Set<String> getTags() {
    return enclosingTags;
  }

  public int id() {
    return index;
  }

  public void setSentence(int sentence) {
    this.sentence = sentence;
  }
}
