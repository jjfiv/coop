package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class IntListTrie {
  /** Re-use root node, since a loop back to the beginning would be degenerate */
  public static final int NO_MATCH = 0;

  ArrayList<AbstractTrieNode> nodes;
  int size;

  public IntListTrie() {
    nodes = new ArrayList<>();
    nodes.add(new TrieNode(this, 1));
    size = 0;
  }

  public TrieNode root() {
    return (TrieNode) nodes.get(0);
  }

  public int size() {
    return size;
  }

  public static abstract class AbstractTrieNode {
    protected final IntListTrie trie;
    protected final int myIndex;

    protected AbstractTrieNode(IntListTrie trie, int myIndex) {
      this.trie = trie;
      this.myIndex = myIndex;
    }

    public abstract boolean matches(int data[], int index, int size);
    public abstract void insert(int data[], int index, int size);

    public abstract int selfCount();
  }

  public static class LeafNode extends AbstractTrieNode {
    IntList tail;

    public LeafNode(IntListTrie trie, int myIndex, int[] data, int index, int size) {
      super(trie, myIndex);
      tail = new IntList(size-index);
      for (int i = index; i < size; i++) {
        tail.push(data[i]);
      }
    }

    @Override
    public boolean matches(int data[], int index, int size) {
      int qsize = size - index;
      if(tail.size() != qsize) return false;
      for (int i = 0; i < qsize; i++) {
        if(tail.getQuick(i) != data[i+index]) return false;
      }
      return true;
    }

    @Override
    public void insert(int[] data, int index, int size) {
      TrieNode replacement = new TrieNode(trie, this.myIndex); // take over previous index
      trie.nodes.set(this.myIndex, replacement);
      replacement.insert(tail.unsafeArray(), 0, tail.size());
      replacement.insert(data, index, size);
      this.tail = null;
    }

    @Override
    public int selfCount() {
      return 1;
    }
  }

  public static class TrieNode extends AbstractTrieNode {
    int myIndex;
    TIntIntHashMap keyMapping;
    TIntHashSet doneMapping;

    public TrieNode(IntListTrie trie, int myIndex) {
      super(trie, myIndex);
      keyMapping = new TIntIntHashMap();
      doneMapping = new TIntHashSet();
    }

    @Override
    public boolean matches(int[] data, int index, int size) {
      if(index >= size) return false;
      if(index+1 == size) {
        return containsLast(data[index]);
      }
      int pos = find(data[index]);
      if(pos == NO_MATCH) return false;
      return trie.nodes.get(pos).matches(data, index + 1, size);
    }

    @Override
    public void insert(int[] data, int index, int size) {
      // nothing to insert:
      if(index >= size) return;

      // this is the last character:
      if(index+1 == size) {
        markLast(data[index]);
        return;
      }

      // this is an intermediate character:
      int pos = find(data[index]);
      if(pos == NO_MATCH) {
        int id = trie.nodes.size();
        LeafNode node = new LeafNode(trie, id, data, index, size);
        trie.nodes.add(node);
        keyMapping.put(data[index], id);
      } else {
        trie.nodes.get(pos).insert(data, index+1, size);
      }
    }

    public int find(int i) {
      if(keyMapping.containsKey(i)) {
        return keyMapping.get(i);
      }
      return NO_MATCH;
    }

    public void insert(int i, TrieNode rest) {
      assert(!keyMapping.containsKey(i));
      keyMapping.put(i, rest.myIndex);
    }

    public void markLast(int i) {
      doneMapping.add(i);
    }

    public boolean containsLast(int i) {
      return doneMapping.contains(i);
    }

    @Override
    public String toString() {
      return "TrieNode["+myIndex+"]={keys="+keyMapping+", done={"+doneMapping+"}";
    }

    @Override
    public int selfCount() {
      return keyMapping.size() + doneMapping.size();
    }
  }

  public static class IntListCmp implements Comparator<IntList> {
    @Override
    public int compare(IntList o1, IntList o2) {
      int size = Math.min(o1.size(), o2.size());
      for (int i = 0; i < size; i++) {
        int cmp = Integer.compare(o1.getQuick(i), o2.getQuick(i));
        if(cmp != 0) return cmp;
      }
      return 0;
    }

  }
  private void insert(IntList aData) {
    insert(root(), aData.unsafeArray(), 0, aData.size());
  }

  private boolean contains(IntList key) {
    return contains(root(), key.unsafeArray(), 0, key.size());
  }

  private boolean contains(AbstractTrieNode root, int[] data, int index, int size) {
    return root.matches(data, index, size);
  }

  private void insert(TrieNode root, int[] data, int index, int size) {
    this.size++;
    root.insert(data, index, size);
  }

  public static void main(String[] args) throws IOException {
    List<IntList> data = Arrays.asList(
        new IntList(Arrays.asList(1,2,3,4)),
        new IntList(Arrays.asList(1,2,3)),
        new IntList(Arrays.asList(3,2,3)),
        new IntList(Arrays.asList(3,2)),
        new IntList(Arrays.asList(3)),
        new IntList(Arrays.asList(2)),
        new IntList(Arrays.asList(1))
    );

    Collections.sort(data, new IntListCmp());

    System.out.println(data);

    IntListTrie trie = new IntListTrie();
    for (IntList aData : data) {
      trie.insert(aData);
      System.out.println(trie.nodes);
    }

    for (IntList key : data) {
      assert(trie.contains(key));
    }

    IntListTrie phrases = new IntListTrie();

    //int N = 57892545;
    int Limit = 5000000;
    ArrayList<IntList> flat = new ArrayList<>(Limit);
    Debouncer msg = new Debouncer();
    try (LinesIterable lines = LinesIterable.fromFile("bills.ints/phrase.vocab.gz")) {
      for (String line : lines) {
        String[] cols = line.split("\\s+");
        if(msg.ready()) {
          System.err.println("# rate: "+msg.estimate(lines.getLineNumber(), Limit));
        }
        IntList slice = new IntList(cols.length-1);
        for (int i = 1; i < cols.length; i++) {
          slice.add(Integer.parseInt(cols[i]));
        }
        phrases.insert(slice);
        flat.add(slice);
        if(phrases.size() > Limit) break;
      }
    }

    StreamingStats lookupTime = new StreamingStats();
    for (IntList query : flat) {
      long start = System.nanoTime();
      assert(phrases.contains(query));
      long end = System.nanoTime();
      lookupTime.push((end-start) /1e9);
    }
    System.out.println(lookupTime);

    System.out.println(flat.subList(0, 10));

    System.out.println("root.next.size="+phrases.root().keyMapping.size());
    System.out.println("root.done.size=" + phrases.root().doneMapping.size());
    System.out.println("nodes.size=" + phrases.nodes.size());

    int count = 0;
    for (AbstractTrieNode node : phrases.nodes) {
      int used = node.selfCount();
      if(used == 1) { count++; }
    }
    System.out.println("single-item-nodes: "+count);


  }


}
