package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.list.IntList;
import org.lemurproject.galago.utility.Parameters;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author jfoley
 */
public class ClassifiedData {
  public Set<LabeledToken> labelEvents;
  public String name;
  public String description;

  public ClassifiedData() {
    this(null, null, new HashSet<>());
  }

  public ClassifiedData(String name) {
    this(name, null, new HashSet<>());
  }

  public ClassifiedData(String name, String description, Collection<? extends LabeledToken> labeledTokens) {
    this.name = name;
    this.description = description;
    this.labelEvents = new HashSet<>(labeledTokens);
  }

  public IntList positive() {
    IntList out = new IntList(labelEvents.size()/2);
    for (LabeledToken labelEvent : labelEvents) {
      if(labelEvent.positive) {
        out.add(labelEvent.tokenId);
      }
    }
    return out;
  }

  public IntList negative() {
    IntList out = new IntList(labelEvents.size()/2);
    for (LabeledToken labelEvent : labelEvents) {
      if(!labelEvent.positive) {
        out.add(labelEvent.tokenId);
      }
    }
    return out;
  }

  public synchronized Parameters getInfo() {
    Parameters output = Parameters.create();
    output.put("totalEvents", labelEvents.size());
    output.put("name", name);
    output.put("description", description);
    output.put("positive", positive());
    output.put("negative", negative());
    return output;
  }

  public boolean add(List<LabeledToken> labeled) {
    return labelEvents.addAll(labeled);
  }
}
