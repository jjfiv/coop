package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class IntListTrie<V> {
  /** Re-use root node, since a loop back to the beginning would be degenerate */
  public static final int NO_MATCH = 0;

  ArrayList<AbstractTrieNode<V>> nodes;
  int size;

  public IntListTrie() {
    nodes = new ArrayList<>();
    nodes.add(new TrieNode<>(this, 1));
    size = 0;
  }

  public TrieNode<V> root() {
    return (TrieNode<V>) nodes.get(0);
  }

  public int size() {
    return size;
  }

  public V findOrInsert(IntList slice, V maybeTermId) {
    return root().findOrInsert(slice.unsafeArray(), 0, slice.size(), maybeTermId);
  }

  public static abstract class AbstractTrieNode<V> {
    protected final IntListTrie<V> trie;
    protected final int myIndex;

    protected AbstractTrieNode(IntListTrie<V> trie, int myIndex) {
      this.trie = trie;
      this.myIndex = myIndex;
    }

    public abstract boolean matches(int data[], int index, int size);
    public abstract void insert(int data[], int index, int size, V value);
    public abstract V findOrInsert(int data[], int index, int size, V value);
    @Nullable
    public abstract V get(int[] ints, int index, int size);

    public abstract int selfCount();

  }

  public static class LeafNode<V> extends AbstractTrieNode<V> {
    IntList tail;
    V value;

    public LeafNode(IntListTrie<V> trie, int myIndex, int[] data, int index, int size, V value) {
      super(trie, myIndex);
      tail = new IntList(size-index);
      for (int i = index; i < size; i++) {
        tail.push(data[i]);
      }
      this.value = value;
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
    public void insert(int[] data, int index, int size, V value) {
      TrieNode<V> replacement = new TrieNode<>(trie, this.myIndex); // take over previous index
      trie.nodes.set(this.myIndex, replacement);
      trie.size--; // subtract current value before it gets added again:
      replacement.insert(tail.unsafeArray(), 0, tail.size(), this.value);
      replacement.insert(data, index, size, value);
    }

    @Override
    public V findOrInsert(int[] data, int index, int size, V value) {
      TrieNode<V> replacement = new TrieNode<>(trie, this.myIndex); // take over previous index
      trie.nodes.set(this.myIndex, replacement);
      trie.size--; // subtract current value before it gets added again:
      replacement.insert(tail.unsafeArray(), 0, tail.size(), this.value);
      return replacement.findOrInsert(data, index, size, value);
    }

    @Nullable
    @Override
    public V get(int[] ints, int index, int size) {
      if(matches(ints, index, size)) {
        return value;
      }
      return null;
    }

    @Override
    public int selfCount() {
      return 1;
    }
  }

  public static class TrieNode<V> extends AbstractTrieNode<V> {
    int myIndex;
    TIntIntHashMap keyMapping;
    TIntObjectHashMap<V> doneMapping;

    public TrieNode(IntListTrie<V> trie, int myIndex) {
      super(trie, myIndex);
      keyMapping = new TIntIntHashMap();
      doneMapping = new TIntObjectHashMap<>();
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
    public void insert(int[] data, int index, int size, V value) {
      // nothing to insert:
      if(index >= size) throw new IllegalArgumentException();

      // this is the last character:
      if(index+1 == size) {
        this.doneMapping.put(data[index], value);
        trie.size++;
        return;
      }

      // this is an intermediate character:
      int pos = find(data[index]);
      if(pos == NO_MATCH) {
        int id = trie.nodes.size();
        LeafNode<V> node = new LeafNode<>(trie, id, data, index, size, value);
        trie.nodes.add(node);
        trie.size++;
        keyMapping.put(data[index], id);
      } else {
        trie.nodes.get(pos).insert(data, index+1, size, value);
      }
    }

    @Override
    public V findOrInsert(int[] data, int index, int size, V value) {
      // nothing to insert:
      if(index >= size) throw new IllegalArgumentException();

      // this is the last character:
      if(index+1 == size) {
        V prev = this.doneMapping.putIfAbsent(data[index], value);
        if(prev == null) {
          trie.size++;
          return value;
        }
        return prev;
      }

      // this is an intermediate character:
      int pos = find(data[index]);
      if(pos == NO_MATCH) {
        int id = trie.nodes.size();
        LeafNode<V> node = new LeafNode<>(trie, id, data, index, size, value);
        trie.nodes.add(node);
        keyMapping.put(data[index], id);
        trie.size++;
        return value;
      } else {
        return trie.nodes.get(pos).findOrInsert(data, index+1, size, value);
      }
    }

    @Nullable
    @Override
    public V get(int[] data, int index, int size) {
      if(index >= size) return null;
      if(index+1 == size) {
        return this.doneMapping.get(data[index]);
      }
      int pos = find(data[index]);
      if(pos == NO_MATCH) return null;
      return trie.nodes.get(pos).get(data, index + 1, size);
    }

    public int find(int i) {
      if(keyMapping.containsKey(i)) {
        return keyMapping.get(i);
      }
      return NO_MATCH;
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
  private void insert(IntList aData, V value) {
    insert(aData.unsafeArray(), 0, aData.size(), value);
  }

  private boolean contains(IntList key) {
    return contains(root(), key.unsafeArray(), 0, key.size());
  }

  private boolean contains(AbstractTrieNode root, int[] data, int index, int size) {
    return root.matches(data, index, size);
  }

  private void insert(int[] data, int index, int size, V value) {
    this.size++;
    root().insert(data, index, size, value);
  }

  private V get(IntList key) {
    return root().get(key.unsafeArray(), 0, key.size());
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

    IntListTrie<Integer> trie = new IntListTrie<>();
    for (int i = 0; i < data.size(); i++) {
      IntList aData = data.get(i);
      trie.insert(aData, i);
      System.out.println(trie.nodes);
    }

    for (int i = 0; i < data.size(); i++) {
      IntList key = data.get(i);
      assert (trie.contains(key));
      assert (trie.get(key) == i);
    }

    IntListTrie<Integer> phrases = new IntListTrie<>();

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
        phrases.insert(slice, lines.getLineNumber());
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
