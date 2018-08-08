package de.upb.crc901.automl.metamining;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.upb.crc901.automl.hascowekaml.MLPipelineComponentInstanceFactory;
import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import jaicore.basic.SQLAdapter;
import weka.core.Instances;

public class ExperimentRepository {
	private SQLAdapter adapter;
	private String host;
	private String user;
	private String password;
	// this should also have a connection to the meta data connector!

	public ExperimentRepository(String host, String user, String password) {
		this.user = user;
		this.password = password;
		this.host = host;
	}

	public List<MLPipeline> getDistinctPipelines() throws Exception {
		connectToMLPlanResults();

		StringBuilder builder = new StringBuilder();
		builder.append(
				"SELECT DISTINCT pipeline FROM evaluations WHERE pipeline NOT LIKE '%jaicore.ml.classification.multiclass.reduction%' ORDER BY pipeline");
		ResultSet resultSet = adapter.getResultsOfQuery(builder.toString());

		System.out.println("Got pipelines from data base.");
		
		List<MLPipeline> pipelines = new ArrayList<MLPipeline>();
		while (resultSet.next()) {
			System.out.println(resultSet.getString("pipeline"));
			pipelines.add(new MLPipeline(resultSet.getString("pipeline")));
			System.out.println("Converting Pipelines to Objects. Progress: " + pipelines.size()/1000.0 + "(" + pipelines.size()+ "/"+1000+")");
			if (pipelines.size() == 1000) {
				break;
			}
		}

		disconnect();

		return pipelines;
	}

	public Instances getDatasetCahracterizations() {
		// get distinct data sets (ordered=

		// characterize pipelines
		return null;
	}

	public double[][][] getPipelineResultsOnDatasets() {
		// ordered accordingly to distinct pipelines + distinct data sets
		return null;
	}

	/**
	 * Returns the characterization of the data set if it is available in the data
	 * base and null otherwise
	 * 
	 * @param dataSetName
	 * @return
	 */
	public HashMap<String, Double> getCharacterizationForDataset(String dataSetName, String metaFeatureSetName) {
		// need to get data set id for name

		// get chara for data set id
		return null;
	}

	private void connectToMLPlanResults() {
		adapter = new SQLAdapter(host, user, password, "mlplan_results");
	}

	private void disconnect() {
		adapter.close();
	}

	public static void main(String[] args) throws Exception {
		MLPipelineComponentInstanceFactory factory = new MLPipelineComponentInstanceFactory(new File("model/weka/weka-all-autoweka.json"));
			
				new ExperimentRepository("isys-db.cs.upb.de", "pgotfml", "automl2018").getDistinctPipelines().forEach(pipeline -> {
					System.out.println(factory.convertToComponentInstance(pipeline).getPrettyPrint());;
				});
	}
}
