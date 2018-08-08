package de.upb.crc901.automl.hascowekaml;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import de.upb.crc901.automl.pipeline.basic.SupervisedFilterSelector;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.serialization.ComponentLoader;
import weka.classifiers.Classifier;
import weka.core.OptionHandler;

public class MLPipelineComponentInstanceFactory {

	private ComponentLoader loader;

	public MLPipelineComponentInstanceFactory(File components) throws IOException {
		loader = new ComponentLoader();
		loader.loadComponents(components);
	}

	public ComponentInstance convertToComponentInstance(MLPipeline pipeline) {
		if (pipeline.getPreprocessors() != null && pipeline.getPreprocessors().size() > 0) {
			System.out.println("Pipeline is pipeline");
			// Pipeline has preprocessor
			SupervisedFilterSelector preprocessor = pipeline.getPreprocessors().get(0);
			System.out.println("preprocessor");
			ComponentInstance searcherCI = null;
			ComponentInstance evaluatorCI = null;
			ComponentInstance preprocessorCI = null;
			ComponentInstance classifierCI = null;
			ComponentInstance pipelineCI = null;

			// Search for component for searcher
			for (Component component : loader.getComponents()) {
				if (component.getName().equals(preprocessor.getSearcher().getClass().getName())) {
					System.out.println("Found compoent searcher");
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = null;
					Map<String, String> parameterValues = null;
					if (preprocessor.getSearcher() instanceof OptionHandler) {
						parameterValues = convertToParameterValues((OptionHandler) preprocessor.getSearcher());
					}
					searcherCI = new ComponentInstance(component, parameterValues, satisfactionOfRequiredInterfaces);
				}
			}

			// Search for component for evaluator
			for (Component component : loader.getComponents()) {
				if (component.getName().equals(preprocessor.getEvaluator().getClass().getName())) {
					System.out.println("Found component evaluator");
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = null;
					Map<String, String> parameterValues = null;
					if (preprocessor.getEvaluator() instanceof OptionHandler) {
						parameterValues = convertToParameterValues((OptionHandler) preprocessor.getEvaluator());
					}
					evaluatorCI = new ComponentInstance(component, parameterValues, satisfactionOfRequiredInterfaces);
				}
			}

			// Search for component for preprocessor
			for (Component component : loader.getComponents()) {
				if (component.getName().equals(preprocessor.getSelector().getClass().getName())) {
					System.out.println("Found component preprocessor!");
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = new HashMap<String, ComponentInstance>();
					satisfactionOfRequiredInterfaces.put("eval", evaluatorCI);
					satisfactionOfRequiredInterfaces.put("search", searcherCI);
					Map<String, String> parameterValues = null;
					if (preprocessor.getSelector() instanceof OptionHandler) {
						parameterValues = convertToParameterValues((OptionHandler) preprocessor.getSelector());
					}
					preprocessorCI = new ComponentInstance(component, parameterValues,
							satisfactionOfRequiredInterfaces);
				}
			}
			
			for (Component component : loader.getComponents()) {
				if (component.getName().equals(pipeline.getBaseClassifier().getClass().getName())) {
					System.out.println("Found component classifier!");
					// Found Component
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = null;
					Map<String, String> parameterValues = null;
					if (pipeline.getBaseClassifier() instanceof OptionHandler) {
						parameterValues = convertToParameterValues((OptionHandler) pipeline.getBaseClassifier());
					}
					classifierCI = new ComponentInstance(component, parameterValues,
							satisfactionOfRequiredInterfaces);
				}
			}

			// Search for component for pipeline
			for (Component component : loader.getComponents()) {
				if (component.getName().equals("pipeline")) {
					System.out.println("Found component pipeline!");
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = new HashMap<String, ComponentInstance>();
					satisfactionOfRequiredInterfaces.put("preprocessor", preprocessorCI);
					satisfactionOfRequiredInterfaces.put("classifier", classifierCI);
					Map<String, String> parameterValues = null;
					pipelineCI = new ComponentInstance(component, parameterValues, satisfactionOfRequiredInterfaces);
				}
			}

			return pipelineCI;

		} else {
			// Pipeline is only classifier
			for (Component component : loader.getComponents()) {
				if (component.getName().equals(pipeline.getBaseClassifier().getClass().getName())) {
					System.out.println("Found component classifier!");
					// Found Component
					Map<String, ComponentInstance> satisfactionOfRequiredInterfaces = null;
					Map<String, String> parameterValues = null;
					if (pipeline.getBaseClassifier() instanceof OptionHandler) {
						parameterValues = convertToParameterValues((OptionHandler) pipeline.getBaseClassifier());
					}
					ComponentInstance classifierCI = new ComponentInstance(component, parameterValues,
							satisfactionOfRequiredInterfaces);
					return classifierCI;
				}
			}

		}

		throw new RuntimeException("Cannot convert MLPipeline " + pipeline + " to ComponentInstance");
	}

	private Map<String, String> convertToParameterValues(OptionHandler classifier) {
		HashMap<String, String> parametersWithValues = new HashMap<String, String>();

		OptionHandler handler = (OptionHandler) classifier;
		for (String option : handler.getOptions()) {
			String[] optionWithValue = option.split(" ");
			if (optionWithValue.length > 1) {
				// Have param with values
				parametersWithValues.put(optionWithValue[0].substring(1, optionWithValue[0].length()),
						optionWithValue[1]);
			} else {
				// Have boolean value
				parametersWithValues.put(optionWithValue[0].substring(1, optionWithValue[0].length()), "true");
			}
		}

		return parametersWithValues;
	}
}
