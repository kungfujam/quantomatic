/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.uci.ics.jung.contrib.graph;

import edu.uci.ics.jung.graph.DirectedSparseMultigraph;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author alex
 */
public class DirectedSparseBangBoxMultigraph<V,E,B>
	extends DirectedSparseMultigraph<V, E>
	implements DirectedBangBoxGraph<V, E, B> {

	protected Map<B,Set<V>> bangBoxes;

	public DirectedSparseBangBoxMultigraph() {
		bangBoxes = new HashMap<B, Set<V>>();
	}

	public Collection<B> getBangBoxes() {
		return Collections.unmodifiableCollection(bangBoxes.keySet());
	}

	public boolean containsBangBox(B b) {
		return bangBoxes.containsKey(b);
	}

	public boolean addBangBox(B bangbox, Collection<? extends V> vertices) {
		if (bangbox == null)
			throw new NullPointerException("bangbox");
		if (vertices == null)
			throw new NullPointerException("vertices");
		if (containsBangBox(bangbox))
			return false;

		bangBoxes.put(bangbox, new HashSet<V>(vertices));
		return true;
	}

	public int getBangBoxCount() {
		return bangBoxes.size();
	}

	public boolean removeBangBox(B bangbox) {
		if (!containsBangBox(bangbox))
			return false;

		bangBoxes.remove(bangbox);

		return true;
	}

	public Collection<V> getBoxedVertices(B bangbox) {
		if (!containsBangBox(bangbox))
			return null;

		return Collections.unmodifiableCollection(bangBoxes.get(bangbox));
	}

	public void addVerticesToBangBox(B bangbox, Collection<? extends V> vertices) {
		if (bangbox == null)
			throw new NullPointerException("bangbox");
		if (vertices == null)
			throw new NullPointerException("vertices");
		if (!containsBangBox(bangbox))
			throw new IllegalArgumentException("bangbox is not in this graph");

		bangBoxes.get(bangbox).addAll(vertices);
	}

	public void removeVerticesFromBangBox(B bangbox, Collection<? extends V> vertices) {
		if (bangbox == null)
			throw new NullPointerException("bangbox");
		if (vertices == null)
			throw new NullPointerException("vertices");
		if (!containsBangBox(bangbox))
			throw new IllegalArgumentException("bangbox is not in this graph");

		bangBoxes.get(bangbox).removeAll(vertices);
	}

	public void setBoxedVertices(B bangbox, Collection<? extends V> vertices) {
		if (bangbox == null)
			throw new NullPointerException("bangbox");
		if (vertices == null)
			throw new NullPointerException("vertices");
		if (!containsBangBox(bangbox))
			throw new IllegalArgumentException("bangbox is not in this graph");

		bangBoxes.put(bangbox, new HashSet<V>(vertices));
	}

	@Override
	public boolean removeVertex(V vertex) {
		if (super.removeVertex(vertex)) {
			for (Set<V> contents : bangBoxes.values()) {
				contents.remove(vertex);
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append("\n!-boxes:");
		for (B bangbox : getBangBoxes()) {
			sb.append(bangbox);
			sb.append("[");
			Collection<V> contents = getBoxedVertices(bangbox);
			if (contents.size() > 0) {
				for (V v : contents) {
					sb.append(v);
					sb.append(',');
				}
				sb.setLength(sb.length()-1);
			}
			sb.append("] ");
		}
		sb.setLength(sb.length()-1);
		return sb.toString();
	}
}
