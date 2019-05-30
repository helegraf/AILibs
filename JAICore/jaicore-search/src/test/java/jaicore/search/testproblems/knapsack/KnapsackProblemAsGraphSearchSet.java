package jaicore.search.testproblems.knapsack;

import java.util.Set;

import jaicore.basic.algorithm.ReductionBasedAlgorithmTestProblemSet;
import jaicore.search.model.other.SearchGraphPath;
import jaicore.search.probleminputs.GraphSearchWithSubpathEvaluationsInput;
import jaicore.testproblems.knapsack.KnapsackConfiguration;
import jaicore.testproblems.knapsack.KnapsackProblem;
import jaicore.testproblems.knapsack.KnapsackProblemSet;

public class KnapsackProblemAsGraphSearchSet extends ReductionBasedAlgorithmTestProblemSet<GraphSearchWithSubpathEvaluationsInput<KnapsackConfiguration, String, Double>, SearchGraphPath<KnapsackConfiguration, String>, KnapsackProblem, Set<String>> {

	public KnapsackProblemAsGraphSearchSet() {
		super("Knapsack problem as graph search", new KnapsackProblemSet(), new KnapsackToGraphSearchReducer());
	}
}
