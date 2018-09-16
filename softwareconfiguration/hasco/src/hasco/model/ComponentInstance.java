package hasco.model;

import java.util.Map;

/**
 * For a given component, a composition defines all parameter values and the
 * required interfaces (recursively)
 *
 * @author fmohr
 *
 */
public class ComponentInstance {
	private final Component component;
	private final Map<String, String> parameterValues;
	/**
	 * The satisfactionOfRequiredInterfaces map maps from Interface IDs to
	 * ComopnentInstances
	 */
	private final Map<String, ComponentInstance> satisfactionOfRequiredInterfaces;

	public ComponentInstance(final Component component, final Map<String, String> parameterValues,
			final Map<String, ComponentInstance> satisfactionOfRequiredInterfaces) {
		super();
		this.component = component;
		this.parameterValues = parameterValues;
		this.satisfactionOfRequiredInterfaces = satisfactionOfRequiredInterfaces;
	}

	public Component getComponent() {
		return this.component;
	}

	public Map<String, String> getParameterValues() {
		return this.parameterValues;
	}

	/**
	 * @return This method returns a mapping of interface IDs to component
	 *         instances.
	 */
	public Map<String, ComponentInstance> getSatisfactionOfRequiredInterfaces() {
		return this.satisfactionOfRequiredInterfaces;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append(this.component);
		sb.append(this.parameterValues);

		return sb.toString();
	}

	public String getPrettyPrint() {
		return getPrettyPrint(0);
	}

	private String getPrettyPrint(int offset) {
		StringBuilder sb = new StringBuilder();
		//sb.append(component.getName() + "\n");
		System.out.print(component.getName() + "\n");
		for (String requiredInterface : component.getRequiredInterfaces().keySet()) {
			for (int i = 0; i < offset + 1; i++)
				System.out.print("\t");
				//sb.append("\t");
			//sb.append(requiredInterface);
			System.out.print(requiredInterface);
			//sb.append(": ");
			System.out.print(":");
			if (satisfactionOfRequiredInterfaces.containsKey(requiredInterface)) {
				//sb.append(satisfactionOfRequiredInterfaces.get(requiredInterface).getPrettyPrint(offset + 1));
				System.out.print(satisfactionOfRequiredInterfaces.get(requiredInterface).getPrettyPrint(offset + 1));
			} else
				//sb.append("null\n");
			System.out.print("null\n");
		}
		return sb.toString();
	}
}
