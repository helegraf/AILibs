package de.upb.crc901.mlplan.examples.multiclass.weka.regression;

import org.aeonbits.owner.Config.Sources;

import jaicore.ml.experiments.IMultiClassClassificationExperimentConfig;

@Sources({ "file:conf/mlplan-weka-regression.properties" })
public interface MLPlanWekaRegressionExperimenterConfig extends IMultiClassClassificationExperimentConfig {

	public static final String DB_EVAL_TABLE = "db.evalTable";

	@Key(DB_EVAL_TABLE)
	@DefaultValue("evaluations_mls")
	public String evaluationsTable();

}
