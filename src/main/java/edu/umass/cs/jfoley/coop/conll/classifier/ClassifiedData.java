package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.lang.annotations.UsedByReflection;
import org.lemurproject.galago.utility.Parameters;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jfoley
 */
public class ClassifiedData {
  public Set<LabeledToken> labelEvents;
  public String id;
  public String name;
  public String description;

  @UsedByReflection
  public ClassifiedData() {
    this(null, null, new HashSet<>());
  }

  public ClassifiedData(String name) {
    this(name, null, new HashSet<>());
  }

  public ClassifiedData(String name, String description, Collection<? extends LabeledToken> labeledTokens) {
    this.id = name;
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
    output.put("labelEvents", labelEvents.stream().map(LabeledToken::toJSON).collect(Collectors.toList()));
    output.put("positive", positive());
    output.put("negative", negative());
    return output;
  }

  public boolean add(List<LabeledToken> labeled) {
    return labelEvents.addAll(labeled);
  }

  public void setName(String name) {
    this.name = name;
  }
}
