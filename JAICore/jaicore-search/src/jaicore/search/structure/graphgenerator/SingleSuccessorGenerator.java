package jaicore.search.structure.graphgenerator;

import jaicore.search.model.travesaltree.NodeExpansionDescription;

public interface SingleSuccessorGenerator<T,A> extends SuccessorGenerator<T, A> {

	/**
	 * generate the (i%N)-th successor of the given node where N is the number of existing successors.
	 * 
	 * @param i
	 * @return
	 */
	public NodeExpansionDescription<T,A> generateSuccessor(T node, int i) throws InterruptedException;
}
