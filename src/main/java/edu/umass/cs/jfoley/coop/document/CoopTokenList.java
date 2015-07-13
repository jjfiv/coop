package edu.umass.cs.jfoley.coop.document;

import com.esotericsoftware.kryo.serializers.CollectionSerializer;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Having this class as a wrapper makes it serialize faster.
 * @author jfoley
 */
public class CoopTokenList {
  @CollectionSerializer.BindCollection(elementClass = CoopToken.class)
  public final ArrayList<CoopToken> tokens;

  public CoopTokenList() {
    this.tokens = new ArrayList<>();
  }
  public CoopTokenList(Collection<? extends CoopToken> tokens) {
    this.tokens = new ArrayList<>(tokens);
  }
}
