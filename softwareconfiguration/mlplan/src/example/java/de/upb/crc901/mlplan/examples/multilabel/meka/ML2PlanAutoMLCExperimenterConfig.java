package de.upb.crc901.mlplan.examples.multilabel.meka;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;

import jaicore.ml.experiments.IMultiClassClassificationExperimentConfig;

@LoadPolicy(LoadType.MERGE)
@Sources({ "file:conf/ml2planExperimenter.properties", "file:conf/ml2plandatabase.properties" })
public interface ML2PlanAutoMLCExperimenterConfig extends IMultiClassClassificationExperimentConfig {

}