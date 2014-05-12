package com.lotaris.jenkins.yaml;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import javax.servlet.ServletException;
import org.yaml.snakeyaml.Yaml;

/**
 * Build step to extend the current variables with variable values from YAML file.
 * 
 * @author Laurent Prevost <laurent.prevost@lotaris.com>
 */
public class YamlVariablesBuilder extends Builder {
	/**
	 * Yaml Configuration file
	 */
	private final String yamlFile;
	
	/**
	 * The location to find a map of key/value pairs
	 */
	private final String mapLocation;
	
	@DataBoundConstructor
	public YamlVariablesBuilder(String yamlFile, String mapLocation) {
		this.yamlFile = yamlFile;
		this.mapLocation = mapLocation;
	}

	public String getYamlFile() {
		return yamlFile;
	}

	public String getMapLocation() {
		return mapLocation;
	}
	
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		try {
			// Get the build variables
			EnvVars env = build.getEnvironment(listener);
			
			// Expand the configuraiton file path
			String yamlFileExpanded = env.expand(yamlFile);
			yamlFileExpanded = Util.replaceMacro(yamlFileExpanded, build.getBuildVariableResolver());

			Yaml yaml = new Yaml();

			Map<String, Object> root = (Map<String, Object>) yaml.load(new FileInputStream(new File(yamlFileExpanded)));
			
			String[] locations = mapLocation.split("\\.");
			
			Map<String, Object> currentObject = root;
			for (String currentLocation : locations) {
				if (currentObject.get(currentLocation) != null && currentObject.get(currentLocation) instanceof Map) {
					currentObject = (Map<String, Object>) currentObject.get(currentLocation);
				}
				else {
					throw new RuntimeException("Unable to find a possible map in location [" + mapLocation + "]");
				}
			}
			
			Map<String, String> extendedParameters = new HashMap<String, String>();
			for (Map.Entry<String, Object> e : currentObject.entrySet()) {
				extendedParameters.put(e.getKey(), (String) e.getValue());
			}
			
			if (extendedParameters.size() > 0) {
				build.addAction(new YamlExtendVariablesAction(extendedParameters));
			}
			
			return true;
		}
		catch (IOException e) {
			listener.error("Unable to read the YAML file.", e);
			return false;
		}
		catch (InterruptedException e) {
			listener.error("Unable to get the build parameters.", e);
			return false;
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'yaml file'.
		 *
		 * @param value This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 * <p>
		 * Note that returning {@link FormValidation#error(String)} does not prevent the form from being saved. It just means that a message will be displayed to
		 * the user.
		 */
		public FormValidation doCheckYamlFile(@QueryParameter String value)
			throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set a YAML file");
			}
			if (value.length() < 4) {
				return FormValidation.warning("Isn't the file	 too short?");
			}
			return FormValidation.ok();
		}

		/**
		 * Performs on-the-fly validation of the form field 'mapLocation'.
		 *
		 * @param value This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 * <p>
		 * Note that returning {@link FormValidation#error(String)} does not prevent the form from being saved. It just means that a message will be displayed to
		 * the user.
		 */
		public FormValidation doCheckMapLocation(@QueryParameter String value)
			throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set the location where to find the parameters.");
			}
			if (value.length() < 4) {
				return FormValidation.warning("Isn't the map location too short?");
			}
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Extand build parameters from YAML file.";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			save();
			return super.configure(req, formData);
		}
	}
}
