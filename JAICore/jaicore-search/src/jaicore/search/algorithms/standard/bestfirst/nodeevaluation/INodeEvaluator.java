package jaicore.search.algorithms.standard.bestfirst.nodeevaluation;

import jaicore.search.model.travesaltree.Node;

public interface INodeEvaluator<T,V extends Comparable<V>> {
	public V f(Node<T,?> node) throws Exception;
}
