package jaicore.search.structure.graphgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jaicore.search.core.interfaces.GraphGenerator;
import jaicore.search.model.travesaltree.NodeExpansionDescription;

public class ReducedGraphGenerator<T, A> implements GraphGenerator<T, A> {

	private GraphGenerator<T, A> basis;

	public ReducedGraphGenerator(GraphGenerator<T, A> basis) {
		this.basis = basis;
	}

	@Override
	public RootGenerator<T> getRootGenerator() {
		return basis.getRootGenerator();
	}

	@Override
	public SuccessorGenerator<T, A> getSuccessorGenerator() {
		return new SuccessorGenerator<T, A>() {
			
			private SuccessorGenerator<T, A> generator = basis.getSuccessorGenerator();

			/**
			 * Expands the node recursively while it only has one successor, until the end
			 * of the branch or a split point in the graph is reached.
			 * 
			 * @param node The node to expand
			 * @return The fully refined node
			 * @throws InterruptedException 
			 */
			public NodeExpansionDescription<T, A> reduce(NodeExpansionDescription<T, A> node) throws InterruptedException {
				List<NodeExpansionDescription<T, A>> sucessors = generator.generateSuccessors(node.getTo());
				List<NodeExpansionDescription<T, A>> previous = Arrays.asList(node);
				while (sucessors.size() == 1) {
					previous = sucessors;
					sucessors = generator.generateSuccessors(sucessors.get(0).getTo());
				}
				return previous.get(0);
			}

			@Override
			public List<NodeExpansionDescription<T, A>> generateSuccessors(T node) throws InterruptedException {				
				List<NodeExpansionDescription<T, A>> successors = generator.generateSuccessors(node);
				// Skip through nodes with 1 successor to find
				while (successors.size() == 1) {
					List<NodeExpansionDescription<T, A>> previous = successors;
					successors = generator.generateSuccessors(successors.get(0).getTo());
					if (successors.isEmpty()) {
						return previous;
					}
				}

				List<NodeExpansionDescription<T, A>> reducedSuccessors = new ArrayList<>();
				for (NodeExpansionDescription<T, A> successor : successors) {
					reducedSuccessors.add(reduce(successor));
				}
				return reducedSuccessors;
			}
		};
	}

	@Override
	public GoalTester<T> getGoalTester() {
		return basis.getGoalTester();
	}

	@Override
	public boolean isSelfContained() {
		return basis.isSelfContained();
	}

	@Override
	public void setNodeNumbering(boolean nodenumbering) {
		basis.setNodeNumbering(nodenumbering);
	}

}
