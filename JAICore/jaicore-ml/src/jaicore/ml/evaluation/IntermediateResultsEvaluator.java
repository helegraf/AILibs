package jaicore.ml.evaluation;

import java.util.HashMap;

import jaicore.basic.SQLAdapter;
import jaicore.experiments.Experiment;
import jaicore.ml.WekaUtil;
import weka.classifiers.Classifier;
import weka.core.Instances;

public class IntermediateResultsEvaluator {
	Instances data;
	String split_technique_val;
	String evaluation_technique_val;
	int seed;
	SQLAdapter adapter;
	String intermediateResultsTableName;

	/**
	 * @param training_data ONLY the training part of the split which is then split again into validation train/ validation test
	 * @param split_technique_val
	 * @param evaluation_technique_val
	 * @param seed
	 * @param adapter
	 * @param intermediateResultsTableName
	 */
	public IntermediateResultsEvaluator(Instances training_data, String split_technique_val, String evaluation_technique_val,
			int seed, SQLAdapter adapter, String intermediateResultsTableName) {
		this.data = training_data;
		this.split_technique_val = split_technique_val;
		this.evaluation_technique_val = evaluation_technique_val;
		this.seed = seed;
		this.intermediateResultsTableName = intermediateResultsTableName;
	}

	public IntermediateResultsEvaluator(Instances data, Experiment experiment, SQLAdapter adapter,
			String intermediateResultsTableName) {
		this(data, experiment.getValuesOfKeyFields().get("split_technique_val"),
				experiment.getValuesOfKeyFields().get("evaluation_technique_val"),
				Integer.parseInt(experiment.getValuesOfKeyFields().get("val_seed")), adapter,
				intermediateResultsTableName);
	}

	public void evaluate(Classifier classifier) throws Exception {
		// Execute Evaluation
		double result = WekaUtil.evaluateClassifier(split_technique_val, evaluation_technique_val, seed, data, classifier);

		// Upload to db
		HashMap<String, Object> map = new HashMap<>();
//		map.put("split_technique_val", value);
//		map.put("evaluation_technique_val", value);
//		map.put("val_seed", value);
//		map.put(key, value)
//		adapter.insert(intermediateResultsTableName, map);
	}
}
