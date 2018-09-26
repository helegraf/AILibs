package hasco.metamining;

import java.util.Collection;
import java.util.Comparator;

import hasco.core.Util;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;

/**
 * A Comparator for {@link TFDNode}s that sorts based on meta information about
 * the underlying {@link ComponentInstance} of the node and possibly application
 * context.
 * 
 * @author Helena Graf
 *
 */
public class MetaMinerBasedSorter implements Comparator<TFDNode> {

	/**
	 * Components for the current configuration used to convert TFDNodes to
	 * ComponentInstances
	 */
	private Collection<Component> components;

	/**
	 * The "MetaMiner" has access to the meta information of the given
	 * {@link ComponentInstance} and possibly its application context. It is used to
	 * derive a score of a given ComponentInstance, based on which a comparison of
	 * the given {@link TFDNode}s is made.
	 */
	private IMetaMiner metaminer;

	public MetaMinerBasedSorter(IMetaMiner metaminer, Collection<Component> components) {
		this.components = components;
		this.metaminer = metaminer;
	}

	@Override
	public int compare(TFDNode o1, TFDNode o2) {
		double score1 = metaminer.score(convertToComponentInstance(o1));
		double score2 = metaminer.score(convertToComponentInstance(o2));

		System.out.println("Comparing nodes with scores: " + score1 + " vs " + score2);
		return (int) Math.signum(score1 - score2);
	}

	/**
	 * Converts the given TFDNode to a ComponentInstance.
	 * 
	 * @param node
	 *            The TFDNode to convert
	 * @return The TFDNode as a ComponentInstance
	 */
	protected ComponentInstance convertToComponentInstance(TFDNode node) {
		return Util.getSolutionCompositionFromState(components, node.getState(), false);
	}

	/**
	 * Gets the {@link IMetaMiner}, which is used to derive a score for a given
	 * {@link TFDNode} based on its attached {@link ComponentInstance}.
	 * 
	 * @return The meta miner
	 */
	public IMetaMiner getMetaminer() {
		return metaminer;
	}

	/**
	 * Sets the {@link IMetaMiner}, which is used to derive a score for a given
	 * {@link TFDNode} based on its attached {@link ComponentInstance}.
	 * 
	 * @param metaminer
	 *            The meta miner
	 */
	public void setMetaminer(IMetaMiner metaminer) {
		this.metaminer = metaminer;
	}
}
